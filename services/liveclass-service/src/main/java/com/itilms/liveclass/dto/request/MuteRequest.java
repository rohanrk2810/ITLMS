package com.itilms.liveclass.dto.request;

import jakarta.validation.constraints.NotNull;

/** Which of a person's media to switch off. */
public record MuteRequest(@NotNull(message = "Say what to mute") Source source) {

    public enum Source {
        MICROPHONE, CAMERA, SCREEN_SHARE
    }
}