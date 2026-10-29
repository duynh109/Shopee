package com.duynh.shopee.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi.Response;
import com.duynh.shopee.support.TestApi.Session;

/**
 * Phân quyền hai tầng (API_SPEC §1.10).
 * - Tầng 1, SecurityConfig: "đã đăng nhập chưa, role gì" → 401 / 403.
 * - Tầng 2, service: "bản ghi này có phải của người đang gọi không" → 404, giống hệt bản ghi không tồn tại,
 *   để người dò không biết id đó có thật.
 */
class AuthorizationIT extends IntegrationTest {

    // ---- Tầng 1 ----

    @Test
    void khongGuiToken_goiApiCanDangNhap_401() {
        Response r = api.get("/api/users/me", null);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.type()).isEqualTo("/errors/token-invalid");
    }

    @Test
    void apiCongKhai_khongCanToken() {
        api.get("/api/products", null).expect(200);
        api.get("/api/categories", null).expect(200);
    }

    @Test
    void userThuong_goiApiAdmin_403() {
        Session user = api.register();

        Response r = api.get("/api/admin/users", user);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.type()).isEqualTo("/errors/forbidden");
        assertThat(api.post("/api/admin/categories", Map.of("name", "Không được tạo"), user).status()).isEqualTo(403);
    }

    @Test
    void admin_goiApiAdmin_200() {
        api.get("/api/admin/users", api.admin()).expect(200);
    }

    @Test
    void tokenBiSuaRoleThanhAdmin_giuChuKyCu_401() {
        // Payload của JWT ai cũng đọc và sửa được (chỉ là Base64), nhưng chữ ký không còn khớp.
        Session user = api.register();
        String[] parts = user.accessToken().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"USER\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"").getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        Response r = api.get("/api/admin/users", new Session(user.email(), forged, null));
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.type()).isEqualTo("/errors/token-invalid");
    }

    // ---- Tầng 2: A đụng vào dữ liệu của B ----

    @Test
    void xemDonCuaNguoiKhac_404_giongHetDonKhongTonTai() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long orderOfB = api.buy(b, api.createProduct(admin, 5), 1);

        Response foreign = api.get("/api/orders/" + orderOfB, a);
        Response missing = api.get("/api/orders/999999999", a);
        assertThat(foreign.status()).isEqualTo(404);
        assertThat(foreign.body().get("title")).isEqualTo(missing.body().get("title"));
        api.get("/api/orders/" + orderOfB, b).expect(200);
    }

    @Test
    void danhSachDonChiCoDonCuaMinh() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long product = api.createProduct(admin, 5);
        long orderOfA = api.buy(a, product, 1);
        api.buy(b, product, 1);

        Response list = api.get("/api/orders", a).expect(200);
        assertThat(list.body().get("totalItems").asInt()).isEqualTo(1);
        assertThat(list.body().get("items").get(0).get("id").asString()).isEqualTo(String.valueOf(orderOfA));
    }

    @Test
    void huyDonCuaNguoiKhac_404_vaDonVanNguyen() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long orderOfB = api.buy(b, api.createProduct(admin, 5), 1);

        assertThat(api.put("/api/orders/" + orderOfB + "/cancel", Map.of("cancelledReason", "Huỷ hộ"), a).status())
                .isEqualTo(404);
        assertThat(api.get("/api/orders/" + orderOfB, b).body().get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void suaHayXoaDongGioCuaNguoiKhac_404_vaDongVanCon() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long itemOfB = api.addToCart(b, api.createProduct(admin, 5), 1);

        assertThat(api.put("/api/cart/items/" + itemOfB, Map.of("quantity", 3), a).status()).isEqualTo(404);
        assertThat(api.delete("/api/cart/items/" + itemOfB, a).status()).isEqualTo(404);
        Response cartOfB = api.get("/api/cart", b).expect(200);
        assertThat(cartOfB.body().size()).isEqualTo(1);
        assertThat(cartOfB.body().get(0).get("quantity").asInt()).isEqualTo(1);
    }

    @Test
    void xoaNhieuDongGio_boQuaImLangIdCuaNguoiKhac() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long product = api.createProduct(admin, 5);
        long itemOfA = api.addToCart(a, product, 1);
        long itemOfB = api.addToCart(b, product, 1);

        api.delete("/api/cart/items?ids=" + itemOfA + "," + itemOfB, a).expect(204);

        assertThat(api.get("/api/cart", a).body().size()).isZero();
        assertThat(api.get("/api/cart", b).body().size()).isEqualTo(1);
    }

    @Test
    void datHangBangDongGioCuaNguoiKhac_404_khongTruKho() {
        Session admin = api.admin();
        Session a = api.register();
        Session b = api.register();
        long product = api.createProduct(admin, 5);
        long itemOfB = api.addToCart(b, product, 2);

        assertThat(api.placeOrder(a, List.of(itemOfB)).status()).isEqualTo(404);
        assertThat(api.stockOf(product)).isEqualTo(5);
        assertThat(api.get("/api/cart", b).body().size()).isEqualTo(1);
    }
}
