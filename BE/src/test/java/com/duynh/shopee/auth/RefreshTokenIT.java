package com.duynh.shopee.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.Concurrently;
import com.duynh.shopee.support.IntegrationTest;
import com.duynh.shopee.support.TestApi;
import com.duynh.shopee.support.TestApi.Response;

/** Refresh token: xoay vòng, phát hiện token bị lộ, thu hồi (API_SPEC §3.3, §3.4, §4.2). */
class RefreshTokenIT extends IntegrationTest {

    private Response refresh(String refreshToken) {
        return api.post("/api/auth/refresh-token", Map.of("refreshToken", refreshToken), null);
    }

    @Test
    void doiToken_nhanCapMoi_accessTokenMoiDungDuoc() {
        TestApi.Session user = api.register();
        Response r = refresh(user.refreshToken()).expect(200);

        String newRefresh = r.body().get("refreshToken").asString();
        assertThat(newRefresh).isNotEqualTo(user.refreshToken());
        TestApi.Session renewed = new TestApi.Session(user.email(), r.body().get("accessToken").asString(), newRefresh);
        api.get("/api/users/me", renewed).expect(200);
    }

    @Test
    void dungLaiTokenDaDoi_401_vaMoiPhienCuaUserBiThuHoi() {
        TestApi.Session user = api.register();
        TestApi.Session otherDevice = api.login(user.email(), TestApi.PASSWORD);
        String t2 = refresh(user.refreshToken()).expect(200).body().get("refreshToken").asString();

        // Token đã đổi rồi mà còn được gửi lên: có hai người cùng cầm nó.
        Response reuse = refresh(user.refreshToken());
        assertThat(reuse.status()).isEqualTo(401);
        assertThat(reuse.type()).isEqualTo("/errors/refresh-token-invalid");

        // Nên mọi phiên của user đó đều chết: cả token vừa nhận lẫn token của thiết bị kia.
        assertThat(refresh(t2).status()).isEqualTo(401);
        assertThat(refresh(otherDevice.refreshToken()).status()).isEqualTo(401);
        // Chủ thật đăng nhập lại bằng mật khẩu thì bình thường.
        api.login(user.email(), TestApi.PASSWORD);
    }

    @Test
    void tokenKhongTonTai_401CungTypeVoiTokenBiThuHoi() {
        Response r = refresh("token-nay-chua-tung-duoc-phat-ra-0000000000");

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.type()).isEqualTo("/errors/refresh-token-invalid");
    }

    @Test
    void dangXuat_thuHoiRefreshToken_khongCanAccessToken() {
        TestApi.Session user = api.register();
        api.post("/api/auth/logout", Map.of("refreshToken", user.refreshToken()), null).expect(204);

        assertThat(refresh(user.refreshToken()).status()).isEqualTo(401);
    }

    @Test
    void doiMatKhau_thuHoiMoiRefreshToken_matKhauMoiDangNhapDuoc() {
        TestApi.Session user = api.register();
        TestApi.Session otherDevice = api.login(user.email(), TestApi.PASSWORD);

        api.put("/api/users/me", Map.of("password", TestApi.PASSWORD, "newPassword", "mat-khau-moi"), user).expect(200);

        assertThat(refresh(user.refreshToken()).status()).isEqualTo(401);
        assertThat(refresh(otherDevice.refreshToken()).status()).isEqualTo(401);
        api.login(user.email(), "mat-khau-moi");
    }

    @Test
    void namRequestDoiCungMotTokenCungLuc_dungMotRequestThanhCong() {
        // Khoá dòng (@Lock) trên câu tìm token bắt các request xếp hàng: request đầu đổi được, các request sau thấy
        // token đã thu hồi. Không khoá thì nhiều request cùng đổi được một token (API_SPEC §3.4).
        TestApi.Session user = api.register();
        List<Integer> statuses = Concurrently.run(5, i -> refresh(user.refreshToken()).status());

        assertThat(Collections.frequency(statuses, 200)).isEqualTo(1);
        assertThat(Collections.frequency(statuses, 401)).isEqualTo(4);
    }
}
