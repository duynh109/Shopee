package com.duynh.shopee.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(@NotBlank(message = "Thiếu refresh token") String refreshToken) {

}
