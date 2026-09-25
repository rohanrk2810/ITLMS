package com.itilms.codeexec.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.common.code.CodeLanguage;

class Judge0CodeRunnerTest {

    private static final String BASE = "http://judge0.test";

    private CodeExecProperties properties;
    private MockRestServiceServer server;
    private Judge0CodeRunner runner;

    @BeforeEach
    void setUp() {
        properties = new CodeExecProperties();
        properties.getJudge0().setBaseUrl(BASE);
        properties.getLanguageIds().put(CodeLanguage.JAVA, 91);
        properties.getLanguageIds().put(CodeLanguage.PYTHON, 92);
        properties.getLanguageIds().put(CodeLanguage.SQL, 82);
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        runner = new Judge0CodeRunner(builder.build(), properties);
    }

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String answer(int statusId, String description, String stdout, String stderr, String compile) {
        return """
                {"stdout": %s, "stderr": %s, "compile_output": %s, "message": null,
                 "time": "0.021", "memory": 3120,
                 "status": {"id": %d, "description": "%s"}, "token": "abc"}
                """.formatted(quote(stdout), quote(stderr), quote(compile), statusId, description);
    }

    private static String quote(String base64OrNull) {
        return base64OrNull == null ? "null" : "\"" + b64(base64OrNull) + "\"";
    }

    @Test
    void aSuccessfulRunSendsBase64CodeAndLimitsAndDecodesTheOutput() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.language_id").value(92))
                .andExpect(jsonPath("$.source_code").value(b64("print('héllo')")))
                .andExpect(jsonPath("$.stdin").value(b64("in")))
                .andExpect(jsonPath("$.cpu_time_limit").value(5))
                .andExpect(jsonPath("$.wall_time_limit").value(15))
                .andExpect(jsonPath("$.memory_limit").value(256000))
                .andRespond(withSuccess(answer(3, "Accepted", "héllo\n", null, null), MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.PYTHON, "print('héllo')", "in");

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.SUCCESS);
        assertThat(result.stdout()).isEqualTo("héllo\n");
        assertThat(result.timeSeconds()).isEqualTo(0.021);
        assertThat(result.memoryKb()).isEqualTo(3120);
        server.verify();
    }

    @Test
    void emptyStdinIsNotSent() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andExpect(jsonPath("$.stdin").doesNotExist())
                .andRespond(withSuccess(answer(3, "Accepted", "x", null, null), MediaType.APPLICATION_JSON));

        runner.run(CodeLanguage.PYTHON, "print('x')", "");

        server.verify();
    }

    @Test
    void base64WrappedAtSixtyColumnsIsStillDecoded() {
        String wrapped = b64("a".repeat(100)).replaceAll("(.{60})", "$1\\\\n");
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withSuccess("""
                        {"stdout": "%s", "status": {"id": 3, "description": "Accepted"}}
                        """.formatted(wrapped), MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.PYTHON, "x", null);

        assertThat(result.stdout()).isEqualTo("a".repeat(100));
    }

    @Test
    void aCompileErrorIsAResultWithTheCompilersMessages() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withSuccess(answer(6, "Compilation Error", null, null, "Main.java:1: error: ';' expected"),
                        MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.JAVA, "class Main {", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.COMPILE_ERROR);
        assertThat(result.compileOutput()).contains("';' expected");
    }

    @Test
    void everyRuntimeErrorStatusIsARuntimeError() {
        for (int status = 7; status <= 12; status++) {
            server.reset();
            server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                    .andRespond(withSuccess(answer(status, "Runtime Error", null, "boom", null), MediaType.APPLICATION_JSON));

            var result = runner.run(CodeLanguage.PYTHON, "x", null);

            assertThat(result.outcome()).as("status %d", status).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
            assertThat(result.stderr()).isEqualTo("boom");
        }
    }

    @Test
    void aTimeLimitIsItsOwnOutcome() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withSuccess(answer(5, "Time Limit Exceeded", null, null, null), MediaType.APPLICATION_JSON));

        assertThat(runner.run(CodeLanguage.PYTHON, "while True: pass", null).outcome())
                .isEqualTo(CodeRunner.Outcome.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void judge0sOwnInternalErrorIsAnOutageNotAStudentBug() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withSuccess(answer(13, "Internal Error", null, null, null), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> runner.run(CodeLanguage.JAVA, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("Java could not be run");
    }

    @Test
    void aRunThatNeverLeftTheQueueIsAnOutage() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withSuccess(answer(1, "In Queue", null, null, null), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> runner.run(CodeLanguage.JAVA, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void aServerErrorFromJudge0IsAnOutageWithoutLeakingItsBody() {
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andRespond(withServerError().body("secret internals").contentType(MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> runner.run(CodeLanguage.JAVA, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void theAuthTokenIsSentInTheConfiguredHeader() {
        properties.getJudge0().setAuthHeader("X-Auth-Token");
        properties.getJudge0().setAuthToken("s3cret");
        server.expect(requestTo(BASE + "/submissions?base64_encoded=true&wait=true&fields=*"))
                .andExpect(header("X-Auth-Token", "s3cret"))
                .andRespond(withSuccess(answer(3, "Accepted", "x", null, null), MediaType.APPLICATION_JSON));

        runner.run(CodeLanguage.PYTHON, "x", null);

        server.verify();
    }

    @Test
    void withNoBaseUrlNothingIsSentAndTheAnswerSaysNotSetUp() {
        properties.getJudge0().setBaseUrl(" ");

        assertThatThrownBy(() -> runner.run(CodeLanguage.PYTHON, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("not set up");
        var status = runner.status();
        assertThat(status.configured()).isFalse();
        assertThat(status.languages()).noneMatch(CodeRunner.LanguageStatus::available);
        server.verify();
    }

    @Test
    void aLanguageWithoutAnIdCannotRun() {
        assertThatThrownBy(() -> runner.run(CodeLanguage.CSHARP, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("C# is not enabled");
    }

    @Test
    void statusNamesConfiguredIdsThatJudge0DoesNotHave() {
        server.expect(requestTo(BASE + "/languages"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id": 92, "name": "Python (3.11.2)"}, {"id": 82, "name": "SQL (SQLite 3.27.2)"},
                         {"id": 62, "name": "Java (OpenJDK 13.0.1)"}]
                        """, MediaType.APPLICATION_JSON));

        var status = runner.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.reachable()).isTrue();
        assertThat(status.message()).contains("1 configured language id(s) do not exist");
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.PYTHON)
                .singleElement().satisfies(l -> {
                    assertThat(l.available()).isTrue();
                    assertThat(l.mapping()).isEqualTo("id 92");
                    assertThat(l.sandboxName()).isEqualTo("Python (3.11.2)");
                });
        // Java is mapped to 91, which this Judge0 does not have (it has 62).
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.JAVA)
                .singleElement().satisfies(l -> {
                    assertThat(l.languageId()).isEqualTo(91);
                    assertThat(l.available()).isFalse();
                });
        // C# has no id at all - switched off, and not counted as a mistake.
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.CSHARP)
                .singleElement().satisfies(l -> {
                    assertThat(l.languageId()).isNull();
                    assertThat(l.mapping()).isNull();
                });
    }

    @Test
    void statusReportsAnUnreachableJudge0WithoutThrowing() {
        server.expect(requestTo(BASE + "/languages")).andRespond(withServerError());

        var status = runner.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.reachable()).isFalse();
        assertThat(status.message()).contains("not reachable");
    }
}
