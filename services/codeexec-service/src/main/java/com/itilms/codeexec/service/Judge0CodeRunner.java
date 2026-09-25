package com.itilms.codeexec.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.common.code.CodeLanguage;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs code on a self-hosted Judge0 instance.
 *
 * <p>Uses {@code base64_encoded=true} in both directions: without it Judge0 answers
 * a program that prints a non-UTF-8 byte with an error instead of a result, and a
 * student's program can print anything.
 *
 * <p>Judge0's status ids: 3 accepted, 5 time limit, 6 compile error, 7-12 the runtime
 * errors, 13 internal error, 14 exec format error. 4 (wrong answer) cannot occur - we
 * send no expected output.
 */
@Slf4j
public class Judge0CodeRunner implements CodeRunner {

    private static final int STATUS_PROCESSING = 2;
    private static final int STATUS_TIME_LIMIT = 5;
    private static final int STATUS_COMPILE_ERROR = 6;
    private static final int STATUS_FIRST_RUNTIME_ERROR = 7;
    private static final int STATUS_LAST_RUNTIME_ERROR = 12;
    private static final int STATUS_INTERNAL_ERROR = 13;
    private static final int STATUS_EXEC_FORMAT_ERROR = 14;

    private final RestClient client;
    private final CodeExecProperties properties;

    public Judge0CodeRunner(RestClient client, CodeExecProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public RunResult run(CodeLanguage language, String sourceCode, String stdin) {
        if (!isConfigured()) {
            throw new CodeRunnerUnavailableException("Code execution is not set up on this server yet.");
        }
        Integer languageId = properties.getLanguageIds().get(language);
        if (languageId == null) {
            throw new CodeRunnerUnavailableException(language.label() + " is not enabled on this server.");
        }

        CodeExecProperties.Limits limits = properties.getLimits();
        Map<String, Object> body = new HashMap<>();
        body.put("language_id", languageId);
        body.put("source_code", encode(sourceCode));
        if (stdin != null && !stdin.isEmpty()) {
            body.put("stdin", encode(stdin));
        }
        body.put("cpu_time_limit", limits.getCpuTimeSeconds());
        body.put("wall_time_limit", limits.getWallTimeSeconds());
        body.put("memory_limit", limits.getMemoryKb());

        Submission submission;
        try {
            submission = client.post()
                    .uri("/submissions?base64_encoded=true&wait=true&fields=*")
                    .headers(this::addAuth)
                    .body(body)
                    .retrieve()
                    .body(Submission.class);
        } catch (RestClientException e) {
            log.warn("Judge0 run failed: {}", e.getMessage());
            throw new CodeRunnerUnavailableException("The code runner did not answer. Try again in a moment.");
        }
        if (submission == null || submission.status == null) {
            throw new CodeRunnerUnavailableException("The code runner returned an empty answer.");
        }

        int statusId = submission.status.id;
        if (statusId == STATUS_INTERNAL_ERROR || statusId == STATUS_EXEC_FORMAT_ERROR) {
            // Judge0's own failure (an unusable language install, a broken sandbox) - not the student's bug.
            log.error("Judge0 reported '{}' for {}: {}",
                    submission.status.description, language, decode(submission.message));
            throw new CodeRunnerUnavailableException(
                    language.label() + " could not be run on this server right now. Tell your trainer.");
        }
        if (statusId <= STATUS_PROCESSING) {
            throw new CodeRunnerUnavailableException("The code runner did not finish. Try again in a moment.");
        }

        return new RunResult(
                outcomeOf(statusId),
                submission.status.description,
                decode(submission.stdout),
                decode(submission.stderr),
                decode(submission.compileOutput),
                decode(submission.message),
                parseSeconds(submission.time),
                submission.memory);
    }

    @Override
    public RunnerStatus status() {
        if (!isConfigured()) {
            return new RunnerStatus(false, false,
                    "Set JUDGE0_URL to your Judge0 server to switch code execution on.", languages(Map.of(), false));
        }
        List<Judge0Language> known;
        try {
            known = client.get()
                    .uri("/languages")
                    .headers(this::addAuth)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Judge0Language>>() {
                    });
        } catch (RestClientException e) {
            return new RunnerStatus(true, false,
                    "Judge0 is configured but not reachable: " + e.getMessage(), languages(Map.of(), false));
        }
        Map<Integer, String> names = new HashMap<>();
        if (known != null) {
            known.forEach(language -> names.put(language.id, language.name));
        }

        List<LanguageStatus> languages = languages(names, true);
        long missing = languages.stream().filter(l -> l.mapping() != null && !l.available()).count();
        String message = missing == 0
                ? "Judge0 is reachable."
                : missing + " configured language id(s) do not exist on this Judge0 - fix itilms.codeexec.language-ids.";
        return new RunnerStatus(true, true, message, languages);
    }

    private List<LanguageStatus> languages(Map<Integer, String> sandboxNames, boolean sandboxKnown) {
        List<LanguageStatus> languages = new ArrayList<>();
        for (CodeLanguage language : CodeLanguage.values()) {
            Integer id = properties.getLanguageIds().get(language);
            String sandboxName = id == null ? null : sandboxNames.get(id);
            languages.add(new LanguageStatus(language, id, id == null ? null : "id " + id, sandboxName,
                    sandboxKnown && sandboxName != null));
        }
        return languages;
    }

    @Override
    public Set<CodeLanguage> enabledLanguages() {
        return properties.getLanguageIds().keySet();
    }

    private boolean isConfigured() {
        String url = properties.getJudge0().getBaseUrl();
        return url != null && !url.isBlank();
    }

    private void addAuth(org.springframework.http.HttpHeaders headers) {
        String token = properties.getJudge0().getAuthToken();
        if (token != null && !token.isBlank()) {
            headers.set(properties.getJudge0().getAuthHeader(), token);
        }
    }

    private static Outcome outcomeOf(int statusId) {
        if (statusId == STATUS_TIME_LIMIT) {
            return Outcome.TIME_LIMIT_EXCEEDED;
        }
        if (statusId == STATUS_COMPILE_ERROR) {
            return Outcome.COMPILE_ERROR;
        }
        if (statusId >= STATUS_FIRST_RUNTIME_ERROR && statusId <= STATUS_LAST_RUNTIME_ERROR) {
            return Outcome.RUNTIME_ERROR;
        }
        return Outcome.SUCCESS;
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return null;
        }
        try {
            // Judge0 wraps its base64 at 60 columns; the MIME decoder tolerates the newlines.
            return new String(Base64.getMimeDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Double parseSeconds(String seconds) {
        try {
            return seconds == null ? null : Double.valueOf(seconds);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Submission {
        public String stdout;
        public String stderr;
        @JsonProperty("compile_output")
        public String compileOutput;
        public String message;
        public String time;
        public Integer memory;
        public Status status;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Status {
        public int id;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Judge0Language {
        public int id;
        public String name;
    }
}
