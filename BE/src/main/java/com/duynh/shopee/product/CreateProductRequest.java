package com.duynh.shopee.product;

public record CreateProductRequest(String name, double price, int stock) {
}