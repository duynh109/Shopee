package org.example.ecommerce.auth;

import org.example.ecommerce.user.UserResponse;

public record AuthResponse(String accessToken, long expires, UserResponse user) {

}
