-- V14__campaign_holdout_cap.sql
--
-- Đo đúng hiệu quả tặng mã theo nhóm và chặn tặng mã hàng loạt (US-24, BR-43; rà soát 12/11/2026,
-- việc 3 và 4). Không sửa V1-V13 vì Flyway kiểm tra checksum.

-- 1. Nhóm đối chứng (holdout): lúc tặng, giữ lại ngẫu nhiên holdout_percent % khách đủ điều kiện,
--    không tặng mã, để so tỷ lệ mua của khách nhận mã với khách không nhận. Khách bị giữ lại vẫn
--    ghi vào voucher_recipient (held_out = TRUE, cùng lúc nhận và hạn như khách nhận mã) nhưng
--    không dùng được mã và không thấy mã.
--    gross_margin_percent: lãi gộp trên tiền hàng (%) admin nhập để tính lãi tăng thêm; NULL là
--    chỉ báo tỷ lệ mua, không tính lãi.
--    max_recipients: chỉ cho tự tặng, trần số khách (kể cả khách bị giữ lại) của chương trình;
--    NULL là không giới hạn.
ALTER TABLE segment_campaign
    ADD COLUMN holdout_percent      INT NOT NULL DEFAULT 0 AFTER auto_enabled,
    ADD COLUMN max_recipients       INT NULL AFTER holdout_percent,
    ADD COLUMN gross_margin_percent INT NULL AFTER max_recipients,
    ADD COLUMN holdout_count        INT NOT NULL DEFAULT 0 AFTER recipient_count;

ALTER TABLE voucher_recipient
    ADD COLUMN held_out BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Đổi nhóm do huấn luyện lại không phải "vào nhóm": segment_entry nhớ mô hình và F, M của khách
--    ở lần ghi trước. Đổi nhóm khi mô hình đã khác mà F, M không đổi (khách không mua thêm) thì
--    moved_by_model = TRUE, chương trình tự tặng bỏ qua khách đó.
ALTER TABLE segment_entry
    ADD COLUMN model_id       BIGINT UNSIGNED NULL,
    ADD COLUMN rfm_frequency  INT UNSIGNED NULL,
    ADD COLUMN rfm_monetary   BIGINT UNSIGNED NULL,
    ADD COLUMN moved_by_model BOOLEAN NOT NULL DEFAULT FALSE;
