package com.duynh.shopee.auth;

import com.duynh.shopee.exception.UnauthorizedException;

/**
 * Refresh token không dùng được: không tồn tại, hết hạn, hoặc đã bị thu hồi.
 * Cố ý không nói là lý do nào — FE xử lý cả ba như nhau: đưa về màn đăng nhập.
 * Type khác /errors/token-expired: FE gặp token-expired thì tự gọi refresh; nếu refresh cũng trả
 * token-expired thì FE gọi refresh mãi không dừng.
 */
public class InvalidRefreshTokenException extends UnauthorizedException {
    public InvalidRefreshTokenException() {
        super("/errors/refresh-token-invalid", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
    }
}
