package org.example.ecommerce.exception;

public class UnauthorizedException extends RuntimeException {
    private final String type;

    public UnauthorizedException(String type, String message) {
        super(message);
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
