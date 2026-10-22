-- V4__vnpay_payment_info.sql
--
-- Thanh toán online qua VNPay (US-10, BR-28, BR-34; docs/dac-ta-yeu-cau.md Mục 3.6).
-- Không sửa V1-V3 vì Flyway kiểm tra checksum.
--
--   1. orders lưu thông tin giao dịch VNPay đã thanh toán thành công, cần để gọi API hoàn tiền
--      (BR-28): mã tham chiếu gửi sang VNPay (vnp_TxnRef = mã đơn + thời điểm tạo yêu cầu, giờ
--      Việt Nam, yyyyMMddHHmmss), mã giao dịch VNPay trả về (vnp_TransactionNo), thời điểm thanh
--      toán. Khách có thể tạo yêu cầu thanh toán nhiều lần cho cùng một đơn (bỏ ngang rồi thanh
--      toán lại), chỉ lần thành công được lưu.
--   2. Chỉ mục cho việc quét đơn ONLINE chưa thanh toán quá hạn (BR-34) và hoàn tiền đang chờ.

ALTER TABLE orders
    ADD COLUMN payment_txn_ref        VARCHAR(40) NULL AFTER payment_status,
    ADD COLUMN payment_transaction_no VARCHAR(50) NULL AFTER payment_txn_ref,
    ADD COLUMN paid_at                DATETIME    NULL AFTER payment_transaction_no,
    ADD INDEX idx_orders_payment_status_created (payment_status, created_at);

ALTER TABLE refund
    ADD INDEX idx_refund_status (status);
