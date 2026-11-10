package com.itilms.finance.online;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

import com.itilms.finance.config.RazorpayProperties;

class RazorpaySignatureTest {

    private static String hmac(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static RazorpayHttpGateway gateway() {
        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId("rzp_test_x");
        props.setKeySecret("key-secret");
        props.setWebhookSecret("hook-secret");
        return new RazorpayHttpGateway(props);
    }

    @Test
    void acceptsTheSignatureOverOrderAndPayment() throws Exception {
        String good = hmac("key-secret", "order_1|pay_1");
        assertThat(gateway().validPayment("order_1", "pay_1", good)).isTrue();
        assertThat(gateway().validPayment("order_1", "pay_1", good.toUpperCase())).isTrue();
    }

    @Test
    void rejectsAnotherPaymentOrOrderOrSecret() throws Exception {
        String good = hmac("key-secret", "order_1|pay_1");
        assertThat(gateway().validPayment("order_1", "pay_2", good)).isFalse();
        assertThat(gateway().validPayment("order_2", "pay_1", good)).isFalse();
        assertThat(gateway().validPayment("order_1", "pay_1", hmac("other", "order_1|pay_1"))).isFalse();
        assertThat(gateway().validPayment("order_1", "pay_1", "")).isFalse();
        assertThat(gateway().validPayment("order_1", "pay_1", null)).isFalse();
    }

    @Test
    void webhookUsesItsOwnSecret() throws Exception {
        String body = "{\"event\":\"payment.captured\"}";
        assertThat(gateway().validWebhook(body, hmac("hook-secret", body))).isTrue();
        assertThat(gateway().validWebhook(body, hmac("key-secret", body))).isFalse();
    }

    @Test
    void anEmptySecretNeverValidates() throws Exception {
        RazorpayProperties props = new RazorpayProperties();
        RazorpayHttpGateway off = new RazorpayHttpGateway(props);
        assertThat(off.validPayment("o", "p", hmac("any", "o|p"))).isFalse();
        assertThat(off.validPayment("o", "p", "")).isFalse();
        assertThat(props.enabled()).isFalse();
    }
}
