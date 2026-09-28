package com.duynh.shopee.order;

public record OrderQuery(OrderStatus status, Integer page, Integer limit) {
    public OrderQuery {
        if (page == null || page < 1) {
            page = 1;
        }
        if (limit == null || limit < 1) {
            limit = 10;
        }
        if (limit > 50) {
            limit = 50;
        }
    }
}
