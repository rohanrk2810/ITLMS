package com.itilms.codeexec.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.itilms.codeexec.config.CodeExecProperties;
import com.itilms.codeexec.dto.RunBatchRequest;
import com.itilms.codeexec.dto.RunCodeRequest;
import com.itilms.codeexec.exception.CodeRunnerUnavailableException;
import com.itilms.codeexec.exception.RunLimitException;
import com.itilms.common.code.CodeLanguage;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.common.exception.ForbiddenOperationException;
import com.itilms.common.security.AppPrincipal;

class CodeExecServiceTest {

    private static final AppPrincipal STUDENT = new AppPrincipal(10L, "s@x", "Student", "STUDENT", 1L);

    private CodeRunner runner;
    private CodeExecProperties properties;
    private CodeExecService service;

    @BeforeEach
    void setUp() {
        runner = mock(CodeRunner.class);
        properties = new CodeExecProperties();
        // Which languages are on is the runner's business, whatever engine it is.
        when(runner.enabledLanguages()).thenReturn(EnumSet.of(CodeLanguage.JAVA, CodeLanguage.PYTHON));
        properties.getLimits().setMaxSourceChars(100);
        properties.getLimits().setMaxStdinChars(10);
        properties.getLimits().setMaxOutputChars(20);
        properties.getLimits().setMaxConcurrentRuns(1);
        properties.getRateLimit().setRunsPerMinute(2);
        service = new CodeExecService(runner, properties, new RunRateLimiter(properties.getRateLimit(), Clock.systemUTC()));
        actAs(STUDENT);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void actAs(AppPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static CodeRunner.RunResult ok(String stdout) {
        return new CodeRunner.RunResult(CodeRunner.Outcome.SUCCESS, "Accepted", stdout, null, null, null, 0.05, 900);
    }

    @Test
    void onlyLanguagesWithAnIdAreOffered() {
        assertThat(service.languages()).extracting(l -> l.code()).containsExactly("JAVA", "PYTHON");
    }

    @Test
    void aRunIsPassedToTheRunnerAndTheAnswerComesBack() {
        when(runner.run(CodeLanguage.PYTHON, "print(1)", null)).thenReturn(ok("1\n"));

        var response = service.run(new RunCodeRequest(" python ", "print(1)", null));

        assertThat(response.language()).isEqualTo("PYTHON");
        assertThat(response.outcome()).isEqualTo("SUCCESS");
        assertThat(response.stdout()).isEqualTo("1\n");
        assertThat(response.outputTruncated()).isFalse();
    }

    @Test
    void aCompileErrorIsAnAnswerNotAFailure() {
        when(runner.run(any(), any(), any())).thenReturn(new CodeRunner.RunResult(
                CodeRunner.Outcome.COMPILE_ERROR, "Compilation Error", null, null, "Main.java:1: error", null, null, null));

        var response = service.run(new RunCodeRequest("JAVA", "class", null));

        assertThat(response.outcome()).isEqualTo("COMPILE_ERROR");
        assertThat(response.compileOutput()).contains("error");
    }

    @Test
    void aBatchRunsEveryInputInOrderAndCountsAsOneRun() {
        when(runner.run(CodeLanguage.PYTHON, "print(input())", "a")).thenReturn(ok("a\n"));
        when(runner.run(CodeLanguage.PYTHON, "print(input())", "b")).thenReturn(ok("b\n"));

        for (int i = 0; i < 2; i++) {
            var responses = service.runBatch(new RunBatchRequest("PYTHON", "print(input())", List.of("a", "b")));
            assertThat(responses).extracting(r -> r.stdout()).containsExactly("a\n", "b\n");
        }

        // Two batches of two inputs used two runs, the whole minute's allowance - not four.
        assertThatThrownBy(() -> service.runBatch(new RunBatchRequest("PYTHON", "x", List.of("a"))))
                .isInstanceOf(RunLimitException.class);
    }

    @Test
    void aBatchThatDoesNotCompileIsCompiledOnceAndReportedForEveryInput() {
        when(runner.run(any(), any(), any())).thenReturn(new CodeRunner.RunResult(
                CodeRunner.Outcome.COMPILE_ERROR, "Compilation Error", null, null, "error: ';' expected", null, null, null));

        var responses = service.runBatch(new RunBatchRequest("JAVA", "class", List.of("1", "2", "3")));

        assertThat(responses).hasSize(3).allMatch(r -> r.outcome().equals("COMPILE_ERROR"));
        verify(runner, org.mockito.Mockito.times(1)).run(any(), any(), any());
    }

    @Test
    void aBatchLargerThanTheConfiguredCeilingIsRefusedBeforeItRuns() {
        properties.getLimits().setMaxBatchCases(2);

        assertThatThrownBy(() -> service.runBatch(new RunBatchRequest("JAVA", "x", List.of("1", "2", "3"))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("At most 2");
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void anUnknownLanguageIsRefusedAndTheAllowedOnesAreListed() {
        assertThatThrownBy(() -> service.run(new RunCodeRequest("PLSQL", "x", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("JAVA, PYTHON, C, CPP, CSHARP or SQL");
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void aKnownLanguageThatIsSwitchedOffIsRefused() {
        assertThatThrownBy(() -> service.run(new RunCodeRequest("CSHARP", "x", null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("C# is not enabled");
        verify(runner, never()).run(any(), any(), any());
    }

    @Test
    void codeAndInputOverTheLimitAreRefusedBeforeTheySpendARun() {
        assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x".repeat(101), null)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("100 characters");
        assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x", "y".repeat(11))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Input is limited");
        verify(runner, never()).run(any(), any(), any());

        // ...and did not count against the rate limit.
        when(runner.run(any(), any(), any())).thenReturn(ok("a"));
        service.run(new RunCodeRequest("JAVA", "x", null));
        service.run(new RunCodeRequest("JAVA", "x", null));
    }

    @Test
    void aStudentRunningTooOftenIsToldToSlowDown() {
        when(runner.run(any(), any(), any())).thenReturn(ok("a"));
        service.run(new RunCodeRequest("JAVA", "x", null));
        service.run(new RunCodeRequest("JAVA", "x", null));

        assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x", null)))
                .isInstanceOf(RunLimitException.class);
        verify(runner, org.mockito.Mockito.times(2)).run(eq(CodeLanguage.JAVA), any(), any());
    }

    @Test
    void whenTheRunnerIsAtCapacityTheCallerIsTurnedAwayNotQueued() {
        when(runner.run(any(), any(), any())).thenAnswer(invocation -> {
            // While this run is in flight (limit: 1), a second caller must be refused at once.
            actAs(new AppPrincipal(11L, "o@x", "Other", "STUDENT", 2L));
            assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x", null)))
                    .isInstanceOfSatisfying(RunLimitException.class, e -> assertThat(e.getCode()).isEqualTo("RUNNER_BUSY"));
            return ok("done");
        });

        var response = service.run(new RunCodeRequest("JAVA", "x", null));

        assertThat(response.stdout()).isEqualTo("done");
        // The slot is released afterwards. (doReturn: "when(runner.run(..))" would call the answer above again.)
        actAs(STUDENT);
        doReturn(ok("again")).when(runner).run(any(), any(), any());
        assertThat(service.run(new RunCodeRequest("JAVA", "x", null)).stdout()).isEqualTo("again");
    }

    @Test
    void theSlotIsReleasedEvenWhenTheRunnerFails() {
        when(runner.run(any(), any(), any()))
                .thenThrow(new CodeRunnerUnavailableException("down"))
                .thenReturn(ok("back"));

        assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x", null)))
                .isInstanceOf(CodeRunnerUnavailableException.class);

        assertThat(service.run(new RunCodeRequest("JAVA", "x", null)).stdout()).isEqualTo("back");
    }

    @Test
    void outputLongerThanTheLimitIsCutAndFlagged() {
        when(runner.run(any(), any(), any())).thenReturn(ok("x".repeat(50)));

        var response = service.run(new RunCodeRequest("JAVA", "x", null));

        assertThat(response.stdout()).hasSize(20);
        assertThat(response.outputTruncated()).isTrue();
    }

    @Test
    void anonymousCallersCannotRun() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.run(new RunCodeRequest("JAVA", "x", null)))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(runner, never()).run(any(), any(), any());
    }
}
