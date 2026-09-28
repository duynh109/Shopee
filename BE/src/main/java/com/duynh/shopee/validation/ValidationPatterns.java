package com.duynh.shopee.validation;

public final class ValidationPatterns {
    private ValidationPatterns() {
    }

    /**
     * Số Việt Nam dạng trong nước: 0 + 9–10 chữ số. Dùng chung cho hồ sơ user và
     * người nhận hàng.
     */
    public static final String PHONE = "0\\d{9,10}";
}
