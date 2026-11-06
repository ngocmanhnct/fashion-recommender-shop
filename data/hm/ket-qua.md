# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)

## Dữ liệu

Mẫu 5,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 50). Từ 20/09/2018 đến 22/09/2020: 68.114 khách, 453.384 đơn (khách, ngày, kênh), 1.574.807 lượt mua, 36.191 sản phẩm; 67,1% khách có từ 2 đơn, trung bình 6,7 đơn mỗi khách; 98,8% khách có tuổi.

## Gợi ý sản phẩm

### Cách 1: giữ lại đơn cuối (như đánh giá trên dữ liệu mô phỏng)

Số khách cần đoán: 42050, đánh giá 10000 khách chọn ngẫu nhiên (seed 2026); K = 10; 36191 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy | 7,0% | 6,5% – 7,4% | 0,018 | 0,1% |
| Đang bán chạy | 10,6% | 10,0% – 11,2% | 0,030 | 0,6% |
| Nội dung (P(u), cosine) | 1,6% | 1,4% – 1,9% | 0,005 | 44,0% |
| Hay mua cùng | 8,6% | 8,0% – 9,1% | 0,026 | 27,6% |
| Hybrid cũ (chưa có đang bán chạy) | 7,2% | 6,7% – 7,8% | 0,023 | 27,1% |
| Hybrid, không ưu tiên gần đây | 12,3% | 11,7% – 12,9% | 0,036 | 2,7% |
| Hybrid (mặc định) | 12,3% | 11,7% – 13,0% | 0,036 | 2,7% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy | +5,4 | +4,8 – +5,9 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | +1,7 | +1,2 – +2,2 | hybrid tốt hơn |
| Hybrid − Nội dung (P(u), cosine) | +10,7 | +10,0 – +11,3 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +3,8 | +3,2 – +4,3 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +5,1 | +4,5 – +5,7 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | +0,0 | −0,0 – +0,1 | chưa phân biệt được |

### Chọn trọng số "đang bán chạy" (tuần kiểm định 09/09–15/09/2020)

Mô hình thấy đến hết 08/09/2020, đoán 7 ngày sau; số khách cần đoán: 3050, đánh giá 3050. Các trọng số khác giữ như mặc định; chọn bộ có NDCG@10 cao nhất, rồi mới đo trên tuần kiểm tra ở cách 2.

| w_t | Chu kỳ bán rã (ngày) | Tỷ lệ trúng | NDCG@10 | Ghi chú |
|---:|---:|---:|---:|---|
| 0,0 | — | 5,2% | 0,016 |  |
| 0,5 | 3 | 10,4% | 0,028 |  |
| 0,5 | 7 | 10,2% | 0,028 |  |
| 1,0 | 3 | 10,5% | 0,029 |  |
| 1,0 | 7 | 10,7% | 0,029 | NDCG cao nhất, đang dùng (model.Weights) |
| 2,0 | 3 | 10,2% | 0,027 |  |
| 2,0 | 7 | 10,5% | 0,028 |  |

### Cách 2: cắt theo thời gian (mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau)

Số khách cần đoán: 3031; K = 10; 36191 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy 7 ngày trước mốc (không mô hình) | 9,6% | 8,7% – 10,7% | 0,025 | 0,0% |
| Bán chạy | 4,1% | 3,4% – 4,8% | 0,011 | 0,1% |
| Đang bán chạy | 9,3% | 8,3% – 10,3% | 0,023 | 0,0% |
| Nội dung (P(u), cosine) | 1,3% | 0,9% – 1,7% | 0,005 | 19,2% |
| Hay mua cùng | 5,7% | 5,0% – 6,6% | 0,016 | 6,9% |
| Hybrid cũ (chưa có đang bán chạy) | 4,8% | 4,1% – 5,6% | 0,014 | 8,1% |
| Hybrid, không ưu tiên gần đây | 10,5% | 9,5% – 11,7% | 0,028 | 0,4% |
| Hybrid (mặc định) | 10,5% | 9,5% – 11,6% | 0,028 | 0,4% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy 7 ngày trước mốc (không mô hình) | +0,9 | −0,2 – +2,0 | chưa phân biệt được |
| Hybrid − Bán chạy | +6,4 | +5,4 – +7,6 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | +1,2 | +0,3 – +2,1 | hybrid tốt hơn |
| Hybrid − Nội dung (P(u), cosine) | +9,2 | +8,0 – +10,3 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +4,8 | +3,7 – +5,8 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +5,7 | +4,6 – +6,7 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | −0,0 | −0,2 – +0,1 | chưa phân biệt được |

## Phân nhóm khách hàng

### Toàn bộ dữ liệu (tính đến 23/09/2020)

Số khách: 68.114.

| K | Inertia | Silhouette |
|---:|---:|---:|
| 1 | 204.342,0 |  |
| 2 | 89.246,1 |  |
| 3 | 66.394,2 | 0,370 |
| 4 | 53.286,0 | 0,372 |
| 5 | 44.479,0 | 0,322 |
| 6 | 38.755,1 | 0,321 |
| 7 | 34.116,6 |  |
| 8 | 31.221,9 |  |

Điểm gãy (Elbow): K = 2. Chọn K = 5, Silhouette = 0,322.

| Nhóm | Số khách | R TB (ngày) | F TB (đơn) | M TB (thang giá H&M) |
|---|---:|---:|---:|---:|
| Khách có nguy cơ rời bỏ | 15.912 | 355 | 2,7 | 0,28 |
| Khách thân thiết | 14.163 | 122 | 9,6 | 0,92 |
| Khách VIP | 10.019 | 22 | 22,7 | 2,30 |
| Khách mới | 9.056 | 43 | 2,9 | 0,20 |
| Khách đã lâu không mua | 18.964 | 428 | 1,1 | 0,06 |

Độ tuổi trong từng nhóm (tỷ lệ trên số khách của nhóm):

| Nhóm | Tuổi TB | Dưới 25 | 25–34 | 35–44 | 45–54 | Từ 55 | Không rõ tuổi |
|---|---:|---:|---:|---:|---:|---:|---:|
| Khách có nguy cơ rời bỏ | 37,5 | 21,6% | 28,7% | 14,4% | 20,1% | 13,9% | 1,3% |
| Khách thân thiết | 36,0 | 24,1% | 33,0% | 10,9% | 19,6% | 11,8% | 0,6% |
| Khách VIP | 35,9 | 23,6% | 34,7% | 10,6% | 19,8% | 11,1% | 0,3% |
| Khách mới | 34,1 | 36,2% | 24,7% | 10,3% | 16,4% | 11,5% | 0,9% |
| Khách đã lâu không mua | 37,2 | 27,4% | 23,1% | 13,8% | 18,0% | 15,6% | 2,2% |

Độ ổn định: huấn luyện lại 20 lần, mỗi lần trên 80% khách chọn ngẫu nhiên, rồi so nhóm của mọi khách với mô hình đầy đủ: ARI trung bình 0,554, thấp nhất 0,542.

### Kiểm tra trên tương lai (phân nhóm đến 25/06/2020, xem 90 ngày sau)

Mô hình chỉ thấy dữ liệu trước mốc (64.516 khách, K = 4, Silhouette = 0,391). Nhóm có ý nghĩa thì nhóm giá trị cao phải mua lại nhiều hơn hẳn nhóm sắp mất.

| Nhóm (lúc mốc) | Số khách | Tỷ lệ mua lại sau mốc | Chi tiêu TB sau mốc |
|---|---:|---:|---:|
| Khách đã lâu không mua | 26.481 | 9,8% | 0,01 |
| Khách VIP | 12.460 | 79,1% | 0,23 |
| Khách có nguy cơ rời bỏ | 16.896 | 38,4% | 0,06 |
| Khách mới | 8.679 | 39,1% | 0,05 |
| Tất cả | 64.516 | 34,6% | 0,07 |

Thời gian chạy (sau khi đọc tệp): 2.183 giây.
