package com.duynh.shopee.order;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.duynh.shopee.product.Product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AccessLevel;

@Getter
@Entity
@Table(name = "order_items")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    // snapshot tại thời điểm đặt hàng
    @Column(nullable = false)
    private String productName;

    private String productImage;

    @Column(nullable = false)
    private long price;

    private Long priceBeforeDiscount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.productName = product.getName();
        this.productImage = product.getImage();
        this.price = product.getPrice();
        this.priceBeforeDiscount = product.getPriceBeforeDiscount();
    }
}
