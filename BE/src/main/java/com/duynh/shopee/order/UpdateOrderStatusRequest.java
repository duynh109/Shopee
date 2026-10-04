package com.duynh.shopee.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
                @NotNull(message = "Vui lòng chọn trạng thái") OrderStatus status,
                @Size(max = 255, message = "Lý do hủy đơn tối đa 255 ký tự") String cancelledReason) {

}
