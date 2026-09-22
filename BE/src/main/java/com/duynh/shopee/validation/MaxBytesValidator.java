package com.duynh.shopee.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Phần "chạy thật" của @MaxBytes. Hibernate Validator tự tạo một thể hiện của class này,
 * gọi initialize() một lần để đọc tham số trong annotation, rồi gọi isValid() cho mỗi giá trị.
 */
public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

    /**
     * Câu thay thế cho trường hợp chuỗi NGẮN mà vẫn vượt trần byte. Người gõ 25 kí tự rồi bị
     * báo "quá dài" sẽ không hiểu nếu không nói thêm nguyên nhân; còn người gõ 200 kí tự thì
     * tự biết, nói thêm về dấu chỉ làm họ rối.
     */
    private static final String MULTIBYTE_MESSAGE =
            "Mật khẩu quá dài, vui lòng bớt kí tự có dấu hoặc kí tự đặc biệt";

    private int max;

    @Override
    public void initialize(MaxBytes annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null coi là HỢP LỆ — giống mọi constraint khác ngoài @NotNull/@NotBlank. Nhờ vậy
        // field optional (newPassword) không cần thêm luật riêng.
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length <= max) {
            return true;
        }

        if (value.length() <= max) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(MULTIBYTE_MESSAGE).addConstraintViolation();
        }
        return false;
    }
}
