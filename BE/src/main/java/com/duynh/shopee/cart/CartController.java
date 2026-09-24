package com.duynh.shopee.cart;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // GET /api/cart
    @GetMapping
    public List<CartItemResponse> getMyCart(@AuthenticationPrincipal UserDetails principal) {
        return cartService.getMyCart(principal.getUsername());
    }

    // POST /api/cart/items
    @PostMapping("/items")
    public CartItemResponse addToCart(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AddToCartRequest request) {
        return cartService.addToCart(principal.getUsername(), request);
    }

    // PUT /api/cart/items/{id}
    @PutMapping("/items/{id}")
    public CartItemResponse updateQuantity(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.updateQuantity(id, principal.getUsername(), request);
    }

    // DELETE /api/cart/items/{id}
    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        cartService.deleteItem(id, principal.getUsername());
    }

    // DELETE /api/cart/items?ids=1,2,3
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItems(@RequestParam List<Long> ids, @AuthenticationPrincipal UserDetails principal) {
        cartService.deleteItems(ids, principal.getUsername());
    }
}
