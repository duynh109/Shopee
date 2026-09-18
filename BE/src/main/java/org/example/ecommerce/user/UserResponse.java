package org.example.ecommerce.user;

import java.util.List;

public record UserResponse(String id, String email, List<String> roles) {
    public static UserResponse from(User user) {
        return new UserResponse(
            String.valueOf(user.getId()),
            user.getEmail(),
            List.of(user.getRole())
        );
    }
}
