package com.duynh.shopee.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.duynh.shopee.category.CategoryResponse;

public record ProductResponse(
        String id,
        String name,
        String image,
        List<String> images,
        Long price,
        Long priceBeforeDiscount,
        int quantity,
        int sold,
        int view,
        BigDecimal rating,
        String description,
        CategoryResponse category,
        Instant createdAt,
        Instant updatedAt) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId().toString(),
                product.getName(),
                product.getImage(),
                product.getImages().stream().map(ProductImage::getUrl).toList(),
                product.getPrice(),
                product.getPriceBeforeDiscount(),
                product.getQuantity(),
                product.getSold(),
                product.getView(),
                product.getRating(),
                product.getDescription(),
                CategoryResponse.from(product.getCategory()),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
