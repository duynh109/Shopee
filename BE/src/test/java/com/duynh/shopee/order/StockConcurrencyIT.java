package com.duynh.shopee.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.Concurrently;
import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi.Session;

/**
 * Tồn kho đúng cả khi nhiều request đến cùng lúc — bốn kịch bản đo ở DATABASE_DESIGN.md §5.
 * Mỗi kịch bản giữ các request ở một vạch xuất phát rồi thả ra cùng lúc ({@link Concurrently}).
 */
class StockConcurrencyIT extends IntegrationTest {

    @Test
    void haiMuoiNguoiCungMuaNamMonCuoi_dungNamDon_khongBanVuot() {
        Session admin = api.admin();
        long product = api.createProduct(admin, 5);
        List<Session> buyers = new ArrayList<>();
        List<Long> cartItems = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Session buyer = api.register();
            buyers.add(buyer);
            cartItems.add(api.addToCart(buyer, product, 1));
        }

        List<Integer> statuses = Concurrently.run(20,
                i -> api.placeOrder(buyers.get(i), List.of(cartItems.get(i))).status());

        assertThat(Collections.frequency(statuses, 201)).isEqualTo(5);
        assertThat(Collections.frequency(statuses, 409)).isEqualTo(15);
        assertThat(api.stockOf(product)).isZero();
        assertThat(api.soldOf(product)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE product_id = ?", Integer.class, product))
                .isEqualTo(5);
    }

    @Test
    void bamDatHangHaiLanCungLuc_chiMotDon_khoTruMotLan() {
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);
        long cartItem = api.addToCart(user, product, 2);

        List<Integer> statuses = Concurrently.run(2, i -> api.placeOrder(user, List.of(cartItem)).status());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(api.get("/api/orders", user).body().get("totalItems").asInt()).isEqualTo(1);
        assertThat(api.stockOf(product)).isEqualTo(3);
    }

    @Test
    void namRequestHuyCungMotDonCungLuc_khoChiHoanMotLan() {
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);
        long order = api.buy(user, product, 2);

        List<Integer> statuses = Concurrently.run(5, i -> api.put("/api/orders/" + order + "/cancel",
                Map.of("cancelledReason", "Đổi ý"), user).status());

        assertThat(Collections.frequency(statuses, 200)).isEqualTo(1);
        assertThat(Collections.frequency(statuses, 409)).isEqualTo(4);
        assertThat(api.stockOf(product)).isEqualTo(5);
        assertThat(api.soldOf(product)).isZero();
    }

    @RepeatedTest(5)
    void adminChuyenDangGiaoCungLucKhachHuy_dungMotBenThang() {
        // Cả hai API khoá cùng một dòng orders: bên đến sau thấy trạng thái bên kia vừa ghi và nhận 409.
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);
        long order = api.buy(user, product, 2);
        api.put("/api/admin/orders/" + order + "/status", Map.of("status", "CONFIRMED"), admin).expect(200);

        List<Integer> statuses = Concurrently.run(2, i -> i == 0
                ? api.put("/api/admin/orders/" + order + "/status", Map.of("status", "SHIPPING"), admin).status()
                : api.put("/api/orders/" + order + "/cancel", Map.of("cancelledReason", "Đổi ý"), user).status());

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String finalStatus = api.get("/api/orders/" + order, user).body().get("status").asString();
        // Kho chỉ được hoàn khi phía huỷ thắng.
        if (finalStatus.equals("CANCELLED")) {
            assertThat(api.stockOf(product)).isEqualTo(5);
        } else {
            assertThat(finalStatus).isEqualTo("SHIPPING");
            assertThat(api.stockOf(product)).isEqualTo(3);
        }
    }
}
