package com.itilms.assessment.entity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A kind of detection the trainer can switch on or off for a test that requires the camera. The camera itself being
 * on is always required; these are the things looked for in the picture.
 */
public enum CameraCheck {
    /** A face must be in view and inside the oval. */
    FACE(ViolationType.FACE_NOT_DETECTED, ViolationType.FACE_OUT_OF_FRAME),
    /** Only the student may be in view: a second face or body is reported. */
    PEOPLE(ViolationType.MULTIPLE_FACES),
    /** The face must stay the one recorded at the start. */
    IDENTITY(ViolationType.FACE_MISMATCH),
    /** A mobile phone in view is reported. */
    PHONE(ViolationType.DEVICE_DETECTED);

    private final Set<ViolationType> reports;

    CameraCheck(ViolationType... reports) {
        this.reports = EnumSet.copyOf(Arrays.asList(reports));
    }

    /** Whether this check is what produces the given report. */
    public boolean produces(ViolationType type) {
        return reports.contains(type);
    }

    /** Every check on: what a test had before the trainer could choose, and what a new test starts with. */
    public static Set<CameraCheck> all() {
        return EnumSet.allOf(CameraCheck.class);
    }

    /** Whether a camera report may be kept for a test running these checks. Camera on/off and permission always may. */
    public static boolean allows(Set<CameraCheck> enabled, ViolationType type) {
        return Arrays.stream(values()).noneMatch(c -> c.produces(type)) || enabled.stream().anyMatch(c -> c.produces(type));
    }

    /** The stored form: names joined by commas. An empty set is stored as an empty string. */
    public static String join(Set<CameraCheck> checks) {
        return checks.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
    }

    public static Set<CameraCheck> parse(String stored) {
        Set<CameraCheck> result = EnumSet.noneOf(CameraCheck.class);
        if (stored != null && !stored.isBlank()) {
            Arrays.stream(stored.split(",")).map(String::trim).map(CameraCheck::valueOf).forEach(result::add);
        }
        return result;
    }

    public static List<String> names(Set<CameraCheck> checks) {
        return checks.stream().sorted().map(Enum::name).toList();
    }
}
