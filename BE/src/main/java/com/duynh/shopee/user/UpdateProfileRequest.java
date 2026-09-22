package com.duynh.shopee.user;

import java.time.LocalDate;

import com.duynh.shopee.validation.MaxBytes;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body của PUT /api/users/me. Mọi field đều optional: gửi field nào thì cập nhật field đó,
 * field để null coi như "đừng đổi" (xem API_SPEC §4.2).
 *
 * Không có email và role — hai thứ đó cố ý không cho sửa qua endpoint này. Client có gửi kèm
 * thì Jackson cũng bỏ qua vì record này không khai báo chúng.
 *
 * Lưu ý về validation: @Size, @Pattern, @Past đều coi null là HỢP LỆ theo chuẩn Bean Validation —
 * chỉ @NotNull/@NotBlank mới chặn null. Chính đặc điểm đó làm "tất cả field optional" chạy được
 * mà không cần viết thêm dòng nào.
 */
public record UpdateProfileRequest(
        @Size(max = 255, message = "Tên không được quá 255 kí tự")
        String name,

        @Pattern(regexp = "\\d{10,11}", message = "Số điện thoại không hợp lệ")
        String phone,

        @Size(max = 255, message = "Địa chỉ không được quá 255 kí tự")
        String address,

        @Past(message = "Ngày sinh phải là ngày trong quá khứ")
        LocalDate dateOfBirth,

        @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 kí tự")
        String avatar,

        /** Mật khẩu hiện tại — chỉ cần khi muốn đổi mật khẩu. */
        String password,

        @Size(min = 6, message = "Độ dài tối thiểu 6 kí tự")
        @MaxBytes(value = 72, message = "Mật khẩu tối đa 72 kí tự")
        String newPassword) {
}
