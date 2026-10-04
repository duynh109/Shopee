package com.duynh.shopee.user;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.duynh.shopee.storage.ImageStorageService;
import com.duynh.shopee.storage.UploadImageResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService userService;
    private final ImageStorageService imageStorageService;

    public UserController(UserService userService, ImageStorageService imageStorageService) {
        this.userService = userService;
        this.imageStorageService = imageStorageService;
    }

    /**
     * Danh tính lấy từ token qua {@code @AuthenticationPrincipal}, KHÔNG nhận id từ
     * client.
     * Nếu endpoint là /api/users/{id} thì phải tự kiểm tra id đó có đúng là người
     * đang gọi không,
     * quên một lần là mọi user đọc được profile của nhau. Dùng /me thì câu hỏi đó
     * không tồn tại.
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

    /**
     * User tải avatar lên. Chỉ lưu file và trả URL — KHÔNG sửa users.avatar;
     * FE lấy URL này gọi tiếp PUT /api/users/me (API_SPEC §4.3).
     * Quyền: đã đăng nhập — rule "/api/users/**" có sẵn trong SecurityConfig phủ
     * luôn đường dẫn này.
     */
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadImageResponse uploadAvatar(@RequestParam(value = "image", required = false) MultipartFile image) {
        String imageUrl = imageStorageService.store(image);
        return new UploadImageResponse(imageUrl);
    }
}
