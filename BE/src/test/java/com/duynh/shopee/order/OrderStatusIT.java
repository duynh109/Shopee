package com.duynh.shopee.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi.Response;
import com.duynh.shopee.support.TestApi.Session;

/** Đổi trạng thái đơn qua API admin và khách huỷ đơn (API_SPEC §8.4, §9.1). Bảng 25 cặp ở OrderStatusTest. */
class OrderStatusIT extends IntegrationTest {

    private Response setStatus(Session admin, long order, String status) {
        return api.put("/api/admin/orders/" + order + "/status", Map.of("status", status), admin);
    }

    @Test
    void donCodDiHetVongDoi_giaoXongThanhDaThanhToan() {
        Session admin = api.admin();
        Session user = api.register();
        long order = api.buy(user, api.createProduct(admin, 5), 1);
        assertThat(api.get("/api/orders/" + order, user).body().get("paymentStatus").asString()).isEqualTo("UNPAID");

        setStatus(admin, order, "CONFIRMED").expect(200);
        setStatus(admin, order, "SHIPPING").expect(200);
        Response delivered = setStatus(admin, order, "DELIVERED").expect(200);

        assertThat(delivered.body().get("status").asString()).isEqualTo("DELIVERED");
        assertThat(delivered.body().get("paymentStatus").asString()).isEqualTo("PAID");
    }

    @Test
    void chuyenSai_409Conflict_trangThaiGiuNguyen() {
        Session admin = api.admin();
        Session user = api.register();
        long order = api.buy(user, api.createProduct(admin, 5), 1);

        Response r = setStatus(admin, order, "DELIVERED");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.type()).isEqualTo("/errors/conflict");
        assertThat(api.get("/api/orders/" + order, user).body().get("status").asString()).isEqualTo("PENDING");
    }

    @Test
    void adminHuyDonDaXacNhan_hoanKho() {
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);
        long order = api.buy(user, product, 2);
        setStatus(admin, order, "CONFIRMED").expect(200);

        api.put("/api/admin/orders/" + order + "/status",
                Map.of("status", "CANCELLED", "cancelledReason", "Hết hàng"), admin).expect(200);

        assertThat(api.stockOf(product)).isEqualTo(5);
        assertThat(api.soldOf(product)).isZero();
    }

    @Test
    void adminHuyMaKhongCoLyDo_422() {
        Session admin = api.admin();
        long order = api.buy(api.register(), api.createProduct(admin, 5), 1);

        Response r = setStatus(admin, order, "CANCELLED");
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body().get("errors").has("cancelledReason")).isTrue();
    }

    @Test
    void khachHuyDonDangGiao_409_khoKhongDoi() {
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);
        long order = api.buy(user, product, 1);
        setStatus(admin, order, "CONFIRMED").expect(200);
        setStatus(admin, order, "SHIPPING").expect(200);

        Response r = api.put("/api/orders/" + order + "/cancel", Map.of("cancelledReason", "Đổi ý"), user);
        assertThat(r.status()).isEqualTo(409);
        assertThat(api.stockOf(product)).isEqualTo(4);
    }

    @Test
    void datHang_truKhoTangDaBan_xoaDongGio() {
        Session admin = api.admin();
        Session user = api.register();
        long product = api.createProduct(admin, 5);

        api.buy(user, product, 2);

        assertThat(api.stockOf(product)).isEqualTo(3);
        assertThat(api.soldOf(product)).isEqualTo(2);
        assertThat(api.get("/api/cart", user).body().size()).isZero();
    }
}
