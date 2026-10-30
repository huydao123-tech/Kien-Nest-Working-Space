package com.cowork.business.exception;

/** Vi phạm quy tắc nghiệp vụ. {@code code} là mã lỗi ổn định trả về cho client. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
