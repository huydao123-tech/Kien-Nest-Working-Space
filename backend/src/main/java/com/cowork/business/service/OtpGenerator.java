package com.cowork.business.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Sinh mã OTP 6 chữ số ngẫu nhiên. Tách riêng để integration test thay bằng mã cố định. */
@Component
public class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String next() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
