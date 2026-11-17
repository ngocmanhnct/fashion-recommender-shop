# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)

## Dữ liệu

Mẫu 5,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 50). Từ 20/09/2018 đến 22/09/2020: 68.114 khách, 453.384 đơn (khách, ngày, kênh), 1.574.807 lượt mua, 36.191 sản phẩm; 67,1% khách có từ 2 đơn, trung bình 6,7 đơn mỗi khách; 98,8% khách có tuổi.

Loại hàng nhiều lượt mua nhất (product_type_name, 10 loại đầu):

| Loại hàng | Lượt mua | Tỷ lệ |
|---|---:|---:|
| Trousers | 208.210 | 13,2% |
| Dress | 158.995 | 10,1% |
| Sweater | 138.278 | 8,8% |
| T-shirt | 111.065 | 7,1% |
| Top | 78.228 | 5,0% |
| Blouse | 74.194 | 4,7% |
| Vest top | 69.974 | 4,4% |
| Bra | 66.701 | 4,2% |
| Shorts | 56.879 | 3,6% |
| Bikini top | 56.012 | 3,6% |

## Gợi ý sản phẩm

Cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước mốc, đoán sản phẩm mới (chưa từng mua) khách mua trong 7 ngày sau mốc; trúng = ít nhất 1 trong 10 sản phẩm gợi ý được mua. Mô hình xếp hạng học trên 5 tuần ngay trước mốc, mỗi tuần học chỉ dùng dữ liệu trước tuần đó (như website học lại mỗi đêm).

### Tuần kiểm định 09/09–15/09/2020

Mô hình thấy đến hết 08/09/2020; đánh giá 3.050 khách. Tuần này dùng để chọn cấu hình, không phải kết quả.

Mô hình xếp hạng học trên 5 tuần từ 05/08 đến 08/09/2020: 16.560 lượt khách, 1.234.170 dòng ứng viên, 12.226 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 13,4% | 12,2% – 14,6% | 0,040 | 1,6% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 10,7% | 9,6% – 11,8% | 0,029 | 0,4% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 9,7% | 8,7% – 10,8% | 0,026 | 0,1% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +2,7 | +1,6 – +3,8 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +3,7 | +2,6 – +4,8 | tốt hơn |

#### Đóng góp của từng nhóm đặc trưng (tuần kiểm định)

Xáo trộn giá trị 1 nhóm đặc trưng giữa mọi dòng ứng viên (mô hình giữ nguyên): tỷ lệ trúng giảm càng nhiều thì nhóm đó càng quan trọng.

Đủ đặc trưng: tỷ lệ trúng 13,4%.

| Nhóm đặc trưng xáo trộn | Tỷ lệ trúng | Giảm (điểm %) | Khoảng tin cậy 95% |
|---|---:|---:|---|
| Đang bán chạy (theo thời gian) | 10,5% | +2,9 | +1,8 – +4,1 |
| Sản phẩm mới hay cũ | 9,2% | +4,2 | +3,1 – +5,2 |
| Nhóm tuổi | 12,3% | +1,1 | +0,3 – +1,9 |
| Giống sản phẩm khách đã mua (nội dung) | 12,3% | +1,1 | +0,3 – +1,8 |
| Hay mua cùng | 8,3% | +5,1 | +4,0 – +6,2 |
| Điểm công thức hybrid | 7,7% | +5,7 | +4,4 – +7,0 |
| Giá | 13,2% | +0,2 | −0,4 – +0,9 |
| Thói quen của khách | 12,7% | +0,7 | −0,1 – +1,5 |

### Tuần kiểm tra 16/09–22/09/2020 (kết quả)

Mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau; đánh giá 3.031 khách; K = 10; 36.191 sản phẩm.

Mô hình xếp hạng học trên 5 tuần từ 12/08 đến 15/09/2020: 16.307 lượt khách, 1.217.959 dòng ứng viên, 12.015 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 13,2% | 12,1% – 14,3% | 0,037 | 1,8% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 10,5% | 9,5% – 11,6% | 0,028 | 0,4% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 9,6% | 8,7% – 10,7% | 0,025 | 0,0% |
| Đang bán chạy | 9,3% | 8,3% – 10,3% | 0,023 | 0,0% |
| Bán chạy (mọi thời điểm) | 4,1% | 3,4% – 4,8% | 0,011 | 0,1% |
| Nội dung (P(u), cosine) | 1,3% | 0,9% – 1,7% | 0,005 | 19,2% |
| Hay mua cùng | 5,7% | 5,0% – 6,6% | 0,016 | 6,9% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +2,7 | +1,5 – +3,8 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +3,5 | +2,3 – +4,6 | tốt hơn |
| Mô hình xếp hạng − Đang bán chạy | +3,9 | +2,6 – +5,1 | tốt hơn |
| Mô hình xếp hạng − Bán chạy (mọi thời điểm) | +9,1 | +7,9 – +10,4 | tốt hơn |
| Mô hình xếp hạng − Nội dung (P(u), cosine) | +11,8 | +10,7 – +13,0 | tốt hơn |
| Mô hình xếp hạng − Hay mua cùng | +7,4 | +6,2 – +8,6 | tốt hơn |

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

Thời gian chạy (sau khi đọc tệp): 657 giây.
