package com.duynh.shopee.order;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.duynh.shopee.cart.CartItem;
import com.duynh.shopee.cart.CartService;
import com.duynh.shopee.common.PagedResponse;
import com.duynh.shopee.exception.FieldValidationException;
import com.duynh.shopee.exception.NotFoundException;
import com.duynh.shopee.product.ProductService;
import com.duynh.shopee.user.User;
import com.duynh.shopee.user.UserService;

@Service
public class OrderService {
    /**
     * v1: phí ship cố định — API_SPEC §2.5. Dấu _ trong số chỉ để dễ đọc, Java bỏ
     * qua nó.
     */
    private static final long SHIPPING_FEE = 30_000;

    /** Ngày trong mã đơn là ngày ở Việt Nam, không phải UTC — mục F. */
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderRepository orderRepository;
    private final CartService cartService;
    private final ProductService productService;
    private final UserService userService;

    public OrderService(OrderRepository orderRepository, CartService cartService, ProductService productService,
            UserService userService) {
        this.orderRepository = orderRepository;
        this.cartService = cartService;
        this.productService = productService;
        this.userService = userService;
    }

    @Transactional
    public OrderResponse placeOrder(String email, CreateOrderRequest request) {
        if (request.paymentMethod() != PaymentMethod.COD) {
            throw new FieldValidationException("paymentMethod", "Hiện chỉ hỗ trợ thanh toán khi nhận hàng (COD)");
        }
        User user = userService.getEntityByEmail(email);
        List<Long> ids = request.cartItemIds().stream().distinct().toList(); // ["1","1"] chỉ tính một lần

        // Bước 1–2: dòng giỏ của chính mình, sản phẩm còn bán. Thiếu dòng nào → 404
        List<CartItem> cartItems = cartService.getItemsForCheckout(ids, user.getId());

        // Bước 3: trừ kho từng món. Món nào không đủ → 409 và huỷ cả đơn
        for (CartItem ci : cartItems) {
            productService.decreaseStock(ci.getProduct(), ci.getQuantity());
        }

        // Bước 4–6: tạo đơn và chép tên/giá/ảnh vào từng dòng. Tổng tiền do
        // Order.addItem() tự cộng
        Order order = new Order(user, generateOrderCode(), request.recipientName(), request.recipientPhone(),
                request.shippingAddress(), request.paymentMethod(), request.note(), SHIPPING_FEE);
        for (CartItem ci : cartItems) {
            order.addItem(new OrderItem(ci.getProduct(), ci.getQuantity()));
        }
        orderRepository.save(order);

        // Bước 7: xoá các dòng đã đặt. Luôn là thao tác DB cuối cùng
        cartService.removeCheckedoutItems(ids, user.getId());

        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public PagedResponse<OrderResponse> getMyOrders(String email, OrderQuery q) {
        User user = userService.getEntityByEmail(email);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")); // mới → cũ
        Pageable pageable = PageRequest.of(q.page() - 1, q.limit(), sort); // Spring đếm từ trang 0

        Page<Order> orders;
        if (q.status() == null) {
            orders = orderRepository.findByUserId(user.getId(), pageable);
        } else {
            orders = orderRepository.findByUserIdAndStatus(user.getId(), q.status(), pageable);
        }

        return PagedResponse.from(orders.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(String email, Long id) {
        User user = userService.getEntityByEmail(email);
        return OrderResponse.from(findOwnedOrThrow(id, user.getId()));
    }

    @Transactional
    public OrderResponse cancelMyOrder(String email, Long id, CancelOrderRequest request) {
        User user = userService.getEntityByEmail(email);
        Order order = orderRepository.findByIdAndUserIdForUpdate(id, user.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
        order.cancel(request.cancelledReason());

        // Trả lại kho từng món. Không cần check số lượng vì đơn đã trừ kho thành công
        for (OrderItem item : order.getOrderItems()) {
            productService.increaseStock(item.getProduct().getId(), item.getQuantity());
        }
        orderRepository.saveAndFlush(order);
        return OrderResponse.from(order);
    }

    private String generateOrderCode() {
        String date = LocalDate.now(VIETNAM).format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
        String code;
        do {
            int random = ThreadLocalRandom.current().nextInt(1_000_000); // 0..999999
            code = "SP" + date + String.format("%06d", random); // SPyyyyMMddxxxxxx
        } while (orderRepository.existsByOrderCode(code));
        return code;
    }

    private Order findOwnedOrThrow(Long id, Long userId) {
        return orderRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng"));
    }
}
