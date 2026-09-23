package com.duynh.shopee.product;

import java.util.Set;

import com.duynh.shopee.exception.FieldValidationException;

public record ProductQuery(
        Integer page,
        Integer limit,
        String sortBy,
        String order,
        Long category,
        String name,
        Long priceMin,
        Long priceMax,
        Integer ratingFilter,
        Long exclude) {
    private static final Set<String> SORT_FIELDS = Set.of("price", "sold", "view", "createdAt");

    public ProductQuery {
        if (page == null || page < 1) {
            page = 1;
        }
        if (limit == null || limit < 1) {
            limit = 20;
        }
        if (limit > 100) {
            limit = 100; // chặn ?limit=1000000 kéo cả bảng về
        }
        if (sortBy == null) {
            sortBy = "createdAt";
        }
        if (order == null || (!order.equals("asc") && !order.equals("desc"))) {
            order = "desc";
        }
    }

    public void validate() {
        if (!SORT_FIELDS.contains(sortBy)) {
            throw new FieldValidationException("sortBy", "Chỉ được sắp xếp theo: createdAt, view, sold, price");
        }
    }
}
