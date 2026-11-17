# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle), chỉ giày, túi, phụ kiện

## Dữ liệu

Mẫu 100,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 1000). Từ 20/09/2018 đến 22/09/2020: 587.605 khách, 1.549.261 đơn (khách, ngày, kênh), 2.352.427 lượt mua, 9.202 sản phẩm; 51,5% khách có từ 2 đơn, trung bình 2,6 đơn mỗi khách; 99,3% khách có tuổi.

Chỉ giữ nhóm hàng Accessories, Bags, Shoes của H&M (giày, túi, phụ kiện, cùng loại với hàng của website): 2.352.427 trên 31.788.324 lượt mua của khách trong mẫu (7,4%). Đơn, khách, R/F/M và gợi ý chỉ tính trên phần này, như thể H&M chỉ bán những loại hàng đó (mô hình xếp hạng học trên chính phần này; cấu hình giữ nguyên).

Loại hàng nhiều lượt mua nhất (product_type_name, 10 loại đầu):

| Loại hàng | Lượt mua | Tỷ lệ |
|---|---:|---:|
| Bag | 218.895 | 9,3% |
| Belt | 202.433 | 8,6% |
| Boots | 182.917 | 7,8% |
| Earring | 175.562 | 7,5% |
| Scarf | 156.517 | 6,7% |
| Necklace | 131.691 | 5,6% |
| Sandals | 125.291 | 5,3% |
| Sneakers | 124.335 | 5,3% |
| Sunglasses | 117.574 | 5,0% |
| Other accessories | 92.879 | 3,9% |

## Gợi ý sản phẩm

Cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước mốc, đoán sản phẩm mới (chưa từng mua) khách mua trong 7 ngày sau mốc; trúng = ít nhất 1 trong 10 sản phẩm gợi ý được mua. Mô hình xếp hạng học trên 5 tuần ngay trước mốc, mỗi tuần học chỉ dùng dữ liệu trước tuần đó (như website học lại mỗi đêm).

### Tuần kiểm định 09/09–15/09/2020

Mô hình thấy đến hết 08/09/2020; đánh giá 8.253 khách. Tuần này dùng để chọn cấu hình, không phải kết quả.

Mô hình xếp hạng học trên 5 tuần từ 05/08 đến 08/09/2020: 46.703 lượt khách, 3.388.966 dòng ứng viên, 29.863 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 15,7% | 14,9% – 16,4% | 0,070 | 5,2% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 14,1% | 13,3% – 14,8% | 0,060 | 3,9% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 13,5% | 12,8% – 14,3% | 0,059 | 0,2% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +1,6 | +1,0 – +2,3 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +2,2 | +1,5 – +2,8 | tốt hơn |

#### Đóng góp của từng nhóm đặc trưng (tuần kiểm định)

Xáo trộn giá trị 1 nhóm đặc trưng giữa mọi dòng ứng viên (mô hình giữ nguyên): tỷ lệ trúng giảm càng nhiều thì nhóm đó càng quan trọng.

Đủ đặc trưng: tỷ lệ trúng 15,7%.

| Nhóm đặc trưng xáo trộn | Tỷ lệ trúng | Giảm (điểm %) | Khoảng tin cậy 95% |
|---|---:|---:|---|
| Đang bán chạy (theo thời gian) | 7,9% | +7,8 | +6,9 – +8,6 |
| Sản phẩm mới hay cũ | 13,5% | +2,2 | +1,7 – +2,7 |
| Nhóm tuổi | 14,2% | +1,5 | +1,0 – +2,1 |
| Giống sản phẩm khách đã mua (nội dung) | 15,0% | +0,7 | +0,3 – +1,1 |
| Hay mua cùng | 13,7% | +2,0 | +1,4 – +2,5 |
| Điểm công thức hybrid | 11,5% | +4,3 | +3,5 – +4,9 |
| Giá | 15,7% | +0,0 | −0,3 – +0,3 |
| Thói quen của khách | 15,1% | +0,6 | +0,1 – +1,0 |

### Tuần kiểm tra 16/09–22/09/2020 (kết quả)

Mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau; đánh giá 8.202 khách; K = 10; 9.202 sản phẩm.

Mô hình xếp hạng học trên 5 tuần từ 12/08 đến 15/09/2020: 45.500 lượt khách, 3.303.202 dòng ứng viên, 29.279 lượt mua trúng ứng viên (28 đặc trưng).

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Mô hình xếp hạng (mặc định) | 14,5% | 13,7% – 15,2% | 0,068 | 5,5% |
| Công thức hybrid (khi chưa đủ dữ liệu học) | 13,5% | 12,8% – 14,3% | 0,059 | 3,7% |
| Bán chạy 7 ngày trước mốc (không mô hình) | 12,9% | 12,3% – 13,6% | 0,059 | 0,2% |
| Đang bán chạy | 13,5% | 12,8% – 14,2% | 0,058 | 0,2% |
| Bán chạy (mọi thời điểm) | 8,8% | 8,2% – 9,5% | 0,043 | 0,2% |
| Nội dung (P(u), cosine) | 1,3% | 1,1% – 1,6% | 0,005 | 69,9% |
| Hay mua cùng | 6,0% | 5,5% – 6,5% | 0,026 | 54,2% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Mô hình xếp hạng − Công thức hybrid (khi chưa đủ dữ liệu học) | +1,0 | +0,3 – +1,6 | tốt hơn |
| Mô hình xếp hạng − Bán chạy 7 ngày trước mốc (không mô hình) | +1,5 | +0,8 – +2,2 | tốt hơn |
| Mô hình xếp hạng − Đang bán chạy | +1,0 | +0,2 – +1,6 | tốt hơn |
| Mô hình xếp hạng − Bán chạy (mọi thời điểm) | +5,7 | +4,9 – +6,4 | tốt hơn |
| Mô hình xếp hạng − Nội dung (P(u), cosine) | +13,2 | +12,4 – +13,9 | tốt hơn |
| Mô hình xếp hạng − Hay mua cùng | +8,4 | +7,8 – +9,3 | tốt hơn |

## Phân nhóm khách hàng

### Toàn bộ dữ liệu (tính đến 23/09/2020)

Số khách: 587.605.

| K | Inertia | Silhouette |
|---:|---:|---:|
| 1 | 1.762.815,0 |  |
| 2 | 954.393,5 |  |
| 3 | 746.108,4 | 0,376 |
| 4 | 571.250,8 | 0,347 |
| 5 | 480.662,2 | 0,309 |
| 6 | 421.978,8 | 0,310 |
| 7 | 380.200,6 |  |
| 8 | 344.329,5 |  |

Điểm gãy (Elbow): K = 3. Chọn K = 4, Silhouette = 0,347.

| Nhóm | Số khách | R TB (ngày) | F TB (đơn) | M TB (thang giá H&M) |
|---|---:|---:|---:|---:|
| Khách có nguy cơ rời bỏ | 158.750 | 326 | 2,7 | 0,11 |
| Khách đã lâu không mua | 243.018 | 449 | 1,1 | 0,02 |
| Khách mới | 97.533 | 59 | 1,7 | 0,04 |
| Khách VIP | 88.304 | 87 | 7,7 | 0,31 |

Độ tuổi trong từng nhóm (tỷ lệ trên số khách của nhóm):

| Nhóm | Tuổi TB | Dưới 25 | 25–34 | 35–44 | 45–54 | Từ 55 | Không rõ tuổi |
|---|---:|---:|---:|---:|---:|---:|---:|
| Khách có nguy cơ rời bỏ | 35,4 | 22,8% | 37,0% | 11,9% | 17,2% | 10,6% | 0,6% |
| Khách đã lâu không mua | 35,6 | 26,6% | 30,2% | 12,6% | 18,3% | 11,2% | 1,0% |
| Khách mới | 33,3 | 34,8% | 29,4% | 10,2% | 16,2% | 8,8% | 0,5% |
| Khách VIP | 34,6 | 21,9% | 40,8% | 12,4% | 15,7% | 9,0% | 0,2% |

Độ ổn định: huấn luyện lại 5 lần, mỗi lần trên 80% khách chọn ngẫu nhiên, rồi so nhóm của mọi khách với mô hình đầy đủ: ARI trung bình 0,976, thấp nhất 0,964.

### Kiểm tra trên tương lai (phân nhóm đến 25/06/2020, xem 90 ngày sau)

Mô hình chỉ thấy dữ liệu trước mốc (546.110 khách, K = 3, Silhouette = 0,416). Nhóm có ý nghĩa thì nhóm giá trị cao phải mua lại nhiều hơn hẳn nhóm sắp mất.

| Nhóm (lúc mốc) | Số khách | Tỷ lệ mua lại sau mốc | Chi tiêu TB sau mốc |
|---|---:|---:|---:|
| Khách thân thiết | 69.017 | 24,2% | 0,01 |
| Khách đã lâu không mua | 338.322 | 10,5% | 0,00 |
| Khách VIP | 138.771 | 33,3% | 0,02 |
| Tất cả | 546.110 | 18,0% | 0,01 |

Thời gian chạy (sau khi đọc tệp): 1.231 giây.
