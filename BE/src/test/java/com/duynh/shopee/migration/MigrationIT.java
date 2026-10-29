package com.duynh.shopee.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.duynh.shopee.support.IntegrationTest;

/**
 * Không bật profile demo: Flyway chỉ dựng schema, không nạp dữ liệu mẫu — đúng như môi trường thật.
 * App khởi động được nghĩa là Hibernate `validate` thấy 8 entity khớp schema V1.
 */
class MigrationIT extends IntegrationTest {

    @Test
    void khongBatProfileDemo_chiChayMigrationSchema() {
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank",
                String.class)).containsExactly("1");
    }

    @Test
    void khongBatProfileDemo_khongCoTaiKhoanMau() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'an.nguyen@gmail.com'", Integer.class))
                .isZero();
    }
}
