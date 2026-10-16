-- V3__variation_tiers_event_log_recompute_request.sql
--
-- Bổ sung lược đồ theo file tiểu luận bản 15/10/2026 (BR-05, BR-15, BR-20, BR-36; Bảng 2.35
-- dòng 5, 7, 10, 20, 21; sơ đồ lớp Hình 2.10) và docs/dac-ta-yeu-cau.md Mục 3.4, 3.6, 3.14, 3.15.
-- Không sửa V1, V2 vì Flyway kiểm tra checksum.
--
--   1. Phân loại 2 tầng thay cho size × màu: sản phẩm có tối đa 2 tầng phân loại do quản trị viên
--      đặt tên (product.variation_name1/2), mỗi biến thể là một tổ hợp giá trị
--      (product_variant.option1_value/2). Sản phẩm không có phân loại có đúng 1 biến thể.
--   2. order_item lưu 1 nhãn phân loại (vd "Đen – 42") thay cho size_snapshot, color_snapshot.
--   3. product_event_log — sự kiện thêm vào giỏ, gợi ý được hiển thị, gợi ý được bấm (đo tỷ lệ bấm).
--   4. recompute_request — yêu cầu tính lại gợi ý hoặc gán nhóm đang chờ (BR-20, BR-23).
--
-- Dữ liệu cũ: biến thể size × màu đổi thành 2 tầng "Màu sắc" (giá trị = color) và "Kích cỡ"
-- (giá trị = size), đúng thứ tự ví dụ "Đen – 42" của BR-05, không mất dữ liệu.

-- ============================================================
-- 3.4 Sản phẩm — tên các tầng phân loại
-- ============================================================
-- NULL = không dùng tầng đó. Có tầng 2 thì phải có tầng 1.
ALTER TABLE product
    ADD COLUMN variation_name1 VARCHAR(50) NULL AFTER description,
    ADD COLUMN variation_name2 VARCHAR(50) NULL AFTER variation_name1,
    ADD CONSTRAINT ck_product_variation_names
        CHECK (variation_name2 IS NULL OR variation_name1 IS NOT NULL);

UPDATE product p
SET p.variation_name1 = 'Màu sắc',
    p.variation_name2 = 'Kích cỡ'
WHERE EXISTS (SELECT 1 FROM product_variant v WHERE v.product_id = p.id);

-- ============================================================
-- 3.4 Biến thể — giá trị phân loại thay cho size, color
-- ============================================================
-- Số giá trị khác NULL của biến thể bằng số tầng của sản phẩm (kiểm tra ở tầng service, vì
-- CHECK của MySQL không đọc được bảng khác).
ALTER TABLE product_variant
    ADD COLUMN option1_value VARCHAR(100) NULL AFTER product_id,
    ADD COLUMN option2_value VARCHAR(100) NULL AFTER option1_value;

UPDATE product_variant
SET option1_value = color,
    option2_value = size;

ALTER TABLE product_variant
    DROP COLUMN size,
    DROP COLUMN color,
    ADD CONSTRAINT ck_product_variant_options
        CHECK (option2_value IS NULL OR option1_value IS NOT NULL);

-- Mỗi tổ hợp giá trị chỉ có 1 biến thể trong một sản phẩm. UNIQUE coi các NULL là khác nhau, nên
-- so trên COALESCE(..., ''): nhờ vậy sản phẩm không có phân loại (cả 2 giá trị NULL) cũng chỉ có
-- được 1 biến thể. Collation mặc định không phân biệt hoa thường và dấu: "Đen" trùng "den".
CREATE UNIQUE INDEX uk_product_variant_options ON product_variant (
    product_id,
    (COALESCE(option1_value, '')),
    (COALESCE(option2_value, ''))
);

-- ============================================================
-- 3.6 Dòng đơn hàng — nhãn phân loại
-- ============================================================
-- NULL = sản phẩm không có phân loại. Chưa có đơn hàng nào được tạo qua ứng dụng, câu UPDATE chỉ
-- để giữ dữ liệu nếu máy nào đã có dòng nhập tay.
ALTER TABLE order_item
    ADD COLUMN variant_label_snapshot VARCHAR(255) NULL AFTER product_name_snapshot;

UPDATE order_item
SET variant_label_snapshot = CONCAT(color_snapshot, ' – ', size_snapshot);

ALTER TABLE order_item
    DROP COLUMN size_snapshot,
    DROP COLUMN color_snapshot;

-- ============================================================
-- 3.14 Nhật ký sự kiện gợi ý — product_event_log
-- ============================================================
-- account_id NULL khi người dùng chưa đăng nhập. placement, source, rank_position chỉ có ý nghĩa
-- với sự kiện của gợi ý (RECOMMENDATION_SHOWN, RECOMMENDATION_CLICKED, hoặc ADD_TO_CART bấm từ ô
-- gợi ý). rank_position: thuộc tính rank trên sơ đồ lớp, đổi tên như ở bảng recommendation (V2).
CREATE TABLE product_event_log (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT UNSIGNED NULL,
    product_id    BIGINT UNSIGNED NOT NULL,
    event_type    ENUM('ADD_TO_CART','RECOMMENDATION_SHOWN','RECOMMENDATION_CLICKED') NOT NULL,
    placement     VARCHAR(50) NULL,
    source        VARCHAR(30) NULL,
    rank_position INT UNSIGNED NULL,
    created_at    DATETIME NOT NULL,
    CONSTRAINT fk_product_event_log_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_product_event_log_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB;

-- Tỷ lệ bấm vào gợi ý tính theo loại sự kiện trong một khoảng thời gian.
CREATE INDEX idx_product_event_log_type_created ON product_event_log (event_type, created_at);

-- ============================================================
-- 3.15 Yêu cầu tính lại — recompute_request
-- ============================================================
-- Mỗi tài khoản có tối đa 1 yêu cầu cho mỗi loại việc, nên khóa chính là (account_id, task_type).
-- Backend ghi trong cùng giao dịch với hành động của người dùng bằng
--   INSERT ... AS incoming ON DUPLICATE KEY UPDATE
--     due_at = LEAST(recompute_request.due_at, incoming.due_at),
--     version = recompute_request.version + 1
-- (gộp yêu cầu, không làm lùi thời điểm đến hạn). Dịch vụ học máy lấy các dòng đã đến hạn, tính
-- xong thì chỉ xóa dòng nếu version chưa đổi; tính lỗi thì tăng attempts, ghi last_error và lùi
-- due_at. Mọi thời điểm của bảng này là giờ UTC, ghi bằng UTC_TIMESTAMP(3), không dùng NOW()
-- (NOW() đổi theo múi giờ của từng kết nối).
CREATE TABLE recompute_request (
    account_id BIGINT UNSIGNED NOT NULL,
    task_type  ENUM('RECOMMENDATION','SEGMENT_ASSIGN') NOT NULL,
    due_at     DATETIME(3) NOT NULL,
    version    INT UNSIGNED NOT NULL DEFAULT 1,
    attempts   INT UNSIGNED NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (account_id, task_type),
    CONSTRAINT fk_recompute_request_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;

-- Dịch vụ học máy tìm các yêu cầu đã đến hạn mỗi 2 giây.
CREATE INDEX idx_recompute_request_due_at ON recompute_request (due_at);
