package com.duynh.shopee.auth;

import com.duynh.shopee.exception.UnauthorizedException;

public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super("/errors/invalid-credentials", "Email hoặc mật khẩu không đúng");
    }
}
