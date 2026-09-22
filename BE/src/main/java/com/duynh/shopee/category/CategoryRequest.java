package com.duynh.shopee.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Tên danh mục là bắt buộc") 
        @Size(max = 255, message = "Độ dài tối đa 255 kí tự") 
        String name
    ) {}
