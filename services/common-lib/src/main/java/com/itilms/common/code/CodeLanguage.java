package com.itilms.common.code;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The languages a lesson's practice editor can offer and codeexec-service can run.
 *
 * <p>Shared so course-service (which stores a lesson's language) and
 * codeexec-service (which runs it) cannot drift apart on spelling. Which
 * compiler version or Judge0 language id backs each constant is deployment
 * configuration in codeexec-service, deliberately not encoded here.
 *
 * <p>PL/SQL is absent on purpose: it needs an Oracle database behind it, which
 * a generic sandbox does not provide.
 */
public enum CodeLanguage {
    JAVA("Java"),
    PYTHON("Python"),
    C("C"),
    CPP("C++"),
    CSHARP("C#"),
    SQL("SQL");

    private final String label;

    CodeLanguage(String label) {
        this.label = label;
    }

    /** What a person sees in a drop-down. */
    public String label() {
        return label;
    }

    /** Case-insensitive, whitespace-tolerant; empty for blank or unknown input. */
    public static Optional<CodeLanguage> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim();
        return Arrays.stream(values())
                .filter(language -> language.name().equalsIgnoreCase(wanted))
                .findFirst();
    }

    /** {@code JAVA, PYTHON, C, CPP, CSHARP or SQL} - for error messages. */
    public static String allowedList() {
        String names = Arrays.stream(values()).map(CodeLanguage::name).collect(Collectors.joining(", "));
        int last = names.lastIndexOf(", ");
        return names.substring(0, last) + " or " + names.substring(last + 2);
    }
}
