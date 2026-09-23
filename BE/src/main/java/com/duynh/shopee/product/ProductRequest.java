package com.duynh.shopee.product;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body của POST và PUT /api/admin/products — hai bên giống hệt nhau (§9) nên dùng chung.
 *
 * Vào là categoryId + mảng URL; ra là object category + mảng URL (§2.3). Hai chiều cố ý
 * không đối xứng: client chỉ cần biết id, còn response trả sẵn tên danh mục để FE khỏi
 * gọi thêm một API nữa mới hiển thị được.
 *
 * Mọi field số đều dùng kiểu BỌC (Long/Integer) chứ không phải nguyên thuỷ. Lý do: int
 * không thể null, nên client quên gửi quantity thì Java tự cho 0 và @NotNull không có gì
 * để bắt — lặng lẽ tạo ra sản phẩm tồn kho 0 thay vì báo lỗi.
 */
public record ProductRequest(
        @NotBlank(message = "Tên sản phẩm không được để trống")
        @Size(max = 255, message = "Tên sản phẩm không được quá 255 kí tự")
        String name,

        /** Không bắt buộc, không giới hạn độ dài — cột TEXT. */
        String description,

        @NotNull(message = "Giá là bắt buộc")
        @Positive(message = "Giá phải lớn hơn 0")
        Long price,

        /** Giá gốc trước giảm. Null nghĩa là không giảm giá. */
        @Positive(message = "Giá gốc phải lớn hơn 0")
        Long priceBeforeDiscount,

        @NotNull(message = "Số lượng là bắt buộc")
        @PositiveOrZero(message = "Số lượng không được âm")
        Integer quantity,

        @Size(max = 255, message = "Đường dẫn ảnh không được quá 255 kí tự")
        String image,

        /**
         * Album ảnh. @NotBlank nằm BÊN TRONG dấu ngoặc nhọn nên nó kiểm từng phần tử;
         * đặt bên ngoài thì thành kiểm cả List, vốn không có nghĩa gì.
         */
        List<@NotBlank(message = "Đường dẫn ảnh không được để trống") String> images,

        @NotNull(message = "Vui lòng chọn danh mục")
        Long categoryId) {
}
