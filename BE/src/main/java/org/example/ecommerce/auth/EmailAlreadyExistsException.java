package org.example.ecommerce.auth;

import org.example.ecommerce.exception.FieldValidationException;

public class EmailAlreadyExistsException extends FieldValidationException {
    public EmailAlreadyExistsException() {
        super("email", "Email đã tồn tại");
    }
}
