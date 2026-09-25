package com.itilms.codeexec.config;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.itilms.common.code.CodeLanguage;

import lombok.Getter;
import lombok.Setter;

/**
 * Where the sandbox is, which languages it offers, and how much one student may ask of it.
 * See {@code config-repo/codeexec-service.yml} for the reasoning behind each default.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "itilms.codeexec")
public class CodeExecProperties {

    /** Which sandbox runs the code. Only that engine's settings below are used. */
    private Engine engine = Engine.JUDGE0;

    private Judge0 judge0 = new Judge0();

    private Piston piston = new Piston();

    private Limits limits = new Limits();

    private RateLimit rateLimit = new RateLimit();

    /**
     * Judge0 language id per language. A language with no id here is switched
     * off - it is not offered and a run for it is refused. Ids differ between
     * Judge0 versions and editions, so these are configuration, not code.
     */
    private Map<CodeLanguage, Integer> languageIds = new EnumMap<>(CodeLanguage.class);

    public enum Engine {
        JUDGE0, PISTON
    }

    @Getter
    @Setter
    public static class Piston {
        /** e.g. {@code http://piston:2000}. Blank means "not set up yet". */
        private String baseUrl;
        /**
         * What each language runs as in Piston. A language with no entry here is switched off.
         * Piston addresses a runtime by name and exact version, and only what has been installed
         * exists: {@code GET /api/v2/runtimes} lists it.
         */
        private Map<CodeLanguage, Runtime> runtimes = new EnumMap<>(CodeLanguage.class);
    }

    @Getter
    @Setter
    public static class Runtime {
        /** Piston's name for the language, e.g. {@code java}, {@code c++}, {@code sqlite3}. */
        private String language;
        /** Exact version, e.g. {@code 15.0.2}. */
        private String version;
        /** Source file name, when the language cares (Java's public class must match it). Blank lets Piston choose. */
        private String fileName;
    }

    @Getter
    @Setter
    public static class Judge0 {
        /** e.g. {@code http://host.docker.internal:2358}. Blank means "not set up yet". */
        private String baseUrl;
        /** Header Judge0 reads the token from (its {@code AUTHN_HEADER}). */
        private String authHeader = "X-Auth-Token";
        /** Judge0's {@code AUTHN_TOKEN}. Blank when the instance is not token-protected. */
        private String authToken;
    }

    @Getter
    @Setter
    public static class Limits {
        private int maxSourceChars = 50_000;
        private int maxStdinChars = 10_000;
        /** Longer output is cut and flagged: a print loop must not become a megabyte response. */
        private int maxOutputChars = 64_000;
        private int cpuTimeSeconds = 5;
        private int wallTimeSeconds = 15;
        private int memoryKb = 256_000;
        /** Runs in flight at once. More than this are told to retry rather than queued. */
        private int maxConcurrentRuns = 8;
    }

    @Getter
    @Setter
    public static class RateLimit {
        private int runsPerMinute = 10;
        private int runsPerDay = 300;
    }
}
