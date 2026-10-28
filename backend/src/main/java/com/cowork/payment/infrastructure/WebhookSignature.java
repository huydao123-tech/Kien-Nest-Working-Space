package com.cowork.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** FR-PAY-03: chữ ký HMAC-SHA256 trên nguyên văn body webhook. */
@Component
public class WebhookSignature {

    private final byte[] secret;

    WebhookSignature(@Value("${payment.webhook-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không tạo được chữ ký HMAC", e);
        }
    }

    public boolean verify(String body, String signature) {
        if (signature == null) {
            return false;
        }
        // So sánh thời gian hằng để không lộ chữ ký qua timing.
        return MessageDigest.isEqual(sign(body).getBytes(StandardCharsets.UTF_8),
                signature.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
