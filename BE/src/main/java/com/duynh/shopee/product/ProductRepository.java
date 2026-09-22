package com.duynh.shopee.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    boolean existsByCategoryId(Long categoryId);

    @Override
    @EntityGraph(attributePaths = { "category" })
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = { "category" })
    Optional<Product> findById(Long id);
}
