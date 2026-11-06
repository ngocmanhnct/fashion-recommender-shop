# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)

## Dữ liệu

Mẫu 100,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 1000). Từ 20/09/2018 đến 22/09/2020: 1.362.281 khách, 9.174.457 đơn (khách, ngày, kênh), 31.788.324 lượt mua, 46.834 sản phẩm; 67,2% khách có từ 2 đơn, trung bình 6,7 đơn mỗi khách; 98,8% khách có tuổi.

## Gợi ý sản phẩm

### Cách 1: giữ lại đơn cuối (như đánh giá trên dữ liệu mô phỏng)

Số khách cần đoán: 841290, đánh giá 10000 khách chọn ngẫu nhiên (seed 2026); K = 10; 46834 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy | 6,7% | 6,2% – 7,1% | 0,018 | 0,0% |
| Đang bán chạy | 9,4% | 8,8% – 9,9% | 0,026 | 0,4% |
| Nội dung (P(u), cosine) | 1,5% | 1,2% – 1,7% | 0,004 | 35,9% |
| Hay mua cùng | 9,0% | 8,4% – 9,5% | 0,028 | 14,9% |
| Hybrid cũ (chưa có đang bán chạy) | 7,5% | 7,0% – 8,0% | 0,024 | 20,4% |
| Hybrid, không ưu tiên gần đây | 12,1% | 11,4% – 12,7% | 0,035 | 3,1% |
| Hybrid (mặc định) | 12,1% | 11,5% – 12,7% | 0,036 | 3,1% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy | +5,4 | +4,9 – +6,0 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | +2,7 | +2,1 – +3,3 | hybrid tốt hơn |
| Hybrid − Nội dung (P(u), cosine) | +10,6 | +10,0 – +11,3 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +3,1 | +2,6 – +3,6 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +4,6 | +4,0 – +5,2 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | +0,0 | −0,0 – +0,1 | chưa phân biệt được |

### Chọn trọng số "đang bán chạy" (tuần kiểm định 09/09–15/09/2020)

Mô hình thấy đến hết 08/09/2020, đoán 7 ngày sau; số khách cần đoán: 62725, đánh giá 10000. Các trọng số khác giữ như mặc định; chọn bộ có NDCG@10 cao nhất, rồi mới đo trên tuần kiểm tra ở cách 2.

| w_t | Chu kỳ bán rã (ngày) | Tỷ lệ trúng | NDCG@10 | Ghi chú |
|---:|---:|---:|---:|---|
| 0,0 | — | 5,8% | 0,016 |  |
| 0,5 | 3 | 10,7% | 0,028 |  |
| 0,5 | 7 | 10,4% | 0,028 |  |
| 1,0 | 3 | 11,0% | 0,028 | NDCG cao nhất |
| 1,0 | 7 | 10,8% | 0,028 | đang dùng (model.Weights) |
| 2,0 | 3 | 10,9% | 0,028 |  |
| 2,0 | 7 | 10,7% | 0,027 |  |

### Cách 2: cắt theo thời gian (mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau)

Số khách cần đoán: 59715, đánh giá 10000 khách chọn ngẫu nhiên (seed 2026); K = 10; 46834 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy 7 ngày trước mốc (không mô hình) | 10,3% | 9,7% – 10,8% | 0,026 | 0,0% |
| Bán chạy | 4,4% | 4,0% – 4,8% | 0,012 | 0,1% |
| Đang bán chạy | 9,7% | 9,2% – 10,3% | 0,025 | 0,0% |
| Nội dung (P(u), cosine) | 1,3% | 1,1% – 1,5% | 0,004 | 26,6% |
| Hay mua cùng | 6,8% | 6,3% – 7,2% | 0,018 | 8,4% |
| Hybrid cũ (chưa có đang bán chạy) | 6,2% | 5,7% – 6,6% | 0,017 | 12,6% |
| Hybrid, không ưu tiên gần đây | 11,6% | 11,0% – 12,3% | 0,030 | 0,9% |
| Hybrid (mặc định) | 11,6% | 11,0% – 12,3% | 0,030 | 0,9% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy 7 ngày trước mốc (không mô hình) | +1,4 | +0,8 – +1,9 | hybrid tốt hơn |
| Hybrid − Bán chạy | +7,3 | +6,7 – +7,9 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | +1,9 | +1,4 – +2,5 | hybrid tốt hơn |
| Hybrid − Nội dung (P(u), cosine) | +10,4 | +9,7 – +11,0 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +4,9 | +4,3 – +5,4 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +5,5 | +5,0 – +6,1 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | +0,0 | −0,0 – +0,1 | chưa phân biệt được |

## Phân nhóm khách hàng

### Toàn bộ dữ liệu (tính đến 23/09/2020)

Số khách: 1.362.281.

| K | Inertia | Silhouette |
|---:|---:|---:|
| 1 | 4.086.843,0 |  |
| 2 | 1.791.712,5 |  |
| 3 | 1.331.612,3 | 0,362 |
| 4 | 1.065.702,0 | 0,375 |
| 5 | 888.254,8 | 0,322 |
| 6 | 777.796,7 | 0,317 |
| 7 | 682.853,8 |  |
| 8 | 625.510,8 |  |

Điểm gãy (Elbow): K = 2. Chọn K = 4, Silhouette = 0,375.

| Nhóm | Số khách | R TB (ngày) | F TB (đơn) | M TB (thang giá H&M) |
|---|---:|---:|---:|---:|
| Khách mới | 188.757 | 43 | 2,9 | 0,21 |
| Khách VIP | 278.376 | 34 | 20,5 | 2,06 |
| Khách đã lâu không mua | 548.261 | 423 | 1,4 | 0,10 |
| Khách có nguy cơ rời bỏ | 346.887 | 208 | 6,3 | 0,62 |

Độ tuổi trong từng nhóm (tỷ lệ trên số khách của nhóm):

| Nhóm | Tuổi TB | Dưới 25 | 25–34 | 35–44 | 45–54 | Từ 55 | Không rõ tuổi |
|---|---:|---:|---:|---:|---:|---:|---:|
| Khách mới | 34,2 | 35,9% | 25,3% | 10,1% | 15,8% | 11,9% | 0,9% |
| Khách VIP | 35,9 | 23,4% | 34,7% | 11,1% | 19,1% | 11,4% | 0,3% |
| Khách đã lâu không mua | 37,4 | 25,6% | 24,7% | 14,1% | 18,5% | 15,2% | 1,9% |
| Khách có nguy cơ rời bỏ | 36,5 | 23,2% | 32,1% | 11,8% | 19,6% | 12,6% | 0,8% |

Độ ổn định: huấn luyện lại 5 lần, mỗi lần trên 80% khách chọn ngẫu nhiên, rồi so nhóm của mọi khách với mô hình đầy đủ: ARI trung bình 0,896, thấp nhất 0,535.

### Kiểm tra trên tương lai (phân nhóm đến 25/06/2020, xem 90 ngày sau)

Mô hình chỉ thấy dữ liệu trước mốc (1.291.147 khách, K = 4, Silhouette = 0,389). Nhóm có ý nghĩa thì nhóm giá trị cao phải mua lại nhiều hơn hẳn nhóm sắp mất.

| Nhóm (lúc mốc) | Số khách | Tỷ lệ mua lại sau mốc | Chi tiêu TB sau mốc |
|---|---:|---:|---:|
| Khách mới | 174.713 | 38,9% | 0,05 |
| Khách VIP | 246.559 | 79,4% | 0,23 |
| Khách có nguy cơ rời bỏ | 336.919 | 38,5% | 0,06 |
| Khách đã lâu không mua | 532.956 | 10,2% | 0,01 |
| Tất cả | 1.291.147 | 34,7% | 0,07 |

Thời gian chạy (sau khi đọc tệp): 3.440 giây.
