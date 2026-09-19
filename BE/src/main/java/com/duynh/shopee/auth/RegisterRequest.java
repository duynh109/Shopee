package com.duynh.shopee.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank (message = "Email là bắt buộc")
    @Email (message = "Email không hợp lệ")
    @Size (min = 5, max = 160, message = "Độ dài từ 5-160 kí tự")
    String email,

    @NotBlank (message = "Mật khẩu là bắt buộc")
    @Size (min = 6, max = 160, message = "Độ dài từ 6-160 kí tự")
    String password
) {}
