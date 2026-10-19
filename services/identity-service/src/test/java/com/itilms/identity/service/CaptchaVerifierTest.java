package com.itilms.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.itilms.common.exception.ApiException;
import com.itilms.common.exception.BusinessRuleException;
import com.itilms.identity.config.IdentityProperties;

/** The "verify you are human" check: it is only a gate if the server does the checking. */
class CaptchaVerifierTest {

    private static final String VERIFY = "https://captcha.test/siteverify";

    private IdentityProperties properties(boolean enabled, String secret, String siteKey) {
        IdentityProperties p = new IdentityProperties();
        p.getCaptcha().setEnabled(enabled);
        p.getCaptcha().setSecret(secret);
        p.getCaptcha().setSiteKey(siteKey);
        p.getCaptcha().setVerifyUrl(VERIFY);
        return p;
    }

    private record Fixture(CaptchaVerifier verifier, MockRestServiceServer server) {
    }

    private Fixture enabled() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new CaptchaVerifier(properties(true, "s3cret", "pub-key"), builder.build()), server);
    }

    @Test
    void whenItIsOffNothingIsCheckedAndNoKeyIsShown() {
        CaptchaVerifier off = new CaptchaVerifier(properties(false, "", ""), RestClient.create());

        off.require(null, "1.2.3.4");   // no token, no call, no error

        assertThat(off.config().enabled()).isFalse();
        assertThat(off.config().siteKey()).isEmpty();
    }

    @Test
    void aGenuineTokenPassesAndTheSecretGoesOnlyToTheProvider() {
        Fixture f = enabled();
        f.server().expect(requestTo(VERIFY)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("secret=s3cret"),
                        org.hamcrest.Matchers.containsString("response=good-token"),
                        org.hamcrest.Matchers.containsString("remoteip=1.2.3.4"))))
                .andRespond(withSuccess("{\"success\":true,\"hostname\":\"app.test\"}", MediaType.APPLICATION_JSON));

        f.verifier().require("good-token", "1.2.3.4");

        f.server().verify();
        assertThat(f.verifier().config().siteKey()).isEqualTo("pub-key");
    }

    @Test
    void aRefusedTokenIsRejected() {
        Fixture f = enabled();
        f.server().expect(requestTo(VERIFY)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> f.verifier().require("bad", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CAPTCHA_FAILED");
    }

    @Test
    void aMissingTokenIsRejectedWithoutCallingTheProvider() {
        Fixture f = enabled();

        assertThatThrownBy(() -> f.verifier().require("  ", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "CAPTCHA_REQUIRED");
        f.server().verify();   // nothing was expected, nothing was called
    }

    @Test
    void ifTheProviderCannotBeReachedTheRequestIsRefusedNotWavedThrough() {
        Fixture f = enabled();
        f.server().expect(requestTo(VERIFY)).andRespond(withServerError());

        assertThatThrownBy(() -> f.verifier().require("anything", null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void itRefusesToStartHalfConfigured() {
        CaptchaVerifier noSecret = new CaptchaVerifier(properties(true, "", "pub"), RestClient.create());
        CaptchaVerifier noSiteKey = new CaptchaVerifier(properties(true, "sec", ""), RestClient.create());
        CaptchaVerifier fine = new CaptchaVerifier(properties(true, "sec", "pub"), RestClient.create());

        assertThatThrownBy(noSecret::refuseToRunHalfConfigured).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(noSiteKey::refuseToRunHalfConfigured).isInstanceOf(IllegalStateException.class);
        fine.refuseToRunHalfConfigured();
    }
}
