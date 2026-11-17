# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)

## Dữ liệu

Mẫu 100,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 1000). Từ 20/09/2018 đến 22/09/2020: 1.362.281 khách, 9.174.457 đơn (khách, ngày, kênh), 31.788.324 lượt mua, 46.834 sản phẩm; 67,2% khách có từ 2 đơn, trung bình 6,7 đơn mỗi khách; 98,8% khách có tuổi.

Loại hàng nhiều lượt mua nhất (product_type_name, 10 loại đầu):

| Loại hàng | Lượt mua | Tỷ lệ |
|---|---:|---:|
| Trousers | 4.217.017 | 13,3% |
| Dress | 3.238.428 | 10,2% |
| Sweater | 2.783.274 | 8,8% |
| T-shirt | 2.203.750 | 6,9% |
| Top | 1.583.408 | 5,0% |
| Blouse | 1.504.868 | 4,7% |
| Vest top | 1.414.101 | 4,4% |
| Bra | 1.335.233 | 4,2% |
| Shorts | 1.152.513 | 3,6% |
| Bikini top | 1.126.202 | 3,5% |

## Gợi ý sản phẩm

Cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước mốc, đoán sản phẩm mới (chưa từng mua) khách mua trong 7 ngày sau mốc; trúng = ít nhất 1 trong 10 sản phẩm gợi ý được mua. Mô hình xếp hạng học trên 5 tuần ngay trước mốc, mỗi tuần học chỉ dùng dữ liệu trước tuần đó (như website học lại mỗi đêm).

### Tuần kiểm định 09/09–15/09/2020

Mô hình thấy đến hết 08/09/2020; đánh giá 10.000 khách. Tuần này dùng để chọn cấu hình, không phải kết quả.

Mô hình xếp hạng học trên 5 tuần từ 05/08 đến 08/09/2020: 50.000 lượt khách, 3.642.227 dòng ứng viên, 38.046 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 14,6% | 13,9% – 15,3% | 0,042 | 2,0% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 10,8% | 10,2% – 11,4% | 0,028 | 0,9% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 10,0% | 9,5% – 10,6% | 0,028 | 0,0% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +3,8 | +3,1 – +4,5 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +4,5 | +3,9 – +5,2 | tốt hơn |

#### Đóng góp của từng nhóm đặc trưng (tuần kiểm định)

Xáo trộn giá trị 1 nhóm đặc trưng giữa mọi dòng ứng viên (mô hình giữ nguyên): tỷ lệ trúng giảm càng nhiều thì nhóm đó càng quan trọng.

Đủ đặc trưng: tỷ lệ trúng 14,6%.

| Nhóm đặc trưng xáo trộn | Tỷ lệ trúng | Giảm (điểm %) | Khoảng tin cậy 95% |
|---|---:|---:|---|
| Đang bán chạy (theo thời gian) | 5,7% | +8,9 | +8,2 – +9,7 |
| Sản phẩm mới hay cũ | 10,1% | +4,5 | +3,8 – +5,1 |
| Nhóm tuổi | 12,1% | +2,4 | +1,9 – +3,0 |
| Giống sản phẩm khách đã mua (nội dung) | 12,8% | +1,8 | +1,4 – +2,3 |
| Hay mua cùng | 8,9% | +5,6 | +4,9 – +6,3 |
| Điểm công thức hybrid | 12,9% | +1,7 | +1,2 – +2,2 |
| Giá | 14,6% | −0,0 | −0,3 – +0,3 |
| Thói quen của khách | 13,6% | +1,0 | +0,5 – +1,5 |

### Tuần kiểm tra 16/09–22/09/2020 (kết quả)

Mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau; đánh giá 10.000 khách; K = 10; 46.834 sản phẩm.

Mô hình xếp hạng học trên 5 tuần từ 12/08 đến 15/09/2020: 50.000 lượt khách, 3.644.897 dòng ứng viên, 37.767 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 15,7% | 14,9% – 16,4% | 0,046 | 2,1% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 11,6% | 11,0% – 12,3% | 0,030 | 0,9% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 10,3% | 9,7% – 10,8% | 0,026 | 0,0% |
| Đang bán chạy | 9,7% | 9,2% – 10,3% | 0,025 | 0,0% |
| Bán chạy (mọi thời điểm) | 4,4% | 4,0% – 4,8% | 0,012 | 0,1% |
| Nội dung (P(u), cosine) | 1,3% | 1,1% – 1,5% | 0,004 | 26,6% |
| Hay mua cùng | 6,8% | 6,3% – 7,2% | 0,018 | 8,4% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +4,0 | +3,3 – +4,7 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +5,4 | +4,6 – +6,1 | tốt hơn |
| Mô hình xếp hạng − Đang bán chạy | +5,9 | +5,2 – +6,6 | tốt hơn |
| Mô hình xếp hạng − Bán chạy (mọi thời điểm) | +11,3 | +10,5 – +12,1 | tốt hơn |
| Mô hình xếp hạng − Nội dung (P(u), cosine) | +14,4 | +13,7 – +15,1 | tốt hơn |
| Mô hình xếp hạng − Hay mua cùng | +8,9 | +8,2 – +9,6 | tốt hơn |

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

Thời gian chạy (sau khi đọc tệp): 4.118 giây.
