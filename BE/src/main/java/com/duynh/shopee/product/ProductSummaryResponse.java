package com.duynh.shopee.product;

import java.math.BigDecimal;
import java.time.Instant;

import com.duynh.shopee.category.CategoryResponse;

public record ProductSummaryResponse(
        String id,
        String name,
        String image,
        Long price,
        Long priceBeforeDiscount,
        int quantity,
        int sold,
        int view,
        BigDecimal rating,
        CategoryResponse category,
        Instant createdAt,
        Instant updatedAt) {
    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId().toString(),
                product.getName(),
                product.getImage(),
                product.getPrice(),
                product.getPriceBeforeDiscount(),
                product.getQuantity(),
                product.getSold(),
                product.getView(),
                product.getRating(),
                CategoryResponse.from(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

}
