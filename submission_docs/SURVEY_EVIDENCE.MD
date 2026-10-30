# BÁO CÁO KHẢO SÁT THỰC TIỄN VÀ SO SÁNH GIẢI PHÁP

- **Đề tài:** Website bán hàng có tích hợp học máy trong gợi ý mua hàng và phân nhóm khách hàng
- **Tiêu chí:** TC1. Mức 5 yêu cầu ≥ 3 bên liên quan hoặc ≥ 30 phiếu khảo sát; chân dung người dùng; so sánh với ≥ 3 giải pháp hiện có.

## 1. Dữ liệu khảo sát thực tế

- **Hình thức:** Google Form, 9 câu hỏi, chia sẻ cho bạn học, các nhóm mua sắm online và mạng xã hội.
- **Số phiếu hợp lệ:** **30**. Đóng khảo sát ngày 13/09/2026.
- **Phỏng vấn sâu:** không thực hiện, vì 30 phiếu đã đạt điều kiện "≥ 30 phiếu" của TC1 Mức 5.
- **Quan sát trực tiếp:** ngày 15/09/2026, tác giả đóng vai khách mua thử trên website DEGREY.

### 1.1. Bộ câu hỏi

1. Độ tuổi (Dưới 18 / 18-22 / 23-30 / Trên 30)
2. Tần suất mua quần áo, thời trang online
3. Kênh mua sắm (chọn nhiều)
4. Cách thường dùng để tìm sản phẩm
5. Điều khó chịu nhất khi mua online
6. Đánh giá mục "có thể bạn cũng thích" trên các sàn
7. Có muốn dùng thử website hiểu gu và tự gợi ý sản phẩm không
8. Chi tiêu trung bình mỗi lần mua (câu mở)
9. Có xu hướng quay lại mua ở cùng shop không

### 1.2. Kết quả chính (N = 30)

| Câu | Kết quả |
|---|---|
| Độ tuổi | 18-22: 47%; Trên 30: 40%; Dưới 18 và 23-30: mỗi nhóm 7% |
| Tần suất mua | Thỉnh thoảng 43%; hằng tháng 43%; hằng tuần 10%; chưa từng 3% |
| Kênh (chọn nhiều) | Shopee 77%; TikTok Shop 67%; Facebook/Instagram 33%; website riêng của shop 13% |
| Cách tìm sản phẩm | Tìm từ khóa 47%; xem gợi ý/quảng cáo 20%; lướt danh mục 17%; hỏi bạn bè/xem review 13%; tùy 3% |
| Khó chịu nhất | Không chắc size 37%; **gợi ý không liên quan 23%**; **quá nhiều lựa chọn 20%**; **khó tìm sản phẩm phù hợp 17%**; khác 3% |
| Gợi ý trên các sàn | Bình thường 50%; khá hữu ích 37%; rất hữu ích 10%; không hữu ích 3% |
| Muốn dùng thử site cá nhân hóa | **Có 70%**; còn tùy 27%; không 3% |
| Quay lại cùng shop | **Có 57%**; tùy sản phẩm 37%; không 7% |
| Chi tiêu mỗi lần | Câu mở, định dạng trả lời không đồng nhất; sơ bộ phần lớn trong khoảng 100.000-1.000.000đ |

**Ý nghĩa:**
- Ba vấn đề in đậm cộng lại là 60%. Cả ba thuộc bài toán gợi ý và tìm kiếm mà đề tài giải quyết.
- 70% muốn dùng thử site cá nhân hóa, đây là cơ sở cho chức năng gợi ý.
- 57% có xu hướng quay lại cùng shop, đây là cơ sở cho phân nhóm khách hàng (RFM + K-Means).

## 2. Chân dung người dùng mục tiêu

Hai nhóm dưới đây tổng hợp từ dữ liệu khảo sát, không phải cá nhân cụ thể. Cỡ mẫu nhỏ, nên đây là giả thuyết phân khúc ban đầu, chưa kiểm định thống kê.

**Nhóm A, 18-22 tuổi (n = 14): mua theo nội dung và gợi ý**
- Dùng TikTok Shop (93%) và Shopee (86%). 100% có mua online.
- Khó chịu nhất là gợi ý không liên quan (36%).
- 64% muốn dùng thử site hiểu gu mình.

**Nhóm B, trên 30 tuổi (n = 12): mua có cân nhắc**
- Dùng Shopee (58%) nhiều hơn TikTok Shop (33%). Là nhóm duy nhất có người mua hằng tuần (3/12).
- Khó chịu nhất là không chắc size (42%).
- 67% muốn dùng thử site hiểu gu mình.

## 3. So sánh với các giải pháp hiện có (5 giải pháp)

| Giải pháp | Gợi ý cá nhân hóa | Phân nhóm khách hàng | Khoảng trống |
|---|---|---|---|
| Shopee | Tài liệu công khai có mô tả mục "Gợi ý cho bạn", nhưng tác giả tự kiểm tra trên tài khoản thật không thấy rõ | Shopee Rewards: hạng theo ngưỡng cố định (Bạc: 3 đơn + 1 triệu; Vàng: 20 đơn + 5 triệu; Kim Cương: 75 đơn + 15 triệu, tính trong 6 tháng) | Phân nhóm là luật cứng theo chi tiêu, không phân tích hành vi |
| TikTok Shop | Rất mạnh, nhưng dẫn dắt qua video ngắn | "TikTok Shop VIP", xét theo chiến dịch, không minh bạch | Cá nhân hóa dựa trên hành vi xem nội dung giải trí; cơ chế là hộp đen |
| Coolmate | Không có, chỉ có danh mục tĩnh | CoolClub: ưu đãi thành viên, không phân hạng theo hành vi | Chưa có gợi ý cá nhân hóa |
| YODY | Không có, chỉ có bộ sưu tập tĩnh | Chính sách khách thân thiết dạng trang tĩnh | Không dùng dữ liệu hành vi |
| DEGREY | Có nhãn "Gợi ý cho bạn", nhiều khả năng chỉ là widget tĩnh của nền tảng dựng web | Không có | Không có cá nhân hóa thật, không có phân nhóm |
| **Hệ thống của đề tài** | Gợi ý hybrid (theo nội dung, sản phẩm hay mua cùng, khảo sát, bán chạy) theo hành vi xem/mua trên chính website; cập nhật trong vài phút; khảo sát lần đầu cho người dùng mới | K-Means trên R, F, M, chọn K bằng Elbow và Silhouette, có diễn giải từng nhóm | Minh bạch, giải thích được, khả thi cho shop nhỏ/vừa |

**Nguồn:**
- [Shopee Rewards](https://fptshop.com.vn/tin-tuc/danh-gia/shopee-rewards-162440)
- [Coolmate](https://www.coolmate.me/)
- [YODY](https://yody.vn/)
- [TikTok và hành trình mua sắm của người Việt 2026](https://cafebiz.vn/84-nguoi-dung-viet-xuong-tien-chi-sau-vai-video-ngan-tiktok-dang-lam-gi-de-ha-be-cac-san-thuong-mai-dien-tu-truyen-thong-176260627095249557.chn)
- [TikTok Shop VIP](https://riokupon.com/vn/blog/dang-ky-tiktok-shop-vip-nhan-ngay-voucher-va-uu-dai-doc-quyen/3830)
- [DEGREY](https://degrey.vn)

## 4. Phạm vi: phần làm và phần không làm

**Làm:**
- Gợi ý hybrid: theo nội dung, sản phẩm hay mua cùng, khảo sát lần đầu, bán chạy.
- Phân nhóm RFM + K-Means.
- Luồng thương mại điện tử đầy đủ: danh mục, giỏ hàng, đặt hàng, thanh toán VNPay sandbox, quản trị.

**Không làm:**
- Gợi ý size theo số đo cơ thể. Đây là bài toán đo lường vật lý, được ghi là hướng phát triển.
- Theo dõi hành vi xuyên nền tảng.
- Thay thế tìm kiếm bằng từ khóa. Gợi ý chỉ là lớp bổ sung, vì 47% người dùng vẫn ưu tiên tìm từ khóa.

---

## 5. Dữ liệu gốc

- File: `survey_data/khao-sat-phieu-tra-loi.csv`. Đây là bản xuất từ Google Form ngày 22/10/2026, đã bỏ cột email và đánh mã phiếu P01-P33.
- Google Form nhận tổng cộng 33 phiếu. Mẫu phân tích (N = 30) gồm 30 phiếu đầu tiên, P01-P30, nhận trong ngày 13/09/2026. Ba phiếu P31-P33 nộp sau khi đã chốt mẫu (13/09 lúc 22:23, 14/09 và 15/09), nên không tính vào mẫu; cột `thuoc_mau_N30` đánh dấu điều này.
- Ngày 22/10/2026 đã tính lại toàn bộ số liệu ở Mục 1.2 từ P01-P30: khớp 100% với kết quả đã báo cáo.
- Phạm vi sản phẩm: khảo sát hỏi về mua sắm thời trang online. Danh mục hiện tại của đề tài (giày, túi ví, balo, vali, phụ kiện từ dữ liệu Tiki) thuộc nhóm sản phẩm thời trang và phụ kiện. Các vấn đề chính rút ra từ khảo sát (gợi ý không liên quan, khó tìm sản phẩm phù hợp) vẫn áp dụng, nên không khảo sát bổ sung.
