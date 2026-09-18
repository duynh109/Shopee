package org.example.ecommerce.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Danh tính lấy từ token qua {@code @AuthenticationPrincipal}, KHÔNG nhận id từ client.
     * Nếu endpoint là /api/users/{id} thì phải tự kiểm tra id đó có đúng là người đang gọi không,
     * quên một lần là mọi user đọc được profile của nhau. Dùng /me thì câu hỏi đó không tồn tại.
     */
    @GetMapping("/me")
    public UserResponse getMe(@AuthenticationPrincipal UserDetails principal) {
        return userService.getMe(principal.getUsername());
    }

    @PutMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateMe(principal.getUsername(), request);
    }
}
