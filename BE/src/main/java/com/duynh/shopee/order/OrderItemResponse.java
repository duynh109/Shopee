package com.duynh.shopee.order;

// Một dòng trong đơn — API_SPEC §2.6. Mọi field đọc từ cột snapshot, không đọc từ products.
public record OrderItemResponse(
        String id,
        String productId,
        String productName,
        String productImage,
        long price,
        Long priceBeforeDiscount,
        int quantity) {
    public static OrderItemResponse from(OrderItem orderItem) {
        return new OrderItemResponse(
                orderItem.getId().toString(),
                orderItem.getProduct().getId().toString(),
                orderItem.getProductName(),
                orderItem.getProductImage(),
                orderItem.getPrice(),
                orderItem.getPriceBeforeDiscount(),
                orderItem.getQuantity());
    }
}
