package com.duynh.shopee.auth;

import com.duynh.shopee.user.UserResponse;

public record AuthResponse(String accessToken, long expires, UserResponse user) {

}
