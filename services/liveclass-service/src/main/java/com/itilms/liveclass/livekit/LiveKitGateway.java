package com.itilms.liveclass.livekit;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;

import com.itilms.common.exception.ApiException;
import com.itilms.liveclass.config.LiveKitProperties;
import com.itilms.liveclass.service.RoomPermissions;

import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanPublishSources;
import io.livekit.server.CanSubscribe;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import io.livekit.server.VideoGrant;
import livekit.LivekitEgress;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import livekit.LivekitModels;
import retrofit2.Call;
import retrofit2.Response;

/**
 * The only place in IT-ILMS that talks to LiveKit.
 *
 * <p>Two jobs: creating and tearing down rooms over the admin API, and minting
 * the signed join tokens a browser needs to enter one. Keeping both behind a
 * single class means the API secret stays in one file, and the grant rules -
 * which decide what a participant can actually do once inside - are written
 * down once rather than assembled ad hoc at each call site.
 *
 * <p>The SDK is a Retrofit client returning {@code Call} objects, and its
 * exceptions are checked {@link IOException}s. They are translated here into the
 * application's own exception type so callers do not have to care that the media
 * server happens to be reached over HTTP.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveKitGateway {

    private final LiveKitProperties properties;

    private RoomServiceClient roomService;
    private EgressServiceClient egressService;

    @PostConstruct
    void init() {
        this.roomService = RoomServiceClient.createClient(
                properties.getUrl(), properties.getApiKey(), properties.getApiSecret());
        this.egressService = EgressServiceClient.createClient(
                properties.getUrl(), properties.getApiKey(), properties.getApiSecret());
        log.info("LiveKit room service configured for {}", properties.getUrl());
    }

    // -----------------------------------------------------------------
    // Rooms
    // -----------------------------------------------------------------

    /**
     * Creates the room, or returns the existing one.
     *
     * <p>LiveKit treats createRoom as idempotent by name, which is what makes
     * this safe to call from the pre-provisioning job and from every join
     * without several rooms appearing.
     *
     * @param emptyTimeoutSeconds     how long the room waits for its first participant
     * @param departureTimeoutSeconds how long it stays open after the last one leaves
     * @return the LiveKit room sid, or null if the server did not report one
     */
    public String createRoom(String roomName, String metadata, int emptyTimeoutSeconds,
                             int departureTimeoutSeconds, int maxParticipants) {
        LivekitModels.Room room = execute(
                roomService.createRoom(roomName, emptyTimeoutSeconds, maxParticipants, null, metadata,
                        null, null, null, departureTimeoutSeconds),
                "create room " + roomName);
        return room == null ? null : room.getSid();
    }

    /**
     * Closes a room and disconnects everyone left in it.
     *
     * <p>Failure is logged rather than thrown. This is called while ending a
     * class, and a media server that cannot be reached must not stop attendance
     * being computed and the register being written - LiveKit will drop an
     * abandoned room on its own empty-timeout anyway.
     */
    public void deleteRoomQuietly(String roomName) {
        try {
            execute(roomService.deleteRoom(roomName), "delete room " + roomName);
        } catch (Exception ex) {
            log.warn("Could not delete LiveKit room {} - it will expire on its own timeout: {}",
                    roomName, ex.getMessage());
        }
    }

    /**
     * Who LiveKit currently believes is in the room.
     *
     * <p>Used to reconcile when webhooks have been missed. Returns an empty list
     * if the room no longer exists, which is the normal answer for a class that
     * has finished.
     */
    public List<LivekitModels.ParticipantInfo> listParticipants(String roomName) {
        try {
            List<LivekitModels.ParticipantInfo> participants =
                    execute(roomService.listParticipants(roomName), "list participants in " + roomName);
            return participants == null ? List.of() : participants;
        } catch (Exception ex) {
            log.debug("Could not list participants in {}: {}", roomName, ex.getMessage());
            return List.of();
        }
    }

    /** Removes one participant - the trainer's "remove from class" control. */
    public void removeParticipant(String roomName, String identity) {
        execute(roomService.removeParticipant(roomName, identity),
                "remove " + identity + " from " + roomName);
    }

    // -----------------------------------------------------------------
    // Join tokens
    // -----------------------------------------------------------------

    /**
     * Mints a signed join token.
     *
     * <p>The grants are decided here, server-side, from the caller's role as
     * IT-ILMS knows it. Nothing in the token comes from the request body, so a
     * student cannot ask for room-admin rights and there is no client-side field
     * to tamper with: the signature covers the grants as well as the identity.
     *
     * <p>{@code canPublish} is what separates an attendee from an observer, and
     * {@code roomAdmin} is what lets a trainer mute or remove someone.
     */
    public String mintJoinToken(String roomName, String identity, String displayName,
                                String metadata, List<String> publishSources, boolean roomAdmin) {
        AccessToken token = new AccessToken(properties.getApiKey(), properties.getApiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        if (metadata != null) {
            token.setMetadata(metadata);
        }
        token.setExpiration(Date.from(expiryFrom(Instant.now())));

        token.addGrants(grantsFor(roomName, publishSources, roomAdmin));
        return token.toJwt();
    }

    /**
     * {@code publishSources} is what this person may switch on: any of microphone, camera, screen_share and
     * screen_share_audio. An empty list is an observer who can watch and listen but not be seen or heard.
     */
    private VideoGrant[] grantsFor(String roomName, List<String> publishSources, boolean roomAdmin) {
        return new VideoGrant[]{
                new RoomJoin(true),
                new RoomName(roomName),
                // Everyone subscribes - an observer with no audio or video is
                // attending a class they cannot see.
                new CanSubscribe(true),
                new CanPublish(!publishSources.isEmpty()),
                new CanPublishSources(publishSources),
                // Chat and the raise-hand signal ride on the data channel, so
                // even a non-publishing observer keeps it.
                new CanPublishData(true),
                new RoomAdmin(roomAdmin)
        };
    }

    // -----------------------------------------------------------------
    // Recording
    // -----------------------------------------------------------------

    /**
     * Starts a room-composite capture (everyone's video and audio, mixed into one file) and returns the Egress id
     * that identifies it. Egress writes to {@code filepath} on its own container's disk - a volume this service also
     * mounts, at the same path - since no cloud upload target is configured; a self-hosted deployment has nowhere
     * else to put it by default.
     */
    public String startRoomCompositeEgress(String roomName, String filepath) {
        LivekitEgress.EncodedFileOutput output = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFilepath(filepath)
                .build();
        LivekitEgress.EgressInfo info = execute(
                egressService.startRoomCompositeEgress(roomName, output),
                "start recording of " + roomName);
        return info == null ? null : info.getEgressId();
    }

    /**
     * Asks Egress to finish and finalise the file. Not thrown on failure: this is called from class-ending code
     * paths (the End button, the sweep) that must not fail just because the media server could not be reached -
     * Egress also stops on its own once the room it is capturing closes.
     */
    public void stopEgressQuietly(String egressId) {
        try {
            execute(egressService.stopEgress(egressId), "stop recording " + egressId);
        } catch (Exception ex) {
            log.warn("Could not stop egress {} - it will stop on its own once the room closes: {}",
                    egressId, ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Moderation while the class is running
    // -----------------------------------------------------------------

    /**
     * Changes what one person present in the room may publish, at once.
     *
     * <p>Their join token still says what it said when they entered; LiveKit's own per-participant permission
     * overrides it from now on, and takes anything they are currently publishing that is no longer allowed off air.
     */
    public void updatePublishPermissions(String roomName, String identity, List<String> publishSources) {
        LivekitModels.ParticipantPermission permission = LivekitModels.ParticipantPermission.newBuilder()
                .setCanSubscribe(true)
                .setCanPublish(!publishSources.isEmpty())
                .setCanPublishData(true)
                .addAllCanPublishSources(publishSources.stream().map(LiveKitGateway::trackSource).toList())
                .build();
        execute(roomService.updateParticipant(roomName, identity, null, null, permission),
                "update permissions of " + identity + " in " + roomName);
    }

    /** Mutes what one person is publishing from the given sources. They may unmute again if still permitted. */
    public int muteSources(String roomName, String identity, List<String> sources) {
        LivekitModels.ParticipantInfo info = execute(roomService.getParticipant(roomName, identity),
                "look up " + identity + " in " + roomName);
        return info == null ? 0 : muteTracksOf(roomName, info, sources);
    }

    /** Mutes the microphone of everyone in the room except the given identities (the hosts). Returns how many. */
    public int muteMicrophonesExcept(String roomName, java.util.Set<String> keep) {
        int muted = 0;
        for (LivekitModels.ParticipantInfo participant : listParticipants(roomName)) {
            if (!keep.contains(participant.getIdentity())) {
                muted += muteTracksOf(roomName, participant, List.of(RoomPermissions.MICROPHONE));
            }
        }
        return muted;
    }

    private int muteTracksOf(String roomName, LivekitModels.ParticipantInfo participant, List<String> sources) {
        java.util.Set<LivekitModels.TrackSource> wanted = sources.stream().map(LiveKitGateway::trackSource)
                .collect(java.util.stream.Collectors.toSet());
        int muted = 0;
        for (LivekitModels.TrackInfo track : participant.getTracksList()) {
            if (wanted.contains(track.getSource()) && !track.getMuted()) {
                execute(roomService.mutePublishedTrack(roomName, participant.getIdentity(), track.getSid(), true),
                        "mute a track of " + participant.getIdentity());
                muted++;
            }
        }
        return muted;
    }

    /**
     * Sends a small message to everyone in the room (or the listed identities) over the data channel. Used to tell
     * browsers "a question was asked" the moment it is; they then fetch it. Best effort: browsers also poll, so a
     * missed message delays a question by seconds and never loses it.
     */
    public void sendDataQuietly(String roomName, String topic, String json, List<String> toIdentities) {
        try {
            execute(roomService.sendData(roomName, json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            LivekitModels.DataPacket.Kind.RELIABLE, List.of(), toIdentities, topic),
                    "send " + topic + " message to " + roomName);
        } catch (Exception ex) {
            log.warn("Could not send a {} message to room {}: {}", topic, roomName, ex.getMessage());
        }
    }

    static LivekitModels.TrackSource trackSource(String source) {
        return switch (source) {
            case RoomPermissions.MICROPHONE -> LivekitModels.TrackSource.MICROPHONE;
            case RoomPermissions.CAMERA -> LivekitModels.TrackSource.CAMERA;
            case RoomPermissions.SCREEN_SHARE -> LivekitModels.TrackSource.SCREEN_SHARE;
            case RoomPermissions.SCREEN_SHARE_AUDIO -> LivekitModels.TrackSource.SCREEN_SHARE_AUDIO;
            default -> throw new IllegalArgumentException("Unknown track source " + source);
        };
    }

    public Instant expiryFrom(Instant now) {
        Duration ttl = properties.getTokenTtl() == null ? Duration.ofHours(4) : properties.getTokenTtl();
        return now.plus(ttl);
    }

    public String wsUrl() {
        return properties.getWsUrl();
    }

    // -----------------------------------------------------------------

    private <T> T execute(Call<T> call, String what) {
        try {
            Response<T> response = call.execute();
            if (!response.isSuccessful()) {
                String detail = errorBody(response);
                log.error("LiveKit rejected request to {}: HTTP {} {}", what, response.code(), detail);
                throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "LIVEKIT_ERROR",
                        "The media server refused the request (" + response.code() + ").");
            }
            return response.body();
        } catch (IOException ex) {
            log.error("Could not reach LiveKit to {}", what, ex);
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "LIVEKIT_UNREACHABLE",
                    "The media server is not reachable. Please try again in a moment.");
        }
    }

    private String errorBody(Response<?> response) {
        try {
            return response.errorBody() == null ? "" : response.errorBody().string();
        } catch (IOException ex) {
            return "";
        }
    }
}
