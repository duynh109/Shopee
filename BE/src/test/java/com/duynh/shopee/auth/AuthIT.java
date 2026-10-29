package com.duynh.shopee.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.Concurrently;
import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi;
import com.duynh.shopee.support.TestApi.Response;

/** Đăng ký, đăng nhập (API_SPEC §3.1, §3.2). */
class AuthIT extends IntegrationTest {

    @Test
    void dangKy_traCapTokenVaUserRoleUser_dungDuocNgay() {
        String email = TestApi.newEmail();
        Response r = api.post("/api/auth/register", Map.of("email", email, "password", TestApi.PASSWORD), null)
                .expect(201);

        assertThat(r.body().get("expires").asInt()).isEqualTo(900);
        assertThat(r.body().get("refreshToken").asString()).hasSize(43);
        assertThat(r.body().get("user").get("email").asString()).isEqualTo(email);
        assertThat(r.body().get("user").get("roles").get(0).asString()).isEqualTo("USER");
        assertThat(r.body().get("user").has("password")).isFalse();

        TestApi.Session me = new TestApi.Session(email, r.body().get("accessToken").asString(), null);
        assertThat(api.get("/api/users/me", me).expect(200).body().get("email").asString()).isEqualTo(email);
    }

    @Test
    void dangNhapSaiMatKhau_401InvalidCredentials() {
        TestApi.Session user = api.register();
        Response r = api.post("/api/auth/login", Map.of("email", user.email(), "password", "sai-mat-khau"), null);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.type()).isEqualTo("/errors/invalid-credentials");
    }

    @Test
    void dangNhapEmailKhongTonTai_cungLoiNhuSaiMatKhau() {
        // Không cho người dò biết email nào đã đăng ký: cùng status, cùng type với sai mật khẩu.
        Response r = api.post("/api/auth/login", Map.of("email", TestApi.newEmail(), "password", "123456"), null);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.type()).isEqualTo("/errors/invalid-credentials");
    }

    @Test
    void dangKyTrungEmail_422LoiOFieldEmail() {
        TestApi.Session user = api.register();
        Response r = api.post("/api/auth/register", Map.of("email", user.email(), "password", "123456"), null);

        assertThat(r.status()).isEqualTo(422);
        assertThat(r.body().get("errors").get("email").asString()).isEqualTo("Email đã tồn tại");
    }

    @Test
    void dangKyDuLieuSai_422KemLoiTungField() {
        Response r = api.post("/api/auth/register", Map.of("email", "khong-phai-email", "password", "123"), null);

        assertThat(r.status()).isEqualTo(422);
        assertThat(r.type()).isEqualTo("/errors/validation");
        assertThat(r.body().get("errors").get("email").asString()).isEqualTo("Email không hợp lệ");
        assertThat(r.body().get("errors").get("password").asString()).isEqualTo("Độ dài tối thiểu 6 kí tự");
    }

    @Test
    void haiRequestDangKyCungEmailCungLuc_mot201Mot409() {
        // Cả hai cùng qua bước "email có chưa?" vì request kia chưa ghi; ràng buộc UNIQUE ở DB chặn request đến sau,
        // và request đó phải nhận 409 chứ không phải 500 (API_SPEC §1.6).
        String email = TestApi.newEmail();
        List<Integer> statuses = Concurrently.run(2, i -> api.post("/api/auth/register",
                Map.of("email", email, "password", TestApi.PASSWORD), null).status());

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }
}
