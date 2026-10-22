import { useState } from 'react'
import {
  CarouselLayout,
  Chat,
  ControlBar,
  FocusLayout,
  FocusLayoutContainer,
  GridLayout,
  LayoutContextProvider,
  ParticipantTile,
  RoomAudioRenderer,
  isTrackReference,
  useCreateLayoutContext,
  usePinnedTracks,
  useTracks,
} from '@livekit/components-react'
import { RoomEvent, Track } from 'livekit-client'

/** Roles that run or observe the class. A student sees these, themselves, and nobody else. */
function isHostRole(metadata: string | undefined): boolean {
  try {
    const role = (JSON.parse(metadata ?? '{}') as { role?: string }).role
    return role === 'TRAINER' || role === 'STAFF'
  } catch {
    return false
  }
}

/**
 * The student's view of the class: LiveKit's own conference layout, except that other students are left out.
 * Who has joined is for the trainer and administrators only, so a student's grid holds the trainer's camera
 * and screen share and their own picture - no placeholder tile, name or count for classmates.
 */
export function StudentConference() {
  const layoutContext = useCreateLayoutContext()
  const [chatOpen, setChatOpen] = useState(false)

  const tracks = useTracks(
    [
      { source: Track.Source.Camera, withPlaceholder: true },
      { source: Track.Source.ScreenShare, withPlaceholder: false },
    ],
    { updateOnlyOn: [RoomEvent.ActiveSpeakersChanged, RoomEvent.ParticipantMetadataChanged], onlySubscribed: false },
  ).filter((t) => t.participant.isLocal || isHostRole(t.participant.metadata))

  const screenShare = tracks.find((t) => isTrackReference(t) && t.publication.source === Track.Source.ScreenShare)
  const pinned = usePinnedTracks(layoutContext)[0]
  const focus = pinned ?? screenShare
  const others = focus
    ? tracks.filter((t) => !(t.participant === focus.participant && t.source === focus.source))
    : tracks

  return (
    <LayoutContextProvider
      value={layoutContext}
      onWidgetChange={(state) => {
        if (typeof state.showChat === 'boolean') setChatOpen(state.showChat)
      }}
    >
      <div className="lk-video-conference">
        <div className="lk-video-conference-inner">
          {focus ? (
            <div className="lk-focus-layout-wrapper">
              <FocusLayoutContainer>
                <CarouselLayout tracks={others}>
                  <ParticipantTile />
                </CarouselLayout>
                <FocusLayout trackRef={focus} />
              </FocusLayoutContainer>
            </div>
          ) : (
            <div className="lk-grid-layout-wrapper">
              <GridLayout tracks={tracks}>
                <ParticipantTile />
              </GridLayout>
            </div>
          )}
          <ControlBar controls={{ chat: true, settings: false }} />
        </div>
        <Chat style={{ display: chatOpen ? 'grid' : 'none' }} />
      </div>
      <RoomAudioRenderer />
    </LayoutContextProvider>
  )
}
