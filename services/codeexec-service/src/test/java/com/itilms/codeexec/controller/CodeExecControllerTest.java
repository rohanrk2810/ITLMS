package com.itilms.codeexec.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.itilms.common.security.AppPrincipal;

/**
 * The real Spring wiring with no Judge0 configured: security, property binding, validation and
 * the shared error contract. Nothing here reaches a sandbox.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CodeExecControllerTest {

    @Autowired
    MockMvc mvc;

    private static RequestPostProcessor as(String role) {
        AppPrincipal principal = new AppPrincipal(10L, "u@x", "User", role, "STUDENT".equals(role) ? 1L : null);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    private static final String RUN = "{\"language\":\"PYTHON\",\"sourceCode\":\"print(1)\"}";

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mvc.perform(get("/api/code/languages")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/code/run").contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theConfiguredLanguagesAreOffered() throws Exception {
        mvc.perform(get("/api/code/languages").with(as("STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].code").value("JAVA"))
                .andExpect(jsonPath("$[0].label").value("Java"))
                .andExpect(jsonPath("$[3].code").value("CPP"))
                .andExpect(jsonPath("$[3].label").value("C++"));
    }

    @Test
    void withoutJudge0ARunIsAnHonestServiceUnavailableNotAServerError() throws Exception {
        mvc.perform(post("/api/code/run").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(RUN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CODE_RUNNER_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Code execution is not set up on this server yet."));
    }

    @Test
    void aMissingLanguageOrEmptyCodeIsABadRequest() throws Exception {
        mvc.perform(post("/api/code/run").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"print(1)\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/code/run").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PYTHON\",\"sourceCode\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codeOverTheConfiguredLimitIsRefusedWith422() throws Exception {
        String big = "{\"language\":\"PYTHON\",\"sourceCode\":\"" + "x".repeat(51) + "\"}";

        mvc.perform(post("/api/code/run").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(big))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Code is limited to 50 characters."));
    }

    @Test
    void anUnsupportedLanguageIsRefusedWith422() throws Exception {
        mvc.perform(post("/api/code/run").with(as("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"PLSQL\",\"sourceCode\":\"BEGIN NULL; END;\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void sandboxStatusIsForAdministratorsOnly() throws Exception {
        mvc.perform(get("/api/code/status").with(as("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/code/status").with(as("TRAINER"))).andExpect(status().isForbidden());

        mvc.perform(get("/api/code/status").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.reachable").value(false))
                .andExpect(jsonPath("$.languages.length()").value(6))
                .andExpect(jsonPath("$.languages[0].languageId").value(91));
    }

    @Test
    void healthIsAnonymousSoTheComposeHealthcheckWorks() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
