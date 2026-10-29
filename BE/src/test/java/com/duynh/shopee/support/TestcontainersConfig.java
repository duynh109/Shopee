package com.duynh.shopee.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * MySQL cho test: một container Docker chạy đúng bản docker-compose.yml dùng (mysql:8.4).
 * - Khai là bean nên Spring tự khởi động container trước khi dựng DataSource, và tắt nó khi xong.
 * - @ServiceConnection: Spring lấy URL, user, mật khẩu của container để nối DataSource, thay cho
 *   spring.datasource.* trong application.properties. Flyway chạy migration lên DB trống này như với DB thật.
 * - Spring giữ lại context (kèm container) cho mọi lớp test cùng cấu hình, nên cả bộ test chỉ khởi động container
 *   một lần. Lớp test đổi cấu hình (ví dụ bật profile demo) thì có context riêng, container riêng, DB riêng.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    MySQLContainer mysql() {
        return new MySQLContainer("mysql:8.4");
    }
}
