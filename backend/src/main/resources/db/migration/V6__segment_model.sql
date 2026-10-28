-- V6__segment_model.sql
--
-- Lưu mô hình phân nhóm khách hàng (US-19, BR-23; docs/dac-ta-yeu-cau.md Mục 3.10). Không sửa
-- V1-V5 vì Flyway kiểm tra checksum.
--
-- BR-23: đơn chuyển sang Đã giao thì khách được gán ngay vào nhóm có tâm gần nhất, chuẩn hóa
-- R, F, M bằng tham số của lần huấn luyện gần nhất. Vì vậy cần lưu lại tham số chuẩn hóa (bảng
-- segment_model) và tâm của từng nhóm (cột center_* của customer_segment). ml-service ghi cả 2
-- bảng mỗi lần huấn luyện; backend chỉ đọc.
--
-- Không gian đặc trưng: x = ln(1 + giá trị) cho R (số ngày từ đơn Đã giao gần nhất), F (số đơn
-- Đã giao), M (tổng tiền các đơn Đã giao, VNĐ); rồi z = (x - mean) / std. Tâm nhóm lưu ở dạng z.

-- 1. Mỗi lần huấn luyện là 1 dòng (giữ lịch sử để so chất lượng qua các tuần).
CREATE TABLE segment_model (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cluster_count       INT      NOT NULL,
    elbow_cluster_count INT      NOT NULL,
    customer_count      INT      NOT NULL,
    silhouette          DOUBLE   NOT NULL,
    log_recency_mean    DOUBLE   NOT NULL,
    log_recency_std     DOUBLE   NOT NULL,
    log_frequency_mean  DOUBLE   NOT NULL,
    log_frequency_std   DOUBLE   NOT NULL,
    log_monetary_mean   DOUBLE   NOT NULL,
    log_monetary_std    DOUBLE   NOT NULL,
    trained_at          DATETIME NOT NULL
) ENGINE=InnoDB;

-- 2. Nhóm của lần huấn luyện mới nhất (ghi đè mỗi lần, như account_segment). Chưa nơi nào ghi 2
--    bảng này trước V6 nên xóa để thêm được cột NOT NULL có khóa ngoại.
DELETE FROM account_segment;
DELETE FROM customer_segment;

ALTER TABLE customer_segment
    ADD COLUMN model_id         BIGINT UNSIGNED NOT NULL AFTER id,
    ADD COLUMN code             VARCHAR(20)     NOT NULL AFTER model_id,
    ADD COLUMN center_recency   DOUBLE          NOT NULL AFTER description,
    ADD COLUMN center_frequency DOUBLE          NOT NULL AFTER center_recency,
    ADD COLUMN center_monetary  DOUBLE          NOT NULL AFTER center_frequency,
    ADD CONSTRAINT fk_customer_segment_model FOREIGN KEY (model_id) REFERENCES segment_model(id),
    ADD CONSTRAINT uk_customer_segment_code UNIQUE (code);
