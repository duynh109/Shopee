package com.duynh.shopee.auth;

public enum TokenError {

    MISSING("/errors/token-invalid", "Bạn cần đăng nhập để thực hiện thao tác này"),
    INVALID("/errors/token-invalid", "Token không hợp lệ"),
    EXPIRED("/errors/token-expired", "Token đã hết hạn");

    private final String type;
    private final String title;

    TokenError(String type, String title) {
        this.type = type;
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }
}
