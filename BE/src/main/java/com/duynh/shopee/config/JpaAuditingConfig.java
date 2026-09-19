package com.duynh.shopee.config;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Bật cơ chế auditing của Spring Data JPA cho toàn ứng dụng: các field đánh dấu
 * {@code @CreatedDate} / {@code @LastModifiedDate} sẽ được điền tự động trước khi
 * INSERT/UPDATE.
 *
 * Đặt ở class riêng thay vì trên {@code ShopeeApplication} để các slice test
 * (ví dụ {@code @WebMvcTest}) không bị kéo theo phần cấu hình JPA mà chúng không cần.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Cắt thời gian về mốc mili-giây trước khi ghi.
     *
     * Vì sao cần: {@code Instant.now()} trên Linux cho độ chính xác tới nano-giây, còn cột
     * {@code DATETIME(6)} của MySQL chỉ lưu được tới micro-giây. Không cắt thì object vừa lưu
     * (đang nằm trong bộ nhớ, giữ nguyên nano-giây) và bản ghi đọc lại từ DB lệch nhau ở các
     * chữ số cuối — cùng một user mà response của /register và của /users/me báo hai giá trị
     * createdAt khác nhau.
     *
     * Chọn mili-giây thay vì micro-giây cho khớp định dạng ở API_SPEC §1.8
     * ("2026-09-16T08:30:00.000Z"); với dấu thời gian kiểm toán thì mili-giây là quá đủ.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
