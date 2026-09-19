package com.duynh.shopee.auth;

import com.duynh.shopee.exception.FieldValidationException;

public class EmailAlreadyExistsException extends FieldValidationException {
    public EmailAlreadyExistsException() {
        super("email", "Email đã tồn tại");
    }
}
