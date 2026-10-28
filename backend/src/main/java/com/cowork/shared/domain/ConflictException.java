package com.cowork.shared.domain;

/** Xung đột với trạng thái hiện tại, ví dụ vị trí đã có người giữ trong khung giờ. */
public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
