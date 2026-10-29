-- Schema v1: tám bảng như lúc chuyển sang Flyway. Lý do của từng cột, từng ràng buộc ở docs/DATABASE_DESIGN.md mục 3.
--
-- Đã chạy rồi thì KHÔNG sửa file này: Flyway lưu checksum của nó và sẽ từ chối khởi động nếu nội dung đổi.
-- Đổi schema thì thêm file mới V2__..., V3__...
--
-- Tên ràng buộc và index đặt tường minh, trùng với SQL trong DATABASE_DESIGN.md, để migration sau này nhắc tới
-- được bằng tên (ví dụ ALTER TABLE ... DROP FOREIGN KEY fk_cart_product).
-- Thứ tự bảng theo khoá ngoại: bảng được trỏ tới tạo trước.

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    address       VARCHAR(255) DEFAULT NULL,
    avatar        VARCHAR(255) DEFAULT NULL,
    created_at    DATETIME(6)  NOT NULL,
    date_of_birth DATE         DEFAULT NULL,
    email         VARCHAR(255) NOT NULL,
    name          VARCHAR(255) DEFAULT NULL,
    password      VARCHAR(100) NOT NULL,
    phone         VARCHAR(20)  DEFAULT NULL,
    role          VARCHAR(20)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6)  NOT NULL,
    name       VARCHAR(255) NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    name                  VARCHAR(255) NOT NULL,
    price                 BIGINT       NOT NULL,
    category_id           BIGINT       NOT NULL,
    created_at            DATETIME(6)  NOT NULL,
    deleted_at            DATETIME(6)  DEFAULT NULL,
    description           TEXT,
    image                 VARCHAR(255) DEFAULT NULL,
    price_before_discount BIGINT       DEFAULT NULL,
    quantity              INT          NOT NULL,
    rating                DECIMAL(2,1) NOT NULL,
    sold                  INT          NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    view                  INT          NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_product_category (category_id),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Khoá ngoại này không có ON DELETE CASCADE (DATABASE_DESIGN.md §3.4): sản phẩm chỉ xoá mềm nên không cần.
CREATE TABLE product_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    sort_order INT          NOT NULL,
    url        VARCHAR(255) NOT NULL,
    product_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_image_product (product_id),
    CONSTRAINT fk_image_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Khoá ngoại user_id dùng chung index của uq_cart_item (user_id đứng đầu), nên không có index riêng cho nó.
CREATE TABLE cart_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    quantity   INT         NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    product_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_cart_item UNIQUE (user_id, product_id),
    INDEX idx_cart_product (product_id),
    CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Khoá ngoại user_id dùng chung index idx_order_user_status. Ba CHECK khoá danh sách giá trị của enum
-- (DATABASE_DESIGN.md §3.6): thêm giá trị mới thì phải viết migration DROP CHECK rồi ADD CHECK.
CREATE TABLE orders (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    cancelled_reason VARCHAR(255) DEFAULT NULL,
    created_at       DATETIME(6)  NOT NULL,
    discount         BIGINT       NOT NULL,
    note             VARCHAR(500) DEFAULT NULL,
    order_code       VARCHAR(32)  NOT NULL,
    payment_method   VARCHAR(20)  NOT NULL,
    payment_status   VARCHAR(20)  NOT NULL,
    recipient_name   VARCHAR(255) NOT NULL,
    recipient_phone  VARCHAR(20)  NOT NULL,
    shipping_address VARCHAR(255) NOT NULL,
    shipping_fee     BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    subtotal         BIGINT       NOT NULL,
    total_amount     BIGINT       NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    user_id          BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_order_code UNIQUE (order_code),
    INDEX idx_order_user_status (user_id, status),
    INDEX idx_order_created (created_at),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_order_payment_method CHECK (payment_method IN ('COD', 'BANK_TRANSFER')),
    CONSTRAINT ck_order_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED')),
    CONSTRAINT ck_order_status CHECK (status IN ('PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    created_at            DATETIME(6)  NOT NULL,
    price                 BIGINT       NOT NULL,
    price_before_discount BIGINT       DEFAULT NULL,
    product_image         VARCHAR(255) DEFAULT NULL,
    product_name          VARCHAR(255) NOT NULL,
    quantity              INT          NOT NULL,
    order_id              BIGINT       NOT NULL,
    product_id            BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_item_order (order_id),
    INDEX idx_item_product (product_id),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) DEFAULT NULL,
    token_hash VARCHAR(64) NOT NULL,
    user_id    BIGINT      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_user (user_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
