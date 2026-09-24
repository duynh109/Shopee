package com.duynh.shopee.cart;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("""
            SELECT ci FROM CartItem ci
            JOIN FETCH ci.product p
            JOIN FETCH p.category
            WHERE ci.user.id = :userId
            ORDER BY ci.createdAt DESC
            """)
    List<CartItem> findAllByUserId(@Param("userId") Long userId);

    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);

    @Query("SELECT ci FROM CartItem ci JOIN FETCH ci.product p JOIN FETCH p.category WHERE ci.id = :id AND ci.user.id = :userId")
    Optional<CartItem> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.id IN :ids AND c.user.id = :userId")
    int deleteByIdInAndUserId(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
