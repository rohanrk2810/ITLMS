package com.itilms.assessment.entity;

/** Something a secure test's browser reported. */
public enum ViolationType {
    /** The test tab was hidden: another tab, another window, minimising. */
    TAB_SWITCH(true),
    /** The window lost focus without the tab being hidden (another application on top). */
    WINDOW_BLUR(true),
    /** Left fullscreen. Recorded only: Escape or F11 pressed by accident should not fail a test. */
    FULLSCREEN_EXIT(false),
    COPY_ATTEMPT(false),
    PASTE_ATTEMPT(false),
    RIGHT_CLICK(false),
    /** A blocked shortcut: print, save, view source, developer tools, print screen. */
    SHORTCUT_BLOCKED(false);

    private final boolean counts;

    ViolationType(boolean counts) {
        this.counts = counts;
    }

    /** Whether this kind of event adds to the count that ends the attempt. */
    public boolean counts() {
        return counts;
    }
}
