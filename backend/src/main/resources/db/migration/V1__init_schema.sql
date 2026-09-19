-- V1__init_schema.sql
--
-- Sinh trực tiếp từ dac-ta-yeu-cau.md Mục 3 (Từ điển dữ liệu) — 17 bảng, khớp tên bảng/cột/kiểu/
-- ràng buộc đúng như tài liệu đã chốt, đúng yêu cầu TC2.1 Mức 5 ("khớp 100% giữa đặc tả và mã
-- nguồn khi hội đồng đối chiếu ngẫu nhiên"). Thứ tự CREATE TABLE đi theo phụ thuộc khóa ngoại
-- (bảng cha trước, bảng con sau) — MySQL đòi bảng được tham chiếu phải tồn tại trước.
--
-- 1 chỗ lệch tên so với đặc tả, có chủ đích: bảng "order" đổi thành "orders" vì ORDER là từ khóa
-- dành riêng của SQL (dùng trong ORDER BY) — dùng "order" làm tên bảng vẫn được nhưng phải escape
-- bằng backtick ở MỌI câu lệnh sau này, rất dễ quên/dễ lỗi. Đây là quyết định tầng triển khai (SQL
-- DDL), không phải đổi nghiệp vụ — ghi lại ở ho-so-du-an.md Mục 5.
--
-- Khóa ngoại không khai ON DELETE CASCADE/SET NULL ở đâu (mặc định MySQL = RESTRICT, chặn xóa nếu
-- còn dòng con phụ thuộc) — nhất quán với cách đặc tả đã chọn xuyên suốt: không hard-delete
-- (product/account dùng soft-delete qua deleted_at), nên CSDL nên CHẶN hard-delete gây mất dữ liệu
-- audit, thay vì âm thầm xóa lan (CASCADE) hoặc gỡ liên kết (SET NULL).

ALTER DATABASE fashionshop CHARACTER SET utf8mb4;

-- ============================================================
-- Nhóm bảng không phụ thuộc bảng nào khác
-- ============================================================

-- 3.1 Tài khoản
CREATE TABLE account (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255) NOT NULL,
    gender        ENUM('MALE','FEMALE','OTHER') NULL,
    birthdate     DATE NULL,
    role          ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT uk_account_email UNIQUE (email)
) ENGINE=InnoDB;

-- 3.3 Danh mục & thương hiệu
CREATE TABLE category (
    id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE brand (
    id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    CONSTRAINT uk_brand_name UNIQUE (name)
) ENGINE=InnoDB;

-- 3.10 Phân nhóm khách hàng — bảng định nghĩa nhóm (chưa phụ thuộc account)
CREATE TABLE customer_segment (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    computed_at DATETIME NOT NULL
) ENGINE=InnoDB;

-- 3.11 Mã giảm giá
CREATE TABLE voucher (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code                     VARCHAR(50) NOT NULL,
    discount_type            ENUM('PERCENT','FIXED') NOT NULL,
    discount_value           BIGINT UNSIGNED NOT NULL,
    min_order_amount         BIGINT UNSIGNED NULL,
    valid_from               DATETIME NOT NULL,
    valid_until              DATETIME NOT NULL,
    usage_limit_total        INT UNSIGNED NULL,
    usage_limit_per_account  INT UNSIGNED NULL,
    used_count               INT UNSIGNED NOT NULL DEFAULT 0,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               DATETIME NOT NULL,
    CONSTRAINT uk_voucher_code UNIQUE (code)
) ENGINE=InnoDB;

-- ============================================================
-- Nhóm bảng phụ thuộc account / category / brand
-- ============================================================

-- 3.2 Địa chỉ giao hàng ("sổ địa chỉ" — khác snapshot lưu trong orders, xem Mục 3.6)
CREATE TABLE address (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id     BIGINT UNSIGNED NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    phone          VARCHAR(20) NOT NULL,
    address_detail VARCHAR(500) NOT NULL,
    is_default     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     DATETIME NOT NULL,
    CONSTRAINT fk_address_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;

-- 3.4 Sản phẩm
CREATE TABLE product (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT UNSIGNED NOT NULL,
    brand_id    BIGINT UNSIGNED NULL,
    name        VARCHAR(255) NOT NULL,
    description TEXT NULL,
    deleted_at  DATETIME NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brand(id)
) ENGINE=InnoDB;

CREATE TABLE product_image (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id    BIGINT UNSIGNED NOT NULL,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT UNSIGNED NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB;

CREATE TABLE product_variant (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT UNSIGNED NOT NULL,
    size       VARCHAR(20) NOT NULL,
    color      VARCHAR(50) NOT NULL,
    price      BIGINT UNSIGNED NOT NULL,
    stock      INT UNSIGNED NOT NULL DEFAULT 0,
    sku_code   VARCHAR(50) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT uk_product_variant_sku_code UNIQUE (sku_code)
) ENGINE=InnoDB;

-- 3.5 Giỏ hàng
CREATE TABLE cart_item (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    variant_id BIGINT UNSIGNED NOT NULL,
    quantity   INT UNSIGNED NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_cart_item_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_cart_item_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id),
    CONSTRAINT uk_cart_item_account_variant UNIQUE (account_id, variant_id)
) ENGINE=InnoDB;

-- 3.10 account_segment (phụ thuộc account + customer_segment)
CREATE TABLE account_segment (
    account_id    BIGINT UNSIGNED PRIMARY KEY,
    segment_id    BIGINT UNSIGNED NOT NULL,
    rfm_recency   INT UNSIGNED NOT NULL,
    rfm_frequency INT UNSIGNED NOT NULL,
    rfm_monetary  BIGINT UNSIGNED NOT NULL,
    computed_at   DATETIME NOT NULL,
    CONSTRAINT fk_account_segment_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_account_segment_segment FOREIGN KEY (segment_id) REFERENCES customer_segment(id)
) ENGINE=InnoDB;

-- ============================================================
-- Đơn hàng — phụ thuộc account, address, voucher, product_variant
-- ============================================================

-- 3.6 "order" -> "orders" (từ khóa dành riêng của SQL, xem ghi chú đầu file)
CREATE TABLE orders (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id               BIGINT UNSIGNED NOT NULL,
    status                   ENUM('PENDING','PROCESSING','SHIPPING','DELIVERED','CANCELLED') NOT NULL DEFAULT 'PENDING',
    payment_method           ENUM('ONLINE','COD') NOT NULL,
    payment_status           ENUM('PENDING','PAID','FAILED') NULL,
    idempotency_key          VARCHAR(100) NOT NULL,
    address_id               BIGINT UNSIGNED NULL,
    recipient_name_snapshot  VARCHAR(255) NOT NULL,
    phone_snapshot           VARCHAR(20) NOT NULL,
    address_detail_snapshot  VARCHAR(500) NOT NULL,
    voucher_id               BIGINT UNSIGNED NULL,
    discount_amount          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    subtotal_amount          BIGINT UNSIGNED NOT NULL,
    shipping_fee             BIGINT UNSIGNED NOT NULL DEFAULT 0,
    total_amount              BIGINT UNSIGNED NOT NULL,
    created_at                DATETIME NOT NULL,
    updated_at                DATETIME NOT NULL,
    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_orders_address FOREIGN KEY (address_id) REFERENCES address(id),
    CONSTRAINT fk_orders_voucher FOREIGN KEY (voucher_id) REFERENCES voucher(id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB;

CREATE TABLE order_item (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id               BIGINT UNSIGNED NOT NULL,
    variant_id             BIGINT UNSIGNED NOT NULL,
    product_name_snapshot  VARCHAR(255) NOT NULL,
    size_snapshot          VARCHAR(20) NOT NULL,
    color_snapshot         VARCHAR(50) NOT NULL,
    unit_price_snapshot    BIGINT UNSIGNED NOT NULL,
    quantity               INT UNSIGNED NOT NULL,
    line_total             BIGINT UNSIGNED NOT NULL,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_item_variant FOREIGN KEY (variant_id) REFERENCES product_variant(id)
) ENGINE=InnoDB;

CREATE TABLE refund (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id        BIGINT UNSIGNED NOT NULL,
    status          ENUM('PROCESSING','COMPLETED','FAILED') NOT NULL,
    amount          BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    retry_count     INT UNSIGNED NOT NULL DEFAULT 0,
    initiated_at    DATETIME NOT NULL,
    completed_at    DATETIME NULL,
    failure_reason  VARCHAR(500) NULL,
    CONSTRAINT fk_refund_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT uk_refund_order_id UNIQUE (order_id),
    CONSTRAINT uk_refund_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB;

-- ============================================================
-- Nhóm bảng còn lại — phụ thuộc account / product / order_item
-- ============================================================

-- 3.7 Nhật ký xem sản phẩm
CREATE TABLE product_view_log (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    viewed_at  DATETIME NOT NULL,
    CONSTRAINT fk_product_view_log_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_product_view_log_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB;

-- Mục 3.7 yêu cầu rõ: index cho truy vấn ngưỡng "≥5 sản phẩm/30 ngày" (BR-19)
CREATE INDEX idx_product_view_log_account_viewed ON product_view_log (account_id, viewed_at);

-- 3.8 Đánh giá
CREATE TABLE review (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT UNSIGNED NOT NULL,
    product_id    BIGINT UNSIGNED NOT NULL,
    order_item_id BIGINT UNSIGNED NOT NULL,
    rating        TINYINT UNSIGNED NOT NULL,
    comment       TEXT NULL,
    created_at    DATETIME NOT NULL,
    updated_at    DATETIME NOT NULL,
    CONSTRAINT fk_review_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT fk_review_order_item FOREIGN KEY (order_item_id) REFERENCES order_item(id),
    CONSTRAINT uk_review_account_product UNIQUE (account_id, product_id)
) ENGINE=InnoDB;

-- 3.9 Danh sách yêu thích
CREATE TABLE wishlist_item (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_wishlist_item_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT uk_wishlist_item_account_product UNIQUE (account_id, product_id)
) ENGINE=InnoDB;
