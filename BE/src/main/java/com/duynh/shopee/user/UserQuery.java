package com.duynh.shopee.user;

public record UserQuery(Integer page, Integer limit) {
    public UserQuery {
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
