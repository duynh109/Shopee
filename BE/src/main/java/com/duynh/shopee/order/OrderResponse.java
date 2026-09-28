package com.duynh.shopee.order;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String orderCode,
        OrderStatus status,
        long subtotal,
        long shippingFee,
        long discount,
        long totalAmount,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        String note,
        String cancelledReason,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt) {
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();

        return new OrderResponse(
                order.getId().toString(),
                order.getOrderCode(),
                order.getStatus(),
                order.getSubtotal(),
                order.getShippingFee(),
                order.getDiscount(),
                order.getTotalAmount(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingAddress(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getNote(),
                order.getCancelledReason(),
                items,
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
