package com.duynh.shopee.support;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Lớp cha của mọi test tích hợp: chạy cả app ở một cổng ngẫu nhiên, nối vào MySQL trong container
 * (TestcontainersConfig), rồi gọi API thật qua HTTP bằng {@link TestApi}.
 * - Không cần BE/.env: secret cho test sinh ngay lúc chạy, mỗi lần một khoá JWT mới.
 * - Không đụng DB dev: DataSource trỏ vào container, không vào shopee_db.
 * - Mỗi test tự tạo dữ liệu riêng (email, sản phẩm sinh mới), nên các test không giẫm lên nhau dù dùng chung DB.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
public abstract class IntegrationTest {
    // Tính một lần cho cả lần chạy: supplier của DynamicPropertySource có thể bị gọi nhiều lần, mỗi lần một khoá
    // khác nhau thì token ký bằng khoá này sẽ bị kiểm bằng khoá kia.
    private static final String JWT_SECRET = randomSecret();

    @LocalServerPort
    private int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected TestApi api;

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("JWT_SECRET", () -> JWT_SECRET);
        // application.properties đòi ${DB_PASSWORD}; giá trị thật do container cấp qua @ServiceConnection.
        registry.add("DB_PASSWORD", () -> "khong-dung-toi");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("app.upload.dir", () -> "target/test-uploads");
    }

    @BeforeEach
    void setUpApi() {
        api = new TestApi("http://localhost:" + port, jdbc);
    }

    private static String randomSecret() {
        byte[] bytes = new byte[64];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
