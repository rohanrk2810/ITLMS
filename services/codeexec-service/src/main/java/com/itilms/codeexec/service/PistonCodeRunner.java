package com.itilms.codeexec.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.common.code.CodeLanguage;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs code on a self-hosted Piston instance.
 *
 * <p>Piston answers a run with up to two stages: {@code compile} (only for compiled languages)
 * and {@code run}. A non-zero exit in the first is a compile error, in the second a crash. Both
 * carry {@code status}, which Piston's API reference does not define; {@code TO} (timeout) and
 * {@code XX} (Piston's own failure) are the two we act on, everything else is judged by exit
 * code and signal.
 *
 * <p>Times are milliseconds and memory is bytes, unlike Judge0's seconds and kilobytes.
 */
@Slf4j
public class PistonCodeRunner implements CodeRunner {

    private static final String STATUS_TIMEOUT = "TO";
    private static final String STATUS_STDOUT_LIMIT = "OL";
    private static final String STATUS_STDERR_LIMIT = "EL";
    private static final String STATUS_INTERNAL = "XX";

    private static final String JAVA_COMPILE_FAILED = "error: compilation failed";
    private static final String JAVA_NO_MAIN = "can't find main(String[]) method in class";

    /** Lines the compilers print that a student does not need: the C# banner, and gcc's chmod after a failed build. */
    private static final List<String> COMPILER_NOISE = List.of(
            "Microsoft (R) Visual C# Compiler", "Copyright (C) Microsoft", "chmod: cannot");
    /** Piston's own run scripts report a killed or crashed program in their own words, with an install path. */
    private static final List<String> WRAPPER_NOISE = List.of("/piston/packages/");

    private final RestClient client;
    private final CodeExecProperties properties;

    public PistonCodeRunner(RestClient client, CodeExecProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public RunResult run(CodeLanguage language, String sourceCode, String stdin) {
        if (!isConfigured()) {
            throw new CodeRunnerUnavailableException("Code execution is not set up on this server yet.");
        }
        CodeExecProperties.Runtime runtime = properties.getPiston().getRuntimes().get(language);
        if (runtime == null) {
            throw new CodeRunnerUnavailableException(language.label() + " is not enabled on this server.");
        }

        CodeExecProperties.Limits limits = properties.getLimits();
        Map<String, Object> file = new LinkedHashMap<>();
        if (runtime.getFileName() != null && !runtime.getFileName().isBlank()) {
            file.put("name", runtime.getFileName());
        }
        file.put("content", sourceCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("language", runtime.getLanguage());
        body.put("version", runtime.getVersion());
        body.put("files", List.of(file));
        if (stdin != null && !stdin.isEmpty()) {
            body.put("stdin", stdin);
        }
        // Only the run stage is limited per request. The compile stage keeps Piston's own ceilings:
        // javac and mono are slow to start, and a student cannot make the compiler misbehave much.
        // The CPU limit is the wall limit, not limits.cpu-time-seconds: a JVM's compiler and GC threads
        // run alongside each other, and a Hello World measured 3-5 s of CPU in 2-3 s of wall time.
        body.put("run_timeout", limits.getWallTimeSeconds() * 1000L);
        body.put("run_cpu_time", limits.getWallTimeSeconds() * 1000L);
        body.put("run_memory_limit", limits.getMemoryKb() * 1024L);

        Execution execution;
        try {
            execution = client.post().uri("/api/v2/execute").body(body).retrieve().body(Execution.class);
        } catch (RestClientResponseException e) {
            // 4xx: Piston refused the request (unknown runtime, limit above its ceiling). 5xx: it is unwell.
            log.error("Piston refused a {} run: HTTP {} {}", language, e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new CodeRunnerUnavailableException(
                    language.label() + " could not be run on this server right now. Tell your trainer.");
        } catch (RestClientException e) {
            log.warn("Piston run failed: {}", e.getMessage());
            throw new CodeRunnerUnavailableException("The code runner did not answer. Try again in a moment.");
        }
        if (execution == null || execution.run == null) {
            throw new CodeRunnerUnavailableException("The code runner returned an empty answer.");
        }

        Stage compile = execution.compile;
        Stage run = execution.run;
        if (STATUS_INTERNAL.equals(run.status) || compile != null && STATUS_INTERNAL.equals(compile.status)) {
            log.error("Piston reported an internal error for {}: {}", language, run.message);
            throw new CodeRunnerUnavailableException(
                    language.label() + " could not be run on this server right now. Tell your trainer.");
        }

        if (compile != null && failed(compile)) {
            String messages = withoutNoise(
                    firstNonEmpty(compile.output, compile.stderr, compile.stdout, compile.message), COMPILER_NOISE);
            return new RunResult(Outcome.COMPILE_ERROR, "Compilation Error", null, null, messages, null,
                    seconds(compile.cpuTime), kilobytes(compile.memory));
        }

        String runStderr = withoutNoise(run.stderr, WRAPPER_NOISE);

        // Java has no compile stage here: Piston runs "java Main.java", which compiles and runs in one go,
        // so a compile error arrives as a failed run whose last line says so.
        if (language == CodeLanguage.JAVA && compile == null && runStderr != null
                && runStderr.contains(JAVA_COMPILE_FAILED)) {
            String messages = withoutNoise(runStderr.replace(JAVA_COMPILE_FAILED, ""), List.of());
            return new RunResult(Outcome.COMPILE_ERROR, "Compilation Error", null, null, messages, null,
                    seconds(run.cpuTime), kilobytes(run.memory));
        }

        Outcome outcome = Outcome.SUCCESS;
        String description = "Accepted";
        String message = blankToNull(run.message);
        if (STATUS_TIMEOUT.equals(run.status)) {
            outcome = Outcome.TIME_LIMIT_EXCEEDED;
            description = "Time Limit Exceeded";
        } else if (STATUS_STDOUT_LIMIT.equals(run.status) || STATUS_STDERR_LIMIT.equals(run.status)) {
            // Piston kills the program and says so in stderr in its own words, which mean nothing to a student.
            outcome = Outcome.RUNTIME_ERROR;
            description = "Output Limit Exceeded";
            runStderr = null;
            message = "The program printed too much, so it was stopped.";
        } else if (failed(run)) {
            outcome = Outcome.RUNTIME_ERROR;
            description = describeFailure(run);
            if (language == CodeLanguage.JAVA && runStderr != null && runStderr.contains(JAVA_NO_MAIN)) {
                // Java 15's source launcher runs the FIRST class in the file, wherever main is.
                runStderr += "Tip: this runner starts the first class in the file. Put the class that contains main first.\n";
            }
        }
        return new RunResult(outcome, description,
                blankToNull(run.stdout), blankToNull(runStderr), null, message,
                seconds(run.cpuTime), kilobytes(run.memory));
    }

    @Override
    public RunnerStatus status() {
        if (!isConfigured()) {
            return new RunnerStatus(false, false,
                    "Set PISTON_URL to your Piston server to switch code execution on.", languages(Set.of(), false));
        }
        List<InstalledRuntime> installed;
        try {
            installed = client.get().uri("/api/v2/runtimes").retrieve()
                    .body(new ParameterizedTypeReference<List<InstalledRuntime>>() {
                    });
        } catch (RestClientException e) {
            return new RunnerStatus(true, false,
                    "Piston is configured but not reachable: " + e.getMessage(), languages(Set.of(), false));
        }
        Set<String> present = new HashSet<>();
        if (installed != null) {
            installed.forEach(r -> present.add(r.language + " " + r.version));
        }

        List<LanguageStatus> languages = languages(present, true);
        long missing = languages.stream().filter(l -> l.mapping() != null && !l.available()).count();
        String message = missing == 0
                ? "Piston is reachable."
                : missing + " configured runtime(s) are not installed in Piston - install them, or fix itilms.codeexec.piston.runtimes.";
        return new RunnerStatus(true, true, message, languages);
    }

    @Override
    public Set<CodeLanguage> enabledLanguages() {
        return properties.getPiston().getRuntimes().keySet();
    }

    private List<LanguageStatus> languages(Set<String> installed, boolean sandboxKnown) {
        List<LanguageStatus> languages = new ArrayList<>();
        for (CodeLanguage language : CodeLanguage.values()) {
            CodeExecProperties.Runtime runtime = properties.getPiston().getRuntimes().get(language);
            if (runtime == null) {
                languages.add(new LanguageStatus(language, null, null, null, false));
                continue;
            }
            String mapping = runtime.getLanguage() + " " + runtime.getVersion();
            boolean present = sandboxKnown && installed.contains(mapping);
            languages.add(new LanguageStatus(language, null, mapping, present ? mapping : null, present));
        }
        return languages;
    }

    private boolean isConfigured() {
        String url = properties.getPiston().getBaseUrl();
        return url != null && !url.isBlank();
    }

    private static String describeFailure(Stage run) {
        if (run.signal != null) {
            return "Runtime Error (" + run.signal + ")";
        }
        // A shell reports "killed by signal N" as exit code 128 + N.
        return switch (run.code == null ? -1 : run.code) {
            case 134 -> "Runtime Error (aborted)";
            case 136 -> "Runtime Error (arithmetic error)";
            case 137 -> "Runtime Error (killed, probably out of memory)";
            case 139 -> "Runtime Error (segmentation fault)";
            default -> "Runtime Error (exit code " + run.code + ")";
        };
    }

    /** Drops every line containing one of the fragments; null when nothing is left. */
    private static String withoutNoise(String text, List<String> fragments) {
        if (text == null) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        boolean removedAny = false;
        for (String line : text.split("\n", -1)) {
            if (fragments.stream().anyMatch(line::contains)) {
                removedAny = true;
            } else {
                kept.append(line).append('\n');
            }
        }
        if (!removedAny) {
            return text;
        }
        // What is left often starts with the blank line that followed the banner.
        String result = kept.toString().strip();
        return result.isEmpty() ? null : result + "\n";
    }

    private static boolean failed(Stage stage) {
        return stage.signal != null || stage.code != null && stage.code != 0;
    }

    private static Double seconds(Double milliseconds) {
        return milliseconds == null ? null : milliseconds / 1000.0;
    }

    private static Integer kilobytes(Long bytes) {
        return bytes == null ? null : (int) (bytes / 1024);
    }

    private static String blankToNull(String text) {
        return text == null || text.isEmpty() ? null : text;
    }

    private static String firstNonEmpty(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Execution {
        public Stage compile;
        public Stage run;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Stage {
        public String stdout;
        public String stderr;
        public String output;
        public Integer code;
        public String signal;
        public String message;
        public String status;
        @JsonProperty("cpu_time")
        public Double cpuTime;
        public Long memory;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class InstalledRuntime {
        public String language;
        public String version;
    }
}
