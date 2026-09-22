package com.duynh.shopee.auth;

import com.duynh.shopee.validation.MaxBytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank (message = "Email là bắt buộc")
    @Email (message = "Email không hợp lệ")
    @Size (min = 5, max = 255, message = "Độ dài từ 5-255 kí tự")
    String email,

    @NotBlank (message = "Mật khẩu là bắt buộc")
    @Size (min = 6, message = "Độ dài tối thiểu 6 kí tự")
    @MaxBytes (value = 72, message = "Mật khẩu tối đa 72 kí tự")
    String password
) {}
