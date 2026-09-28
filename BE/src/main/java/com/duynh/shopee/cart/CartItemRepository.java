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

    /**
     * Lấy các dòng giỏ hàng mà user đã tick chọn để đặt hàng. Dùng ở OrderService.placeOrder.
     *
     * Chỉ trả về những dòng:
     *   - có id nằm trong danh sách user gửi lên, VÀ
     *   - thuộc giỏ hàng của chính user này (id của người khác bị loại ngay trong câu query), VÀ
     *   - trỏ tới sản phẩm còn đang bán (sản phẩm đã xoá mềm thì dòng đó bị bỏ qua).
     *
     * Vì vậy: số dòng trả về ít hơn số id gửi lên = có id không hợp lệ.
     *
     * Nạp kèm luôn product của mỗi dòng, vì lúc tạo đơn cần chép tên, giá, ảnh của nó.
     *
     * Sắp theo product id: đây là thứ tự trừ kho. Mọi đơn cùng trừ kho theo một thứ tự
     * thì hai đơn đặt cùng lúc không bao giờ chờ nhau vòng tròn (deadlock).
     */

    @Query("""
            SELECT ci FROM CartItem ci
            JOIN FETCH ci.product p
            WHERE ci.id IN :ids AND ci.user.id = :userId
            ORDER BY p.id
            """)
    List<CartItem> findAllForCheckout(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
