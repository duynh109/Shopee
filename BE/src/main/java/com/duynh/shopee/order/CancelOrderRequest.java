package com.duynh.shopee.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(
        @NotBlank(message = "Lý do hủy đơn không được để trống") @Size(max = 255, message = "Lý do hủy đơn không được dài quá 255 ký tự") String cancelledReason) {

}
