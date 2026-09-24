package com.duynh.shopee.cart;

import java.time.Instant;

import com.duynh.shopee.product.ProductSummaryResponse;

public record CartItemResponse(
        String id,
        int quantity,
        ProductSummaryResponse product,
        Instant createdAt,
        Instant updatedAt) {
    public static CartItemResponse from(CartItem cartItem) {
        return new CartItemResponse(
                cartItem.getId().toString(),
                cartItem.getQuantity(),
                ProductSummaryResponse.from(cartItem.getProduct()),
                cartItem.getCreatedAt(),
                cartItem.getUpdatedAt());
    }
}
