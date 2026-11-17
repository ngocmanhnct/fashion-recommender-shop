# Cần bao nhiêu dữ liệu để mô hình xếp hạng hơn công thức (H&M)

Mẫu 5,0% khách (68.114 khách) chia thành các phần rời nhau theo mã khách băm. Mỗi phần như 1 cửa hàng riêng: chỉ thấy đơn của khách trong phần, mô hình xếp hạng học trên 5 tuần trước mốc như website (bỏ ngưỡng số lượt mua trúng) rồi so với công thức hybrid trên chính khách của phần, ở 3 tuần kiểm định từ 26/08 đến 15/09/2020 (không dùng tuần kiểm tra). Trúng = ít nhất 1 trong 10 sản phẩm gợi ý được mua trong 7 ngày sau mốc.

| Mỗi phần | Lần học | Lượt mua trúng ứng viên khi học (TB) | Khách đánh giá | Mô hình xếp hạng | Công thức hybrid | Chênh lệch (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---:|---:|---:|---:|---:|---|---|
| 0,2% (2.725 khách) | 75 (54 lần kém hơn) | 370 | 9.858 | 6,6% | 8,3% | −1,6 | −2,2 – −1,1 | kém hơn |
| 0,5% (6.811 khách) | 30 (23 lần kém hơn) | 1.059 | 9.858 | 8,5% | 9,7% | −1,2 | −1,8 – −0,7 | kém hơn |
| 1,0% (13.623 khách) | 15 (7 lần kém hơn) | 2.285 | 9.858 | 10,1% | 10,1% | +0,1 | −0,5 – +0,6 | chưa phân biệt được |
| 2,5% (34.057 khách) | 6 (0 lần kém hơn) | 6.067 | 9.858 | 12,3% | 10,4% | +2,0 | +1,3 – +2,6 | tốt hơn |

Lượt mua trúng ứng viên: số lần khách mua 1 sản phẩm mới nằm trong danh sách ứng viên, cộng trên 5 tuần học. Website chỉ dùng mô hình xếp hạng khi có ít nhất 5.000 lượt (`ranker.MIN_POSITIVES`), ít hơn thì dùng công thức hybrid.

Thời gian chạy (sau khi đọc tệp): 2.013 giây.
