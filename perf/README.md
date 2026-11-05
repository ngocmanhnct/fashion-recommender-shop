# Đo hiệu năng và khả năng truy cập

Hai script trong thư mục này đo các chỉ tiêu phi chức năng ở mục 2.2.3 của báo cáo (Bảng 2.9)
và KPI K8 của bản cam kết. Cả hai chỉ cần Node 22 trở lên.

| Script                    | Đo gì                                            | Lệnh                                                    |
| ------------------------- | ------------------------------------------------ | ------------------------------------------------------- |
| `tai-dong-thoi.mjs`       | P95 thời gian phản hồi khi N khách dùng cùng lúc | `node perf/tai-dong-thoi.mjs --users 50 --duration 180` |
| `lighthouse-truy-cap.mjs` | Điểm Accessibility của Lighthouse trên 6 trang   | xem đầu file (cần cài `lighthouse` tạm)                 |

`tai-dong-thoi.mjs` ở chế độ thường **đặt đơn COD thật và trừ tồn kho**: chỉ chạy trên CSDL thử.
Trên staging dùng `--read-only` (chỉ đọc, không đăng nhập).

## Kết quả đo ngày 04/11/2026

### Môi trường

- Code: `main` tại commit `2e70260`. Backend chạy file jar, ml-service chạy uvicorn, MySQL 8.4 trong Docker.
- CSDL: danh mục mẫu 1.050 sản phẩm và dữ liệu mô phỏng seed 2026 (354 tài khoản, 1.496 đơn).
- Máy đo có 4 vCPU. Backend, ml-service và MySQL bị ghim vào **2 vCPU** (`taskset`,
  `docker update --cpuset-cpus`) cho giống máy ảo staging (2 vCPU); script tạo tải chạy trên 2 vCPU còn lại.
- Gọi thẳng backend `http://localhost:8080`: **không** có độ trễ mạng internet và không qua Caddy
  như staging. Số đo vì vậy là thời gian xử lý của hệ thống, chưa gồm đường truyền tới người dùng.

### Tải 50 khách đồng thời

Lệnh: `node perf/tai-dong-thoi.mjs --users 50 --duration 180 --ramp 30`. Có 50 khách ảo, mỗi
khách nghỉ 0,5–1,5 giây giữa 2 thao tác. Số đo lấy trong 180 giây sau khi đủ 50 khách.

| Nhóm thao tác                                                | Số yêu cầu |               Lỗi | TB (ms) | P50 |     P95 | P99 | Max | Chỉ tiêu P95    |
| ------------------------------------------------------------ | ---------: | ----------------: | ------: | --: | ------: | --: | --: | --------------- |
| Đọc dữ liệu (danh sách, tìm kiếm, chi tiết, gợi ý trang chủ) |      7.878 |                 0 |      76 |  44 | **284** | 496 | 953 | < 1.000 ms: đạt |
| Gợi ý tính khi yêu cầu (liên quan, mua cùng)                 |      2.308 |                 0 |      37 |  17 | **138** | 318 | 701 | < 1.500 ms: đạt |
| Ghi có giao dịch (đăng ký, địa chỉ, lượt xem, giỏ hàng)      |      2.305 |                 0 |      24 |  12 |  **91** | 198 | 432 | < 2.000 ms: đạt |
| Xác nhận đơn hàng (`POST /api/orders`)                       |        442 | 0 (1 từ chối 409) |      35 |  20 | **119** | 242 | 378 | < 2.000 ms: đạt |

- Tổng có 12.933 yêu cầu trong 185 giây, khoảng 70 yêu cầu mỗi giây.
- **Từ chối 409:** 1 lần đặt hàng bị trả "Giỏ hàng đang trống". Đây là quy tắc chạy đúng, không
  phải lỗi hệ thống: khi đọc giỏ, hệ thống tự loại dòng đã hết hàng (BR-11). Sản phẩm trong giỏ đã
  bị khách khác mua hết giữa lúc xem trước và lúc đặt.
  - Bản script đo lần này tính 409 là lỗi. Bản trong repo đã tách 409 thành cột riêng.
- **Gợi ý liên quan:** trong 180 giây đo, backend không lần nào phải bỏ chờ ml-service (quá
  500 ms) để chuyển sang gợi ý theo quy tắc.
- **Tính lại gợi ý trong lúc tải:** có 276 lượt tính lại. Mỗi lượt xong sau hạn hẹn P50 1,3 s,
  P95 2,3 s, lâu nhất 4,4 s. Hạn hẹn là 3 s sau giỏ hàng/đặt đơn và 30 s sau lượt xem. Như vậy
  gợi ý đổi trong khoảng 7,4 s sau thao tác rõ ý (chỉ tiêu ≤ 10 s) và khoảng 34 s sau lượt xem
  (chỉ tiêu ≤ 1 phút).

Chưa đo: staging thật qua internet. Mạnh có thể tự chạy chế độ chỉ đọc từ máy mình (tốn vài MB
dữ liệu, không tạo đơn):
`node perf/tai-dong-thoi.mjs --base https://fashionshop-staging.eastasia.cloudapp.azure.com --read-only --users 50 --duration 120`

### Lighthouse Accessibility (KPI K8: ≥ 90 mỗi trang)

Đo trên bản build production (`vite build`), chế độ desktop, khách đã đăng nhập, có 1 sản phẩm
trong giỏ.

| Trang                      | Trước khi sửa | Sau khi sửa |
| -------------------------- | ------------: | ----------: |
| Trang chủ                  |            96 |         100 |
| Danh sách sản phẩm         |           100 |         100 |
| Chi tiết sản phẩm          |            96 |         100 |
| Giỏ hàng                   |            96 |         100 |
| Thanh toán                 |           100 |         100 |
| Đăng nhập (chưa đăng nhập) |            96 |         100 |

Đã sửa:

- **Nút "Xem sản phẩm" ở trang chủ:** chữ xanh trên nền đồng chỉ đạt tương phản 3,6:1, cần ≥ 4,5:1.
  Đổi sang nền đồng sáng hơn (`--accent-on-deep`), đạt 5,3:1.
- **Liên kết giữa đoạn chữ** (như "Đăng ký" ở trang đăng nhập, đường dẫn ở trang chi tiết):
  trước đây chỉ khác màu chữ xung quanh, nay có gạch chân.
- **Ảnh trong dòng giỏ hàng:** là một liên kết không có tên, trùng đích với tên sản phẩm ngay bên
  cạnh. Đã bỏ khỏi thứ tự phím Tab và khỏi trình đọc màn hình.
