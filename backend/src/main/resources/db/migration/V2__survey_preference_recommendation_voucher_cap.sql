-- V2__survey_preference_recommendation_voucher_cap.sql
--
-- Bổ sung lược đồ theo các thay đổi đặc tả sau khi V1 đã chạy (không sửa V1: Flyway kiểm tra
-- checksum, sửa file đã chạy sẽ làm mọi máy đã migrate báo lỗi). Khớp với file tiểu luận bản
-- 14/10/2026: Bảng 2.35 (19 lớp), sơ đồ lớp Hình 2.10, và docs/dac-ta-yeu-cau.md Mục 3.12–3.13.
--
--   1. user_preference  — US-23 "Khảo sát sở thích lần đầu", BR-37, BR-38, QĐ_KH10
--   2. recommendation   — kết quả batch gợi ý hằng ngày, trang chủ chỉ đọc bảng này (BR-20)
--   3. voucher.max_discount_amount — trần giảm cho mã phần trăm (rà soát 14/10, mục 3.4)
--
-- 1 chỗ lệch tên so với đặc tả, có chủ đích (giống orders ở V1): thuộc tính rank của lớp
-- Recommendation lưu ở cột rank_position, vì RANK là từ khóa dành riêng của MySQL 8 (hàm cửa sổ
-- RANK()), dùng làm tên cột thì phải escape bằng backtick ở mọi câu lệnh.

-- ============================================================
-- 3.12 Sở thích khảo sát — user_preference
-- ============================================================
-- Một dòng cho mỗi danh mục người dùng chọn (1 đến 3 danh mục, QĐ_KH10), đúng quan hệ
-- Category 1 – 0..* UserPreference trên sơ đồ lớp. Khoảng giá và thương hiệu chọn một lần
-- trong khảo sát nên các dòng của cùng tài khoản mang cùng giá trị. Làm lại khảo sát (BR-37)
-- = xóa các dòng cũ của tài khoản rồi ghi dòng mới trong cùng transaction.
CREATE TABLE user_preference (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    brand_id    BIGINT UNSIGNED NULL,
    price_min   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    price_max   BIGINT UNSIGNED NULL,
    created_at  DATETIME NOT NULL,
    updated_at  DATETIME NOT NULL,
    CONSTRAINT fk_user_preference_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_user_preference_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_user_preference_brand FOREIGN KEY (brand_id) REFERENCES brand(id),
    CONSTRAINT uk_user_preference_account_category UNIQUE (account_id, category_id),
    CONSTRAINT ck_user_preference_price_range CHECK (price_max IS NULL OR price_max >= price_min)
) ENGINE=InnoDB;

-- ============================================================
-- 3.13 Gợi ý tính sẵn — recommendation
-- ============================================================
-- Batch hằng ngày ghi đè toàn bộ danh sách của từng tài khoản (xóa rồi ghi lại trong cùng
-- transaction), không lưu lịch sử. Trang chủ đọc theo (account_id, rank_position).
CREATE TABLE recommendation (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT UNSIGNED NOT NULL,
    product_id    BIGINT UNSIGNED NOT NULL,
    rank_position INT UNSIGNED NOT NULL,
    score         DOUBLE NOT NULL,
    reason        VARCHAR(255) NOT NULL,
    generated_at  DATETIME NOT NULL,
    CONSTRAINT fk_recommendation_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_recommendation_product FOREIGN KEY (product_id) REFERENCES product(id),
    CONSTRAINT uk_recommendation_account_rank UNIQUE (account_id, rank_position),
    CONSTRAINT uk_recommendation_account_product UNIQUE (account_id, product_id)
) ENGINE=InnoDB;

-- ============================================================
-- 3.11 Mã giảm giá — thêm trần giảm
-- ============================================================
-- Chỉ dùng cho mã PERCENT; NULL = không giới hạn. Mã FIXED để NULL.
ALTER TABLE voucher
    ADD COLUMN max_discount_amount BIGINT UNSIGNED NULL AFTER usage_limit_per_account;
