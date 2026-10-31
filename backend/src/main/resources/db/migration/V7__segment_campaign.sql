-- V7__segment_campaign.sql
--
-- Chiến dịch ưu đãi theo nhóm khách hàng (US-26): Quản trị viên tặng mã giảm giá riêng cho cả một
-- nhóm khách hoặc cho khách cần chăm sóc của nhóm (BR-43), rồi đo kết quả (bao nhiêu khách quay
-- lại mua, doanh thu mang về, tiền đã giảm). Không sửa V1-V6 vì Flyway kiểm tra checksum.

-- 1. Mã riêng: chỉ khách có tên trong voucher_recipient dùng được (BR-44). Mã công khai cũ giữ
--    FALSE nên ai cũng dùng được như trước.
ALTER TABLE voucher
    ADD COLUMN recipients_only BOOLEAN NOT NULL DEFAULT FALSE AFTER is_active;

-- 2. Khách được tặng mã riêng.
CREATE TABLE voucher_recipient (
    voucher_id BIGINT UNSIGNED NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (voucher_id, account_id),
    KEY idx_voucher_recipient_account (account_id),
    CONSTRAINT fk_voucher_recipient_voucher FOREIGN KEY (voucher_id) REFERENCES voucher(id),
    CONSTRAINT fk_voucher_recipient_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;

-- 3. Mỗi lần tặng là 1 chiến dịch gắn với đúng 1 mã. Mã và tên nhóm lưu bản sao vì ml-service ghi
--    đè bảng customer_segment mỗi lần huấn luyện lại.
CREATE TABLE segment_campaign (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    segment_code    VARCHAR(20)          NOT NULL,
    segment_name    VARCHAR(100)         NOT NULL,
    target          ENUM('ALL', 'CARE')  NOT NULL,
    voucher_id      BIGINT UNSIGNED      NOT NULL,
    recipient_count INT                  NOT NULL,
    created_at      DATETIME             NOT NULL,
    CONSTRAINT uk_segment_campaign_voucher UNIQUE (voucher_id),
    CONSTRAINT fk_segment_campaign_voucher FOREIGN KEY (voucher_id) REFERENCES voucher(id),
    KEY idx_segment_campaign_segment (segment_code, created_at)
) ENGINE=InnoDB;
