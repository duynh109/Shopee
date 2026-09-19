package com.duynh.shopee.user;

import com.duynh.shopee.exception.FieldValidationException;
import com.duynh.shopee.exception.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return UserResponse.from(findByEmailOrThrow(email));
    }

    @Transactional
    public UserResponse updateMe(String email, UpdateProfileRequest request) {
        User user = findByEmailOrThrow(email);

        // Đổi mật khẩu làm trước: nếu mật khẩu hiện tại sai thì ném lỗi ngay,
        // không để rơi vào cảnh profile đã đổi mà mật khẩu thì không.
        if (request.newPassword() != null) {
            changePassword(user, request.password(), request.newPassword());
        }

        if (request.name() != null) {
            user.setName(request.name());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone());
        }
        if (request.address() != null) {
            user.setAddress(request.address());
        }
        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }

        // saveAndFlush chứ không phải save: @LastModifiedDate được điền trong callback
        // @PreUpdate, mà callback đó chỉ chạy lúc Hibernate flush. save() thuần chỉ đánh dấu
        // entity là bẩn và hoãn flush tới lúc commit — tức là SAU khi method này return, nên
        // DTO dựng ra sẽ mang updatedAt cũ dù DB đã ghi đúng. flush() ép ghi ngay để entity
        // trong bộ nhớ và bản ghi dưới DB khớp nhau tại thời điểm dựng response.
        return UserResponse.from(userRepository.saveAndFlush(user));
    }

    private void changePassword(User user, String currentPassword, String newPassword) {
        if (currentPassword == null) {
            throw new FieldValidationException("password", "Vui lòng nhập mật khẩu hiện tại");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new FieldValidationException("password", "Mật khẩu không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
    }

    /**
     * Token đã qua được filter nên email chắc chắn hợp lệ; nhánh này chỉ xảy ra khi tài khoản
     * bị xoá trong lúc token cũ vẫn còn hạn. Trả 404 chứ không 401: người gọi đã xác thực xong,
     * thứ không tìm thấy là bản ghi.
     */
    private User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy người dùng"));
    }
}
