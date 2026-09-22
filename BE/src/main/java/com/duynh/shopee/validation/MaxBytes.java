package com.duynh.shopee.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Giới hạn độ dài một chuỗi theo SỐ BYTE khi mã hoá UTF-8, khác với @Size vốn đếm số KÍ TỰ.
 *
 * Sinh ra vì BCrypt chỉ băm được tối đa 72 byte và Spring Security 7 ném
 * IllegalArgumentException khi vượt quá — tức là 500 chứ không phải 422. @Size(max = 72)
 * không đủ: một kí tự tiếng Việt có dấu chiếm 3 byte UTF-8, nên 24 kí tự "mật khẩu có dấu"
 * đã chạm trần trong khi @Size vẫn thấy mới có 24.
 *
 * Ba method message/groups/payload là bắt buộc với mọi constraint của Bean Validation —
 * thiếu một cái là framework từ chối annotation lúc khởi động.
 */
@Documented
@Target({ FIELD, METHOD, PARAMETER, CONSTRUCTOR, ANNOTATION_TYPE })
@Retention(RUNTIME)
@Constraint(validatedBy = MaxBytesValidator.class)
public @interface MaxBytes {

    /** Số byte tối đa cho phép. */
    int value();

    String message() default "Giá trị quá dài";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
