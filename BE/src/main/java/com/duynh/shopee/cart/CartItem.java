package com.duynh.shopee.cart;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.duynh.shopee.product.Product;
import com.duynh.shopee.user.User;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uq_cart_item", columnNames = { "user_id",
        "product_id" }))
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @Column(nullable = false)
    private int quantity;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public CartItem(User user, Product product, int quantity) {
        this.user = user;
        this.product = product;
        this.quantity = quantity;
    }

    public void addQuantity(int quantity) {
        this.quantity += quantity;
    }
}
