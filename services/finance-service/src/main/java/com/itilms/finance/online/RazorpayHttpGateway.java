package com.itilms.finance.online;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.itilms.common.exception.BusinessRuleException;
import com.itilms.finance.config.RazorpayProperties;

import lombok.extern.slf4j.Slf4j;

/** Razorpay over HTTPS with the account key. Signatures are checked here, in constant time. */
@Slf4j
@Component
public class RazorpayHttpGateway implements RazorpayGateway {

    private final RazorpayProperties props;
    private final RestClient http;

    public RazorpayHttpGateway(RazorpayProperties props) {
        this.props = props;
        this.http = RestClient.builder().baseUrl(props.getBaseUrl()).build();
    }

    @Override
    public String createOrder(BigDecimal amount, String currency, String receipt) {
        long paise = amount.movePointRight(2).longValueExact();
        String basic = Base64.getEncoder().encodeToString(
                (props.getKeyId() + ":" + props.getKeySecret()).getBytes(StandardCharsets.UTF_8));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.post().uri("/orders")
                    .header("Authorization", "Basic " + basic)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", paise, "currency", currency, "receipt", receipt))
                    .retrieve().body(Map.class);
            Object id = response == null ? null : response.get("id");
            if (id == null) {
                throw new BusinessRuleException("The payment gateway did not return an order. Please try again.");
            }
            return id.toString();
        } catch (BusinessRuleException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Razorpay order creation failed", e);
            throw new BusinessRuleException("The payment gateway is not reachable right now. Please try again shortly.");
        }
    }

    @Override
    public boolean validPayment(String orderId, String paymentId, String signature) {
        return matches(props.getKeySecret(), orderId + "|" + paymentId, signature);
    }

    @Override
    public boolean validWebhook(String body, String signature) {
        return matches(props.getWebhookSecret(), body, signature);
    }

    static boolean matches(String secret, String payload, String signature) {
        if (secret == null || secret.isBlank() || payload == null || signature == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
