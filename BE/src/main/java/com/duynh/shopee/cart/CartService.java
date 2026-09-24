package com.duynh.shopee.cart;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duynh.shopee.exception.FieldValidationException;
import com.duynh.shopee.exception.NotFoundException;
import com.duynh.shopee.product.Product;
import com.duynh.shopee.product.ProductService;
import com.duynh.shopee.user.User;
import com.duynh.shopee.user.UserService;

@Service
public class CartService {
    private final CartItemRepository cartItemRepository;
    private final UserService userService;
    private final ProductService productService;

    public CartService(CartItemRepository cartItemRepository, UserService userService, ProductService productService) {
        this.cartItemRepository = cartItemRepository;
        this.userService = userService;
        this.productService = productService;
    }

    // Get /api/cart
    @Transactional(readOnly = true)
    public List<CartItemResponse> getMyCart(String email) {
        User user = userService.getEntityByEmail(email);
        List<CartItem> cartItems = cartItemRepository.findAllByUserId(user.getId());
        return cartItems.stream().map(CartItemResponse::from).toList();
    }

    // POST /api/cart/items - có thì cộng dồn chưa có thì tạo mới
    @Transactional
    public CartItemResponse addToCart(String email, AddToCartRequest request) {
        User user = userService.getEntityByEmail(email);
        Product product = productService.getEntityById(request.productId());
        CartItem item = cartItemRepository.findByUserIdAndProductId(user.getId(), product.getId())
                .orElseGet(() -> new CartItem(user, product, 0));

        long total = (long) item.getQuantity() + request.quantity();
        validateStock(product, total);
        item.addQuantity(request.quantity());
        return CartItemResponse.from(cartItemRepository.save(item));
    }

    // PUT /api/cart/items/{id}
    @Transactional
    public CartItemResponse updateQuantity(Long id, String email, UpdateCartItemRequest request) {
        User user = userService.getEntityByEmail(email);
        CartItem item = findOwnedOrThrow(id, user.getId());
        validateStock(item.getProduct(), request.quantity());
        item.setQuantity(request.quantity());
        return CartItemResponse.from(cartItemRepository.saveAndFlush(item));
    }

    // DELETE /api/cart/items/{id}
    @Transactional
    public void deleteItem(Long id, String email) {
        User user = userService.getEntityByEmail(email);
        CartItem item = findOwnedOrThrow(id, user.getId());
        cartItemRepository.delete(item);
    }

    // DELETE /api/cart/items?ids=1,2,3
    @Transactional
    public void deleteItems(List<Long> ids, String email) {
        if (ids == null || ids.isEmpty()) {
            throw new FieldValidationException("ids", "Danh sách id không được để trống");
        }
        User user = userService.getEntityByEmail(email);
        cartItemRepository.deleteByIdInAndUserId(ids, user.getId());
    }

    private CartItem findOwnedOrThrow(Long id, Long userId) {
        return cartItemRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy sản phẩm trong giỏ hàng"));
    }

    private void validateStock(Product product, long wanted) {
        if (wanted > product.getQuantity()) {
            throw new FieldValidationException("quantity", "Số lượng vượt quá số lượng sản phẩm trong kho");
        }
    }
}
