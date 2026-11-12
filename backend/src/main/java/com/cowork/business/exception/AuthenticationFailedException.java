package com.cowork.business.exception;

/** Đăng nhập thất bại: sai tài khoản / mật khẩu, tài khoản bị khóa (UC01, UC23). Trả về HTTP 401. */
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException(String code, String message) {
        super(code, message);
    }
}
