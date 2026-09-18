package org.example.ecommerce.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Email là bắt buộc")
    String email,

    @NotBlank(message = "Password là bắt buộc")
    String password
) {}
