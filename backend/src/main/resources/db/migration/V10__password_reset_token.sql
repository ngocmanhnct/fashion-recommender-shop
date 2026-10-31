-- V10__password_reset_token.sql
--
-- Quên mật khẩu (US-05 mở rộng, chức năng 21, BR-48): mỗi lần khách xin đặt lại mật khẩu, backend
-- tạo 1 chuỗi ngẫu nhiên, gửi liên kết chứa chuỗi đó qua email và chỉ lưu bản băm SHA-256 của nó ở
-- đây (lộ bảng này cũng không dùng được liên kết). Liên kết dùng được 1 lần (used_at) và hết hạn sau
-- 30 phút (expires_at). Không sửa V1-V9 vì Flyway kiểm tra checksum.
CREATE TABLE password_reset_token (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64)        NOT NULL,
    created_at DATETIME        NOT NULL,
    expires_at DATETIME        NOT NULL,
    used_at    DATETIME        NULL,
    UNIQUE KEY uk_password_reset_token_hash (token_hash),
    KEY idx_password_reset_token_account (account_id, created_at),
    CONSTRAINT fk_password_reset_token_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB;
