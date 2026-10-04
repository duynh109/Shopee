package com.duynh.shopee.product;

/**
 * "Sản phẩm này vừa bị admin gỡ khỏi sàn." ProductService phát ra, module nào quan tâm thì tự nghe.
 * Chỉ mang id: người nghe cần gì thêm thì tự đọc — mà sản phẩm đã xoá mềm thì cũng không đọc lại được qua repository nữa (@SQLRestriction lọc mất).
 */

public record ProductDeletedEvent(Long productId) {

}
