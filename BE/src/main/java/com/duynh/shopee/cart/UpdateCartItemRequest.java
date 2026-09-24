package com.duynh.shopee.cart;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateCartItemRequest(@NotNull @Positive Integer quantity) {

}
