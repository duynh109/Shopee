package com.duynh.shopee.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;

public class ProductSpecifications {
    private ProductSpecifications() {
    }

    public static Specification<Product> withFilter(ProductQuery q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (q.category() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), q.category()));
            }
            if (q.name() != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + q.name().toLowerCase() + "%"));
            }
            if (q.priceMin() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), q.priceMin()));
            }
            if (q.priceMax() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), q.priceMax()));
            }
            if (q.ratingFilter() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("rating"), BigDecimal.valueOf(q.ratingFilter())));
            }
            if (q.exclude() != null) {
                predicates.add(cb.notEqual(root.get("id"), q.exclude()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
