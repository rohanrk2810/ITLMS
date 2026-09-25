package com.itilms.codeexec.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.common.code.CodeLanguage;

class PistonCodeRunnerTest {

    private static final String BASE = "http://piston.test";
    private static final String EXECUTE = BASE + "/api/v2/execute";

    private CodeExecProperties properties;
    private MockRestServiceServer server;
    private PistonCodeRunner runner;

    @BeforeEach
    void setUp() {
        properties = new CodeExecProperties();
        properties.setEngine(CodeExecProperties.Engine.PISTON);
        properties.getPiston().setBaseUrl(BASE);
        properties.getPiston().getRuntimes().put(CodeLanguage.JAVA, runtime("java", "15.0.2", "Main"));
        properties.getPiston().getRuntimes().put(CodeLanguage.PYTHON, runtime("python", "3.12.0", null));
        properties.getPiston().getRuntimes().put(CodeLanguage.SQL, runtime("sqlite3", "3.36.0", null));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        runner = new PistonCodeRunner(builder.build(), properties);
    }

    private static CodeExecProperties.Runtime runtime(String language, String version, String fileName) {
        CodeExecProperties.Runtime runtime = new CodeExecProperties.Runtime();
        runtime.setLanguage(language);
        runtime.setVersion(version);
        runtime.setFileName(fileName);
        return runtime;
    }

    private static String stage(String stdout, String stderr, int code, String signal, String status) {
        return """
                {"stdout": "%s", "stderr": "%s", "output": "%s%s", "code": %d, "signal": %s,
                 "message": null, "status": %s, "cpu_time": 21, "wall_time": 40, "memory": 3145728}
                """.formatted(stdout, stderr, stdout, stderr, code,
                signal == null ? "null" : "\"" + signal + "\"", status == null ? "null" : "\"" + status + "\"");
    }

    private static String answer(String compileStage, String runStage) {
        return "{\"language\": \"x\", \"version\": \"1\""
                + (compileStage == null ? "" : ", \"compile\": " + compileStage)
                + ", \"run\": " + runStage + "}";
    }

    @Test
    void aRunSendsTheRuntimeTheFileNameTheStdinAndOnlyTheRunLimits() {
        server.expect(requestTo(EXECUTE))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.language").value("java"))
                .andExpect(jsonPath("$.version").value("15.0.2"))
                .andExpect(jsonPath("$.files[0].name").value("Main"))
                .andExpect(jsonPath("$.files[0].content").value("class Main {}"))
                .andExpect(jsonPath("$.stdin").value("in"))
                .andExpect(jsonPath("$.run_timeout").value(15000))
                .andExpect(jsonPath("$.run_cpu_time").value(15000))
                .andExpect(jsonPath("$.run_memory_limit").value(256000L * 1024))
                .andExpect(jsonPath("$.compile_timeout").doesNotExist())
                .andRespond(withSuccess(answer(stage("", "", 0, null, null), stage("hi\\n", "", 0, null, null)),
                        MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.JAVA, "class Main {}", "in");

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.SUCCESS);
        assertThat(result.stdout()).isEqualTo("hi\n");
        assertThat(result.stderr()).isNull();
        assertThat(result.timeSeconds()).isEqualTo(0.021);
        assertThat(result.memoryKb()).isEqualTo(3072);
        server.verify();
    }

    @Test
    void aLanguageWithoutAFileNameLetsPistonChooseAndEmptyStdinIsNotSent() {
        server.expect(requestTo(EXECUTE))
                .andExpect(jsonPath("$.files[0].name").doesNotExist())
                .andExpect(jsonPath("$.stdin").doesNotExist())
                .andRespond(withSuccess(answer(null, stage("1\\n", "", 0, null, null)), MediaType.APPLICATION_JSON));

        assertThat(runner.run(CodeLanguage.PYTHON, "print(1)", "").stdout()).isEqualTo("1\n");
        server.verify();
    }

    @Test
    void aFailedCompileStageIsACompileErrorWithTheCompilersWords() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withSuccess(answer(
                        stage("", "Main.java:1: error: reached end of file", 1, null, "RE"),
                        stage("", "", 0, null, null)), MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.JAVA, "class Main {", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.COMPILE_ERROR);
        assertThat(result.compileOutput()).contains("reached end of file");
        assertThat(result.stdout()).isNull();
    }

    @Test
    void aNonZeroExitFromTheRunStageIsARuntimeErrorAndKeepsWhatWasPrinted() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withSuccess(answer(null, stage("before\\n", "Traceback: boom", 1, null, "RE")),
                        MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.PYTHON, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.statusDescription()).isEqualTo("Runtime Error (exit code 1)");
        assertThat(result.stdout()).isEqualTo("before\n");
        assertThat(result.stderr()).isEqualTo("Traceback: boom");
    }

    @Test
    void aProcessKilledBySignalIsARuntimeErrorNamingTheSignal() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withSuccess(answer(null, stage("", "", 0, "SIGSEGV", "SG")), MediaType.APPLICATION_JSON));

        var result = runner.run(CodeLanguage.PYTHON, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.statusDescription()).isEqualTo("Runtime Error (SIGSEGV)");
    }

    @Test
    void aTimeoutIsItsOwnOutcome() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withSuccess(answer(null, stage("", "", 0, "SIGKILL", "TO")), MediaType.APPLICATION_JSON));

        assertThat(runner.run(CodeLanguage.PYTHON, "while True: pass", null).outcome())
                .isEqualTo(CodeRunner.Outcome.TIME_LIMIT_EXCEEDED);
    }

    @Test
    void pistonsOwnInternalErrorIsAnOutageNotAStudentBug() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withSuccess(answer(null, stage("", "", 0, null, "XX")), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> runner.run(CodeLanguage.PYTHON, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("Python could not be run");
    }

    @Test
    void aRefusedRequestIsAnOutageWithoutLeakingPistonsMessage() {
        server.expect(requestTo(EXECUTE))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\": \"python-3.12.0 runtime is unknown, secret path /piston\"}"));

        assertThatThrownBy(() -> runner.run(CodeLanguage.PYTHON, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class)
                .hasMessageNotContaining("secret");
    }

    @Test
    void aServerErrorIsAnOutage() {
        server.expect(requestTo(EXECUTE)).andRespond(withServerError());

        assertThatThrownBy(() -> runner.run(CodeLanguage.PYTHON, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void withNoBaseUrlNothingIsSentAndTheAnswerSaysNotSetUp() {
        properties.getPiston().setBaseUrl(" ");

        assertThatThrownBy(() -> runner.run(CodeLanguage.PYTHON, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("not set up");
        assertThat(runner.status().configured()).isFalse();
        server.verify();
    }

    @Test
    void aLanguageWithoutARuntimeCannotRunAndIsNotEnabled() {
        assertThat(runner.enabledLanguages()).containsExactlyInAnyOrder(
                CodeLanguage.JAVA, CodeLanguage.PYTHON, CodeLanguage.SQL);
        assertThatThrownBy(() -> runner.run(CodeLanguage.CSHARP, "x", null))
                .isInstanceOf(CodeRunnerUnavailableException.class).hasMessageContaining("C# is not enabled");
    }

    @Test
    void statusNamesConfiguredRuntimesThatAreNotInstalled() {
        server.expect(requestTo(BASE + "/api/v2/runtimes"))
                .andRespond(withSuccess("""
                        [{"language": "python", "version": "3.12.0", "aliases": ["py"]},
                         {"language": "sqlite3", "version": "3.36.0", "aliases": ["sqlite"]}]
                        """, MediaType.APPLICATION_JSON));

        var status = runner.status();

        assertThat(status.reachable()).isTrue();
        assertThat(status.message()).contains("1 configured runtime(s) are not installed");
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.PYTHON)
                .singleElement().satisfies(l -> {
                    assertThat(l.available()).isTrue();
                    assertThat(l.mapping()).isEqualTo("python 3.12.0");
                });
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.JAVA)
                .singleElement().satisfies(l -> {
                    assertThat(l.available()).isFalse();
                    assertThat(l.mapping()).isEqualTo("java 15.0.2");
                });
        assertThat(status.languages()).filteredOn(l -> l.language() == CodeLanguage.CSHARP)
                .singleElement().satisfies(l -> assertThat(l.mapping()).isNull());
    }

    @Test
    void statusReportsAnUnreachablePistonWithoutThrowing() {
        server.expect(requestTo(BASE + "/api/v2/runtimes")).andRespond(withServerError());

        var status = runner.status();

        assertThat(status.configured()).isTrue();
        assertThat(status.reachable()).isFalse();
        assertThat(status.message()).contains("not reachable");
    }

    // ---- Real answers from a running Piston, kept as captured (only paths shortened). ----

    private static String runOnly(String stdout, String stderr, Integer code, String signal, String status, String message) {
        return """
                {"language": "x", "version": "1", "run": {"stdout": %s, "stderr": %s, "output": "", "code": %s,
                 "signal": %s, "message": %s, "status": %s, "cpu_time": 2950, "wall_time": 1450, "memory": 53477376}}
                """.formatted(json(stdout), json(stderr), code, json(signal), json(message), json(status));
    }

    private static String json(String text) {
        return text == null ? "null" : "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                .replace("\t", "\\t").replace("\r", "\\r") + "\"";
    }

    private void respondWith(String body) {
        server.expect(requestTo(EXECUTE)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void aJavaCompileErrorArrivesAsAFailedRunAndIsStillACompileError() {
        // Piston runs "java Main.java" - there is no compile stage, so javac's messages come back in the run stage.
        respondWith(runOnly("",
                "Main.java:3: error: incompatible types: String cannot be converted to int\n        int x = \"text\";\n"
                        + "                ^\n1 error\nerror: compilation failed\n",
                1, null, "RE", "Exited with error status 1"));

        var result = runner.run(CodeLanguage.JAVA, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.COMPILE_ERROR);
        assertThat(result.compileOutput()).startsWith("Main.java:3: error: incompatible types")
                .doesNotContain("compilation failed");
        assertThat(result.stderr()).isNull();
    }

    @Test
    void aJavaExceptionIsStillARuntimeErrorNotACompileError() {
        respondWith(runOnly("before\n",
                "Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 2\n"
                        + "\tat Main.main(Main.java:5)\n", 1, null, "RE", "Exited with error status 1"));

        var result = runner.run(CodeLanguage.JAVA, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.stdout()).isEqualTo("before\n");
        assertThat(result.stderr()).contains("ArrayIndexOutOfBoundsException");
    }

    @Test
    void aJavaHelperClassBeforeMainGetsATipBecausePistonRunsTheFirstClass() {
        respondWith(runOnly("", "error: can't find main(String[]) method in class: Helper\n", 1, null, "RE",
                "Exited with error status 1"));

        var result = runner.run(CodeLanguage.JAVA, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.stderr()).contains("can't find main(String[]) method in class: Helper")
                .contains("Put the class that contains main first");
    }

    @Test
    void theJavaTipIsNotAddedForOtherLanguages() {
        respondWith(runOnly("", "error: can't find main(String[]) method in class: Helper\n", 1, null, "RE", null));

        assertThat(runner.run(CodeLanguage.PYTHON, "x", null).stderr()).doesNotContain("Tip");
    }

    @Test
    void aCSharpCompileErrorLosesTheCompilerBanner() {
        String stage = """
                {"stdout": "Microsoft (R) Visual C# Compiler version 3.9.0-6.21124.20 (db94f4cc)\\nCopyright (C) Microsoft Corporation. All rights reserved.\\n\\nMain.cs(4,17): error CS0029: Cannot implicitly convert type 'string' to 'int'\\n",
                 "stderr": "", "output": "Microsoft (R) Visual C# Compiler version 3.9.0-6.21124.20 (db94f4cc)\\nCopyright (C) Microsoft Corporation. All rights reserved.\\n\\nMain.cs(4,17): error CS0029: Cannot implicitly convert type 'string' to 'int'\\n",
                 "code": 1, "signal": null, "message": "Exited with error status 1", "status": "RE",
                 "cpu_time": 1379, "wall_time": 1405, "memory": 55574528}
                """;
        respondWith(answer(stage, stage));

        var result = runner.run(CodeLanguage.PYTHON, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.COMPILE_ERROR);
        assertThat(result.compileOutput()).isEqualTo("Main.cs(4,17): error CS0029: Cannot implicitly convert type 'string' to 'int'\n");
    }

    @Test
    void aSegmentationFaultIsNamedAndPistonsWrapperLineIsDropped() {
        respondWith(runOnly("before\n",
                "/piston/packages/gcc/10.2.0/run: line 6:     3 Segmentation fault      (core dumped) ./a.out \"$@\"\n",
                139, null, "RE", "Exited with error status 139"));

        var result = runner.run(CodeLanguage.PYTHON, "x", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.statusDescription()).isEqualTo("Runtime Error (segmentation fault)");
        assertThat(result.stdout()).isEqualTo("before\n");
        assertThat(result.stderr()).isNull();
    }

    @Test
    void exitCode137MeansTheProgramWasKilledProbablyForMemory() {
        respondWith(runOnly("", "/piston/packages/python/3.12.0/run: line 3:     3 Killed                  python3.12 \"$@\"\n",
                137, null, "RE", "Exited with error status 137"));

        assertThat(runner.run(CodeLanguage.PYTHON, "x", null).statusDescription())
                .isEqualTo("Runtime Error (killed, probably out of memory)");
    }

    @Test
    void tooMuchOutputIsExplainedInPlainWordsAndPistonsInternalMessageIsHidden() {
        respondWith(runOnly("x".repeat(100), "Sandbox keeper received fatal signal 6\n", null, "SIGKILL", "OL",
                "stdout length exceeded"));

        var result = runner.run(CodeLanguage.PYTHON, "print('x' * 10**6)", null);

        assertThat(result.outcome()).isEqualTo(CodeRunner.Outcome.RUNTIME_ERROR);
        assertThat(result.statusDescription()).isEqualTo("Output Limit Exceeded");
        assertThat(result.stdout()).hasSize(100);
        assertThat(result.stderr()).isNull();
        assertThat(result.message()).isEqualTo("The program printed too much, so it was stopped.");
    }

    @Test
    void aWallClockTimeoutIsATimeLimitEvenWhenTheProgramWasOnlySleeping() {
        respondWith(runOnly("", "", null, "SIGKILL", "TO", "Time limit exceeded (wall clock)"));

        assertThat(runner.run(CodeLanguage.PYTHON, "import time; time.sleep(99)", null).outcome())
                .isEqualTo(CodeRunner.Outcome.TIME_LIMIT_EXCEEDED);
    }
}
