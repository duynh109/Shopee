package com.duynh.shopee.product;

import jakarta.validation.constraints.NotNull;

public record CreateProductRequest(String name, double price, int stock,
        @NotNull(message = "Vui lòng chọn danh mục") Long categoryId) {
}
