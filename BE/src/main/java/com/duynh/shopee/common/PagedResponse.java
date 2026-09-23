package com.duynh.shopee.common;

import java.util.List;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int limit,
        long totalItems,
        int totalPages) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<T>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(),
                page.getTotalPages());
    }
}
