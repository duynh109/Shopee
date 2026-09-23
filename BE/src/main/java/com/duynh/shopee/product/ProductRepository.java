package com.duynh.shopee.product;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    @Query(value = "select count(*) from products where category_id = :categoryId", nativeQuery = true)
    long countByCategoryIdIncludingDeleted(@Param("categoryId") Long categoryId);

    @Override
    @EntityGraph(attributePaths = { "category" })
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Chi tiết sản phẩm cần cả danh mục lẫn album ảnh → nạp kèm cả hai. */
    @Override
    @EntityGraph(attributePaths = { "category", "images" })
    Optional<Product> findById(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.view = p.view + 1 WHERE p.id = :id")
    void increaseView(@Param("id") Long id);
}
