package org.example.ecommerce.product;

public record CreateProductRequest(String name, double price, int stock) {
}