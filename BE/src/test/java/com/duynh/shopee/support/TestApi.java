package com.duynh.shopee.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Gọi API của app đang chạy trong test, và dựng sẵn dữ liệu cho test (tài khoản, sản phẩm, giỏ, đơn).
 * - Mọi test dựng dữ liệu qua đây, không tự viết request: khi API đổi hình dạng (ví dụ thêm phân loại hàng) thì chỉ
 *   sửa class này, các test giữ nguyên.
 * - Mọi thứ tạo ra đều mang tên mới (email, tên danh mục có số thứ tự + chuỗi ngẫu nhiên), nên test không đụng dữ liệu
 *   của test khác dù dùng chung một DB.
 * - Response 4xx/5xx không ném exception: trả về {@link Response} để test tự kiểm status và `type`.
 */
public class TestApi {
    public static final String PASSWORD = "123456";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final RestClient http;
    private final JdbcTemplate jdbc;

    public TestApi(String baseUrl, JdbcTemplate jdbc) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.jdbc = jdbc;
    }

    /** Một response bất kỳ: status và body JSON (null nếu không có body, ví dụ 204). */
    public record Response(int status, JsonNode body) {
        /** `type` của ProblemDetail, ví dụ "/errors/conflict"; null nếu body không có. */
        public String type() {
            return body == null || body.get("type") == null ? null : body.get("type").asString();
        }

        /** Đòi đúng status mong đợi; sai thì báo kèm body để biết vì sao. */
        public Response expect(int expectedStatus) {
            if (status != expectedStatus) {
                throw new AssertionError("Mong đợi " + expectedStatus + " nhưng nhận " + status + ": " + body);
            }
            return this;
        }
    }

    /** Một người đang đăng nhập: email và cặp token. */
    public record Session(String email, String accessToken, String refreshToken) {
    }

    // ---- HTTP thô ----

    public Response get(String path, Session who) {
        return send(http.get().uri(path), who);
    }

    public Response post(String path, Object body, Session who) {
        return send(http.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body), who);
    }

    public Response put(String path, Object body, Session who) {
        return send(http.put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body), who);
    }

    public Response delete(String path, Session who) {
        return send(http.delete().uri(path), who);
    }

    /** Chỉ lấy status, bỏ body — dùng cho thứ không phải JSON (ảnh ở /images/**). */
    public int statusOf(String path) {
        return http.get().uri(path).exchange((request, response) -> response.getStatusCode().value());
    }

    private Response send(RestClient.RequestHeadersSpec<?> spec, Session who) {
        if (who != null) {
            spec.header("Authorization", "Bearer " + who.accessToken());
        }
        return spec.exchange((request, response) -> new Response(response.getStatusCode().value(),
                response.bodyTo(JsonNode.class)));
    }

    // ---- Tài khoản ----

    /** Email chưa ai dùng, ví dụ "u7-3f9a1c2e@test.vn". */
    public static String newEmail() {
        return "u" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.vn";
    }

    /** Đăng ký một user mới (role USER), trả về phiên đăng nhập của user đó. */
    public Session register() {
        String email = newEmail();
        Response r = post("/api/auth/register", Map.of("email", email, "password", PASSWORD), null).expect(201);
        return session(email, r);
    }

    public Session login(String email, String password) {
        Response r = post("/api/auth/login", Map.of("email", email, "password", password), null).expect(200);
        return session(email, r);
    }

    /**
     * Một admin mới. Không có API nào cấp quyền ADMIN, nên nâng role bằng SQL như cách tạo admin thật, rồi đăng
     * nhập lại: role nằm trong access token, token phát lúc còn là USER thì vẫn mang USER.
     */
    public Session admin() {
        Session user = register();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", user.email());
        return login(user.email(), PASSWORD);
    }

    private static Session session(String email, Response r) {
        return new Session(email, r.body().get("accessToken").asString(), r.body().get("refreshToken").asString());
    }

    // ---- Danh mục, sản phẩm, tồn kho ----

    public long createCategory(Session admin) {
        String name = "Danh mục " + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID().toString().substring(0, 8);
        return id(post("/api/admin/categories", Map.of("name", name), admin).expect(201));
    }

    /** Một sản phẩm mới giá 100.000₫, còn `stock` cái, nằm trong một danh mục mới. */
    public long createProduct(Session admin, int stock) {
        Map<String, Object> body = Map.of(
                "name", "Sản phẩm test " + SEQUENCE.incrementAndGet(),
                "price", 100_000,
                "quantity", stock,
                "categoryId", createCategory(admin));
        return id(post("/api/admin/products", body, admin).expect(201));
    }

    /** Tồn kho hiện tại của sản phẩm, đọc qua API công khai. */
    public int stockOf(long productId) {
        return get("/api/products/" + productId, null).expect(200).body().get("quantity").asInt();
    }

    /** Số đã bán của sản phẩm. */
    public int soldOf(long productId) {
        return get("/api/products/" + productId, null).expect(200).body().get("sold").asInt();
    }

    // ---- Giỏ hàng, đơn hàng ----

    /** Thêm vào giỏ, trả về id dòng giỏ. Trả 200 chứ không 201: sản phẩm đã có trong giỏ thì cộng dồn (§7.2). */
    public long addToCart(Session user, long productId, int quantity) {
        return id(post("/api/cart/items", Map.of("productId", productId, "quantity", quantity), user).expect(200));
    }

    /** Đặt hàng (COD) với các dòng giỏ đã chọn; trả nguyên response để test tự kiểm 201 hay 409. */
    public Response placeOrder(Session user, List<Long> cartItemIds) {
        Map<String, Object> body = Map.of(
                "cartItemIds", cartItemIds,
                "recipientName", "Người Nhận Test",
                "recipientPhone", "0912345678",
                "shippingAddress", "1 Đường Test, Quận 1, TP.HCM",
                "paymentMethod", "COD");
        return post("/api/orders", body, user);
    }

    /** Mua `quantity` cái của sản phẩm: thêm vào giỏ rồi đặt ngay; trả về id đơn. */
    public long buy(Session user, long productId, int quantity) {
        long cartItemId = addToCart(user, productId, quantity);
        return id(placeOrder(user, List.of(cartItemId)).expect(201));
    }

    /** Id trong body, API trả id dạng chuỗi (API_SPEC §1.3). */
    public static long id(Response r) {
        return Long.parseLong(r.body().get("id").asString());
    }
}
