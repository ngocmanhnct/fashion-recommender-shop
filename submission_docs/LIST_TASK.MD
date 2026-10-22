# LIST_TASK: ĐỀ TÀI, MỤC TIÊU VÀ BẢNG PHÂN CÔNG CÔNG VIỆC

> Cập nhật 22/10/2026. Nguồn: `ke-hoach-15-tuan.md`, `docs/dac-ta-yeu-cau.md` và lịch sử PR trên GitHub. Đề tài do 1 sinh viên thực hiện, nên mọi đầu việc do một người phụ trách. Cột "Tuần" lấy theo kế hoạch 15 tuần; cột "Trạng thái" tính đến 22/10.

## 1. Thông tin đề tài

- **Tên đề tài:** Website bán hàng có tích hợp học máy trong gợi ý mua hàng và phân nhóm khách hàng
- **Sinh viên:** Đoàn Ngọc Mạnh (thực hiện một mình, đóng góp 100%)
- **GVHD:** ThS. Nguyễn Minh Đạo
- **Thời gian:** 15 tuần, từ 10/09/2026 đến 23/12/2026

**Mục tiêu:**
1. Xây dựng website thương mại điện tử đầy đủ luồng mua hàng.
2. Gợi ý sản phẩm cá nhân hóa, cập nhật gần thời gian thực.
3. Phân nhóm khách hàng bằng K-Means trên R, F, M.

**Công nghệ:**
- Backend: Spring Boot (Java 21), MySQL, Flyway.
- Frontend: React/Vite.
- Học máy: FastAPI (Python, scikit-learn).
- Hạ tầng: Docker Compose, GitHub Actions, SonarQube Cloud, gitleaks.
- Thanh toán: VNPay sandbox.

## 2. Bảng phân công theo mô-đun

| # | Mô-đun / đầu việc | US | Tuần | Phụ trách | Trạng thái (22/10) |
|---|---|---|---|---|---|
| 1 | Khảo sát thực tiễn, so sánh đối thủ, KPI nháp | | 1 | Mạnh | Xong |
| 2 | Đặc tả yêu cầu: US, BR, từ điển dữ liệu, NFR | | 2 | Mạnh | Xong, cập nhật liên tục |
| 3 | Thiết kế: kiến trúc, ERD, sơ đồ | | 3 | Mạnh | Xong |
| 4 | Dựng khung: monorepo, Docker Compose, CI 6 job, SonarQube, gitleaks | | 4 | Mạnh | Xong (PR #2-#4) |
| 5 | Xác thực JWT, đăng ký, đăng nhập | US-04, 05 | 5 | Mạnh | Xong (PR #6, #8) |
| 6 | Quản trị sản phẩm, phân loại 2 tầng | US-16 | 5-6 | Mạnh | Xong (PR #7, #8, #12) |
| 7 | Nạp danh mục mẫu Tiki | | 6 | Mạnh | Xong (PR #13, #14) |
| 8 | Trang người dùng: danh sách, tìm kiếm, chi tiết | US-01, 02, 03 | 6 | Mạnh | Xong (PR #15) |
| 9 | Giỏ hàng | US-06, 07, 08 | 6 | Mạnh | Xong (PR #16) |
| 10 | Đặt hàng, địa chỉ, lịch sử đơn, hủy đơn | US-09, 10, 11, 20 | 6 | Mạnh | Xong (PR #17) |
| 11 | Quản trị đơn hàng | US-17 | 6 | Mạnh | Xong (PR #18) |
| 12 | Thanh toán VNPay sandbox | US-10 | 6 | Mạnh | Xong (PR #19) |
| 13 | Chốt sản phẩm và metric (bản cam kết Bước 3) | | 7 | Mạnh, GVHD | Đang làm |
| 14 | Quản trị: danh sách khách hàng | US-18 | 7 | Mạnh | Chưa làm |
| 15 | Khảo sát lần đầu cho người dùng mới | US-23 | 7-8 | Mạnh | Chưa làm (đã có bảng, V2) |
| 16 | Dữ liệu cho học máy: dữ liệu mô phỏng, EDA | | 8 | Mạnh | Chưa làm |
| 17 | Phân nhóm khách hàng (RFM + K-Means) và trang quản trị | US-19 | 9 | Mạnh | Chưa làm |
| 18 | Gợi ý sản phẩm: liên quan và cá nhân hóa | US-12, 13 | 10 | Mạnh | Chưa làm (đã có bảng sự kiện, V3) |
| 19 | Kiểm thử toàn diện, coverage, hoàn thiện pipeline có deploy | | 11 | Mạnh | Chưa làm |
| 20 | Triển khai và thực nghiệm ≥ 10 người dùng (SUS) | | 12-13 | Mạnh | Chưa làm |
| 21 | Luận văn, slide, video demo | | 13-15 | Mạnh | Đang viết luận văn trên Google Doc |
| — | Ngoài cam kết: wishlist, đánh giá, mã giảm giá | US-14, 15, 21, 22 | nếu còn thời gian | Mạnh | Chưa làm |

## 3. Tỷ lệ đóng góp

| Thành viên | Vai trò | Tỷ lệ |
|---|---|---|
| Đoàn Ngọc Mạnh | Toàn bộ: phân tích, thiết kế, lập trình, kiểm thử, triển khai, viết báo cáo | 100% |

Việc dùng công cụ AI (Claude) được kê khai trong AI_LOG.MD, theo TC2.3.
