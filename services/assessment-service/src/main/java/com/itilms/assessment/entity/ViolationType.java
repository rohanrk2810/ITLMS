package com.itilms.assessment.entity;

/** Something a monitored test's browser reported. */
public enum ViolationType {
    /** The test tab was hidden: another tab, another window, minimising. */
    TAB_SWITCH(true, false, null),
    /** The window lost focus without the tab being hidden (another application on top). */
    WINDOW_BLUR(true, false, null),
    /** Left fullscreen. Recorded only: Escape or F11 pressed by accident should not fail a test. */
    FULLSCREEN_EXIT(false, false, null),
    COPY_ATTEMPT(false, false, null),
    PASTE_ATTEMPT(false, false, null),
    RIGHT_CLICK(false, false, null),
    /** A blocked shortcut: print, save, view source, developer tools, print screen. */
    SHORTCUT_BLOCKED(false, false, null),

    /**
     * Camera events. Each warns the student and is recorded for the trainer; none ends the attempt,
     * because a face detector has false alarms and must not fail anyone on its word alone.
     */
    FACE_NOT_DETECTED(false, true, "Warning: Please keep your face properly visible in the camera."),
    MULTIPLE_FACES(false, true, "Warning: Only you should be visible in the camera."),
    CAMERA_DISABLED(false, true, "Warning: Your camera must stay on for this test. Turn it back on."),
    CAMERA_PERMISSION_DENIED(false, true, "Warning: Camera permission was removed. Allow the camera again to continue."),

    MICROPHONE_DISABLED(false, false, true, "Warning: Your microphone must stay on for this test. Turn it back on."),
    MICROPHONE_PERMISSION_DENIED(false, false, true,
            "Warning: Microphone permission was removed. Allow the microphone again to continue."),
    SPEECH_DETECTED(false, false, true, "Warning: Sound was picked up. Please stay quiet during the test.");

    private final boolean counts;
    private final boolean camera;
    private final boolean microphone;
    private final String warning;

    ViolationType(boolean counts, boolean camera, String warning) {
        this(counts, camera, false, warning);
    }

    ViolationType(boolean counts, boolean camera, boolean microphone, String warning) {
        this.counts = counts;
        this.camera = camera;
        this.microphone = microphone;
        this.warning = warning;
    }

    /** Whether this kind of event adds to the count that ends the attempt. */
    public boolean counts() {
        return counts;
    }

    /** Reported by camera monitoring, so it applies to tests that require the camera rather than secure ones. */
    public boolean isCamera() {
        return camera;
    }

    public boolean isMicrophone() {
        return microphone;
    }

    /** Reported by the browser's camera or microphone check rather than by the test window. */
    public boolean isMonitoring() {
        return camera || microphone;
    }

    /** What to tell the student when this is reported, or null when it is only recorded. */
    public String warning() {
        return warning;
    }
}
