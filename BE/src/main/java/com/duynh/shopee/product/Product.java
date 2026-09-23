package com.duynh.shopee.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.duynh.shopee.category.Category;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@SQLRestriction("deleted_at IS NULL") // soft delete: tự động thêm điều kiện WHERE deleted_at IS NULL cho mọi truy vấn
@Table(name = "products")
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Setter
    @Column(nullable = false)
    private Long price;

    @Setter
    private Long priceBeforeDiscount;

    @Setter
    @Column(nullable = false)
    private int quantity = 0;

    @Column(nullable = false)
    private int sold = 0;

    @Column(nullable = false)
    private int view = 0;

    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal rating = BigDecimal.ZERO;

    @Setter
    private String image;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @Setter
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Setter
    private Instant deletedAt;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Thay toàn bộ album ảnh. Không có setter cho `images` vì gán một List mới sẽ
     * làm hỏng
     * cơ chế orphanRemoval của Hibernate — nó theo dõi chính đối tượng collection
     * đang giữ.
     * clear() thì an toàn: Hibernate thấy các phần tử bị gỡ và tự phát câu DELETE
     * cho chúng.
     *
     * sortOrder lấy theo đúng vị trí trong mảng client gửi lên, nên thứ tự album do
     * client quyết.
     */
    public void replaceImages(List<String> urls) {
        images.clear();
        if (urls == null) {
            return;
        }
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage(urls.get(i), i);
            image.setProduct(this); // phía ManyToOne mới là bên giữ khoá ngoại
            images.add(image);
        }
    }

    public Product(String name, String description, Long price, Long priceBeforeDiscount, int quantity,
            Category category) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.priceBeforeDiscount = priceBeforeDiscount;
        this.quantity = quantity;
        this.category = category;
    }

}
