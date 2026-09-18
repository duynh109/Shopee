package org.example.ecommerce.user;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO trả ra ngoài cho một user. Thứ tự field ở đây chính là thứ tự key trong JSON,
 * đặt theo đúng mẫu ở API_SPEC §2.1.
 *
 * Cố ý KHÔNG có field password — entity thì có, DTO thì không, nên không có cách nào
 * lộ hash BCrypt ra ngoài kể cả khi lỡ tay.
 */
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
                String.valueOf(user.getId()),
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
