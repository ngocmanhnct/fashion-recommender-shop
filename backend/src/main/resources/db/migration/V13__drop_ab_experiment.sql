-- V13__drop_ab_experiment.sql
--
-- Bỏ chức năng thử nghiệm A/B gợi ý ở trang chủ (ngày 16/11/2026): website chưa có người dùng thật,
-- mỗi nhóm cần hàng trăm đến hàng nghìn khách mới kết luận được, nên đề tài chỉ giữ thử nghiệm A/B
-- ở phần hướng phát triển. Xóa 2 bảng V9 đã tạo, bảng gán nhóm trước vì có khóa ngoại tới bảng thử
-- nghiệm. Không sửa V9 vì Flyway kiểm tra checksum.
DROP TABLE IF EXISTS ab_assignment;
DROP TABLE IF EXISTS ab_experiment;
