package org.example.ecommerce.exception;

import java.util.Map;

public class FieldValidationException extends RuntimeException {
    private final Map<String, String> errors;
    
    public FieldValidationException(Map<String, String> errors) {
        super("Dữ liệu không hợp lệ");
        this.errors = errors;
    }

    public FieldValidationException(String field, String message) {
        this(Map.of(field, message));
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
