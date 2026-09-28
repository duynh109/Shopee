package com.duynh.shopee.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.duynh.shopee.exception.ConflictException;
import com.duynh.shopee.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "orders", indexes = @Index(name = "idx_order_user_status", columnList = "user_id, status"))
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true, length = 32)
    private String orderCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false)
    private long subtotal;

    @Column(nullable = false)
    private long shippingFee;

    @Column(nullable = false)
    private long discount;

    @Column(nullable = false)
    private long totalAmount;

    // snapshot thông tin nhận hàng
    @Column(nullable = false)
    private String recipientName;

    @Column(nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false)
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    @Column(length = 500)
    private String note;

    private String cancelledReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    @BatchSize(size = 50)
    private List<OrderItem> orderItems = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Order(User user, String orderCode, String recipientName, String recipientPhone, String shippingAddress,
            PaymentMethod paymentMethod, String note, long shippingFee) {
        this.user = user;
        this.orderCode = orderCode;
        this.shippingFee = shippingFee;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.shippingAddress = shippingAddress;
        this.paymentMethod = paymentMethod;
        this.note = note;
        this.discount = 0; // v1 chưa có voucher
        this.status = OrderStatus.PENDING;
        this.paymentStatus = PaymentStatus.UNPAID;
        recalculateTotal();
    }

    public void addItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
        this.subtotal += item.getPrice() * item.getQuantity();
        recalculateTotal();
    }

    public void cancel(String reason) {
        switch (status) {
            case PENDING, CONFIRMED -> {
                this.status = OrderStatus.CANCELLED;
                this.cancelledReason = reason;
            }
            case SHIPPING -> throw new ConflictException("Đơn hàng đang được giao, không thể hủy");
            case DELIVERED -> throw new ConflictException("Đơn hàng đã giao, không thể hủy");
            case CANCELLED -> throw new ConflictException("Đơn hàng đã được hủy trước đó");
        }
    }

    private void recalculateTotal() {
        this.totalAmount = this.subtotal + this.shippingFee - this.discount;
    }
}
