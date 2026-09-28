package com.duynh.shopee.order;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.duynh.shopee.common.PagedResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // POST /api/orders
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.placeOrder(principal.getUsername(), request);
    }

    // GET /api/orders?status=PENDING&page=1&limit=10
    @GetMapping
    public PagedResponse<OrderResponse> getMyOrders(@AuthenticationPrincipal UserDetails principal,
            @ModelAttribute OrderQuery q) {
        return orderService.getMyOrders(principal.getUsername(), q);
    }

    // GET /api/orders/{id}
    @GetMapping("/{id}")
    public OrderResponse getMyOrder(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id) {
        return orderService.getMyOrder(principal.getUsername(), id);
    }

    // PUT /api/orders/{id}/cancel
    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@AuthenticationPrincipal UserDetails principal, @PathVariable Long id,
            @Valid @RequestBody CancelOrderRequest request) {
        return orderService.cancelMyOrder(principal.getUsername(), id, request);
    }
}
