package com.duynh.shopee.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi;

/**
 * Bật profile demo trên một DB trống: Flyway chạy schema (1) rồi dữ liệu mẫu (1.1), và dữ liệu đó dùng được thật.
 * Đổi profile nên lớp này có context riêng, container riêng — dữ liệu mẫu không lẫn với dữ liệu của test khác.
 */
@ActiveProfiles("demo")
class SeedMigrationIT extends IntegrationTest {

    @Test
    void chayDuSchemaVaDuLieuMau() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class)).containsExactly("1", "1.1");
    }

    @Test
    void soDongDungNhuDuLieuMau() {
        Map<String, Integer> expected = Map.of("users", 4, "categories", 12, "products", 69, "product_images", 207,
                "orders", 8, "order_items", 13, "cart_items", 5);
        expected.forEach((table, rows) -> assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class)).as(table).isEqualTo(rows));
    }

    @Test
    void taiKhoanMauDangNhapDuoc_vaAdminCoQuyenAdmin() {
        api.login("an.nguyen@gmail.com", TestApi.PASSWORD);
        TestApi.Session admin = api.login("admin@shopeeclone.vn", TestApi.PASSWORD);
        assertThat(api.get("/api/admin/users", admin).expect(200).body().get("totalItems").asInt()).isEqualTo(4);
    }

    @Test
    void anhMauDuocPhucVuTuJar() {
        String url = api.get("/api/products?limit=1", null).expect(200).body().get("items").get(0).get("image").asString();
        assertThat(url).startsWith("http://localhost:8081/images/");
        // URL trong DB mang địa chỉ cấu hình (cổng 8081); test chạy ở cổng ngẫu nhiên nên chỉ lấy phần đường dẫn.
        assertThat(api.statusOf(url.substring(url.indexOf("/images/")))).isEqualTo(200);
    }
}
