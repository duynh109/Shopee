package com.duynh.shopee.order;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.duynh.shopee.common.PagedResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // PUT /api/admin/orders/{id}/status
    @PutMapping("/{id}/status")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.changeStatus(id, request);
    }

    // GET /api/admin/orders?status=PENDING&page=1&limit=10
    @GetMapping
    public PagedResponse<OrderResponse> getAllOrders(@ModelAttribute OrderQuery q) {
        return orderService.getAllOrders(q);
    }
}
