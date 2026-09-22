package com.duynh.shopee.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record UserResponse(
        String id,
        List<String> roles,
        String email,
        String name,
        LocalDate dateOfBirth,
        String address,
        String phone,
        String avatar,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId().toString(),
                List.of(user.getRole()),
                user.getEmail(),
                user.getName(),
                user.getDateOfBirth(),
                user.getAddress(),
                user.getPhone(),
                user.getAvatar(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
