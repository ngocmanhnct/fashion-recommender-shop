-- V9__ab_experiment.sql
--
-- Thử nghiệm A/B cho gợi ý ở trang chủ (US-27, BR-46): khách đủ điều kiện được chia ngẫu nhiên
-- nhưng cố định vào nhóm TREATMENT (thấy gợi ý của ml-service như bình thường) hoặc CONTROL (thấy
-- sản phẩm bán chạy), rồi so sánh tỷ lệ bấm và tỷ lệ đặt hàng của 2 nhóm. Không sửa V1-V8 vì Flyway
-- kiểm tra checksum.

-- 1. Mỗi lần thử nghiệm. Tại một thời điểm có nhiều nhất 1 thử nghiệm đang chạy (ended_at NULL),
--    service kiểm tra trước khi tạo.
CREATE TABLE ab_experiment (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    treatment_percent INT          NOT NULL,
    started_at        DATETIME     NOT NULL,
    ended_at          DATETIME     NULL,
    CONSTRAINT ck_ab_experiment_percent CHECK (treatment_percent BETWEEN 10 AND 90),
    KEY idx_ab_experiment_started (started_at)
) ENGINE=InnoDB;

-- 2. Nhóm của từng khách trong một thử nghiệm, ghi lần đầu khách mở trang chủ khi thử nghiệm đang
--    chạy. Kết quả chỉ tính hành động từ assigned_at trở đi.
CREATE TABLE ab_assignment (
    experiment_id BIGINT UNSIGNED              NOT NULL,
    account_id    BIGINT UNSIGNED              NOT NULL,
    variant       ENUM('TREATMENT', 'CONTROL') NOT NULL,
    assigned_at   DATETIME                     NOT NULL,
    PRIMARY KEY (experiment_id, account_id),
    KEY idx_ab_assignment_account (account_id),
    CONSTRAINT fk_ab_assignment_experiment FOREIGN KEY (experiment_id) REFERENCES ab_experiment(id),
    CONSTRAINT fk_ab_assignment_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;
