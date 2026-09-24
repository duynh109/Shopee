package com.duynh.shopee.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddToCartRequest(@NotNull(message = "Vui lòng chọn sản phẩm") Long productId,
        @NotNull(message = "Số lượng là bắt buộc") @Positive(message = "Số lượng phải lớn hơn 0") Integer quantity) {

}
