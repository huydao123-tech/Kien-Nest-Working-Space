package com.cowork.shared.domain;

public class NotFoundException extends DomainException {

    public NotFoundException(String what, Object id) {
        super("NOT_FOUND", what + " không tồn tại: " + id);
    }
}
