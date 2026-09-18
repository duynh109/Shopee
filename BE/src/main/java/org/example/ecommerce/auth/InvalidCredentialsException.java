package org.example.ecommerce.auth;

import org.example.ecommerce.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super("/errors/invalid-credentials", "Email hoặc mật khẩu không đúng");
    }
}
