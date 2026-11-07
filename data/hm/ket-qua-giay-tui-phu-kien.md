# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle), chỉ giày, túi, phụ kiện

## Dữ liệu

Mẫu 100,0% khách (8 ký tự đầu của mã khách đổi sang số, chia 1000 dư nhỏ hơn 1000). Từ 20/09/2018 đến 22/09/2020: 587.605 khách, 1.549.261 đơn (khách, ngày, kênh), 2.352.427 lượt mua, 9.202 sản phẩm; 51,5% khách có từ 2 đơn, trung bình 2,6 đơn mỗi khách; 99,3% khách có tuổi.

Chỉ giữ nhóm hàng Accessories, Bags, Shoes của H&M (giày, túi, phụ kiện, cùng loại với hàng của website): 2.352.427 trên 31.788.324 lượt mua của khách trong mẫu (7,4%). Đơn, khách, R/F/M và gợi ý chỉ tính trên phần này, như thể H&M chỉ bán những loại hàng đó; trọng số gợi ý giữ nguyên.

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

### Cách 1: giữ lại đơn cuối (như đánh giá trên dữ liệu mô phỏng)

Số khách cần đoán: 283728, đánh giá 10000 khách chọn ngẫu nhiên (seed 2026); K = 10; 9202 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy | 9,4% | 8,9% – 10,0% | 0,043 | 0,2% |
| Đang bán chạy | 13,7% | 13,0% – 14,3% | 0,062 | 2,1% |
| Nội dung (P(u), cosine) | 1,6% | 1,4% – 1,8% | 0,007 | 76,1% |
| Hay mua cùng | 7,0% | 6,5% – 7,5% | 0,031 | 67,4% |
| Hybrid cũ (chưa có đang bán chạy) | 5,6% | 5,1% – 6,0% | 0,024 | 63,2% |
| Hybrid, không ưu tiên gần đây | 14,9% | 14,1% – 15,5% | 0,067 | 13,1% |
| Hybrid (mặc định) | 14,9% | 14,1% – 15,5% | 0,067 | 13,2% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy | +5,4 | +4,8 – +6,0 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | +1,2 | +0,6 – +1,8 | hybrid tốt hơn |
| Hybrid − Nội dung (P(u), cosine) | +13,3 | +12,5 – +14,0 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +7,9 | +7,2 – +8,5 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +9,3 | +8,6 – +10,0 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | +0,0 | −0,0 – +0,1 | chưa phân biệt được |

### Chọn trọng số "đang bán chạy" (tuần kiểm định 09/09–15/09/2020)

Mô hình thấy đến hết 08/09/2020, đoán 7 ngày sau; số khách cần đoán: 8253, đánh giá 8253. Các trọng số khác giữ như mặc định; chọn bộ có NDCG@10 cao nhất, rồi mới đo trên tuần kiểm tra ở cách 2.

| w_t | Chu kỳ bán rã (ngày) | Tỷ lệ trúng | NDCG@10 | Ghi chú |
|---:|---:|---:|---:|---|
| 0,0 | — | 4,8% | 0,021 |  |
| 0,5 | 3 | 14,0% | 0,058 |  |
| 0,5 | 7 | 13,2% | 0,055 |  |
| 1,0 | 3 | 14,6% | 0,063 | NDCG cao nhất |
| 1,0 | 7 | 14,1% | 0,060 | đang dùng (model.Weights) |
| 2,0 | 3 | 14,5% | 0,063 |  |
| 2,0 | 7 | 13,8% | 0,060 |  |

### Cách 2: cắt theo thời gian (mô hình thấy đến hết 15/09/2020, đoán 7 ngày sau)

Số khách cần đoán: 8202; K = 10; 9202 sản phẩm.

| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |
|---|---:|---|---:|---:|
| Bán chạy 7 ngày trước mốc (không mô hình) | 12,9% | 12,3% – 13,6% | 0,059 | 0,2% |
| Bán chạy | 8,8% | 8,2% – 9,5% | 0,043 | 0,2% |
| Đang bán chạy | 13,5% | 12,8% – 14,2% | 0,058 | 0,2% |
| Nội dung (P(u), cosine) | 1,3% | 1,1% – 1,6% | 0,005 | 69,9% |
| Hay mua cùng | 6,0% | 5,5% – 6,5% | 0,026 | 54,2% |
| Hybrid cũ (chưa có đang bán chạy) | 4,4% | 3,9% – 4,9% | 0,019 | 53,7% |
| Hybrid, không ưu tiên gần đây | 13,5% | 12,8% – 14,3% | 0,059 | 3,7% |
| Hybrid (mặc định) | 13,5% | 12,8% – 14,3% | 0,059 | 3,7% |

| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |
|---|---:|---|---|
| Hybrid − Bán chạy 7 ngày trước mốc (không mô hình) | +0,5 | −0,1 – +1,2 | chưa phân biệt được |
| Hybrid − Bán chạy | +4,7 | +4,1 – +5,3 | hybrid tốt hơn |
| Hybrid − Đang bán chạy | −0,0 | −0,6 – +0,6 | chưa phân biệt được |
| Hybrid − Nội dung (P(u), cosine) | +12,2 | +11,5 – +12,9 | hybrid tốt hơn |
| Hybrid − Hay mua cùng | +7,5 | +6,9 – +8,1 | hybrid tốt hơn |
| Hybrid − Hybrid cũ (chưa có đang bán chạy) | +9,1 | +8,5 – +9,8 | hybrid tốt hơn |
| Hybrid − Hybrid, không ưu tiên gần đây | +0,0 | +0,0 – +0,0 | chưa phân biệt được |

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

Thời gian chạy (sau khi đọc tệp): 707 giây.
