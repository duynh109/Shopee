package com.duynh.shopee.order;

import java.util.List;

import com.duynh.shopee.validation.ValidationPatterns;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotEmpty(message = "Vui lòng chọn sản phẩm để đặt hàng") List<@NotNull(message = "Vui lòng chọn sản phẩm để đặt hàng") Long> cartItemIds,

        @NotBlank(message = "Vui lòng nhập tên người nhận") @Size(max = 255, message = "Tên người nhận không được vượt quá 255 ký tự") String recipientName,

        @NotNull(message = "Vui lòng nhập số điện thoại người nhận") @Pattern(regexp = ValidationPatterns.PHONE, message = "Số điện thoại không hợp lệ") String recipientPhone,

        @NotBlank(message = "Vui lòng nhập địa chỉ nhận hàng") @Size(max = 255, message = "Địa chỉ nhận hàng không được vượt quá 255 ký tự") String shippingAddress,

        @NotNull(message = "Vui lòng chọn phương thức thanh toán") PaymentMethod paymentMethod,

        @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự") String note) {

}
