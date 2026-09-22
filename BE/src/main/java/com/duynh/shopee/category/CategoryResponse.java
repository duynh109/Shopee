package com.duynh.shopee.category;

public record CategoryResponse(String id, String name) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId().toString(), category.getName());
    }
}
