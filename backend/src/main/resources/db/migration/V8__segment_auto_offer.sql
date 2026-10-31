-- V8__segment_auto_offer.sql
--
-- Tự tặng mã khi khách mới vào nhóm (US-24): Quản trị viên bật cho một nhóm một chương trình tự
-- tặng; mỗi khách vào nhóm sau lúc bật được thêm vào danh sách nhận mã riêng của chương trình và có
-- hạn dùng riêng tính từ lúc nhận. Không sửa V1-V7 vì Flyway kiểm tra checksum.

-- 1. Hạn dùng riêng của từng khách (giờ Việt Nam, như thời hạn của mã). NULL ở các lần tặng tay
--    của US-26: khách dùng được đến hết hạn của mã.
ALTER TABLE voucher_recipient
    ADD COLUMN granted_at DATETIME NULL,
    ADD COLUMN expires_at DATETIME NULL;

-- 2. Chương trình tự tặng là 1 chiến dịch loại AUTO: valid_days là số ngày mỗi khách được dùng mã
--    kể từ lúc nhận, auto_enabled = FALSE khi admin dừng (khách đã nhận vẫn dùng được).
ALTER TABLE segment_campaign
    MODIFY COLUMN target ENUM('ALL', 'CARE', 'AUTO') NOT NULL,
    ADD COLUMN valid_days INT NULL AFTER target,
    ADD COLUMN auto_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER valid_days;

-- 3. Nhóm hiện tại của mỗi khách và lúc vào nhóm đó, do backend ghi khi thấy account_segment đổi
--    (ml-service ghi đè account_segment nên không giữ được lúc khách vào nhóm).
CREATE TABLE segment_entry (
    account_id   BIGINT UNSIGNED PRIMARY KEY,
    segment_code VARCHAR(20) NOT NULL,
    entered_at   DATETIME    NOT NULL,
    KEY idx_segment_entry_code (segment_code, entered_at),
    CONSTRAINT fk_segment_entry_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;
