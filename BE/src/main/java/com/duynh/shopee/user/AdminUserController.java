package com.duynh.shopee.user;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duynh.shopee.common.PagedResponse;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {
    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/admin/users?page=1&limit=10
    @GetMapping
    public PagedResponse<UserResponse> getAllUsers(@ModelAttribute UserQuery q) {
        return userService.getAllUsers(q);
    }
}
