# Website bán hàng có tích hợp học máy trong gợi ý mua hàng và phân nhóm khách hàng

- **Môn học:** Tiểu luận chuyên ngành, HCMUTE, ngành Công nghệ phần mềm
- **Sinh viên:** Đoàn Ngọc Mạnh (23110124)
- **GVHD:** ThS. Nguyễn Minh Đạo

Website thương mại điện tử (dữ liệu sản phẩm Tiki) tích hợp học máy cho hai bài toán:
- **Gợi ý mua hàng:** content-based, cập nhật gần thời gian thực theo hành vi của khách.
- **Phân nhóm khách hàng:** K-Means trên R, F, M.

| Thành phần | Công nghệ | Thư mục |
|---|---|---|
| Backend API | Spring Boot (Java 21), Flyway | `backend/` |
| Giao diện | React + Vite | `frontend/` |
| Dịch vụ học máy | FastAPI (Python, uv) | `ml-service/` |
| Cơ sở dữ liệu | MySQL 8.4 | `database/`, `backend/src/main/resources/db/migration/` |
| Triển khai | Docker Compose, GitHub Actions, Caddy | `deploy/staging/`, `.github/workflows/` |
| Tài liệu | Đặc tả, coding convention, Postman | `docs/` |
| Hồ sơ nộp Khoa | Theo checklist của Khoa | `submission_docs/` |

## 1. Môi trường triển khai thực tế

- **Staging:** https://fashionshop-staging.eastasia.cloudapp.azure.com. Mỗi lần merge vào `main`, CI tự build và deploy lên đây.
- **Giám sát:** UptimeRobot (gói miễn phí) kiểm tra `/actuator/health` (phải có `"status":"UP"`) và trang chủ staging mỗi 5 phút, gửi email khi lỗi. Trang trạng thái công khai: https://stats.uptimerobot.com/hOhfzy7MHT.
- **Tài khoản test:** tài khoản quản trị viên và tài khoản khách hàng mẫu được gửi riêng cho hội đồng. Mật khẩu không ghi trong repository để đảm bảo 0 secret bị lộ.
- **Thanh toán:** VNPay sandbox (thẻ test của VNPay, không trừ tiền thật).

## 2. Hướng dẫn chạy máy nội bộ

**Yêu cầu:** Docker Desktop, JDK 21, Node.js LTS, Python và [uv](https://docs.astral.sh/uv/).

```powershell
# 1. Cấu hình: tạo .env từ mẫu rồi điền MYSQL_ROOT_PASSWORD, JWT_SECRET, ADMIN_EMAIL, ADMIN_PASSWORD
#    (VNPAY_* chỉ cần khi thử thanh toán)
copy .env.example .env

# 2. Cơ sở dữ liệu (MySQL trong Docker, cổng 3306)
docker compose up -d mysql

# 3. Backend (cổng 8080). Lần chạy đầu, Flyway tạo toàn bộ bảng, hệ thống tự nạp
#    1.050 sản phẩm mẫu và tạo tài khoản admin theo ADMIN_EMAIL/ADMIN_PASSWORD
cd backend
.\mvnw.cmd spring-boot:run

# 4. Dịch vụ học máy (terminal khác)
cd ml-service
uv run fastapi dev app/main.py

# 5. Giao diện (terminal khác), mở địa chỉ Vite in ra (mặc định http://localhost:5173)
cd frontend
npm install
npm run dev
```

**Kiểm thử:**
- Backend: `.\mvnw.cmd verify` trong `backend/` (dùng CSDL riêng `fashionshop_test`, có báo cáo JaCoCo).
- ML service: `uv run pytest` trong `ml-service/`.
- Frontend: `npm test` trong `frontend/` (Vitest, không cần backend; `npm run test:coverage` kèm độ phủ).
- Đầu cuối (E2E): sau `docker compose up -d --build --wait`, chạy `npm run test:e2e` trong `frontend/` (Playwright: đăng nhập, giỏ hàng, đặt hàng, admin đổi trạng thái đơn; cách đặt biến admin xem `docs/coding-convention.md` Mục 2.6).
- API: import `docs/postman/fashion-shop.postman_collection.json` vào Postman.

**Lỗi hay gặp:**
- Nếu Windows có sẵn dịch vụ MySQL80 thì cổng 3306 có thể bị chiếm; tắt dịch vụ đó trước khi chạy.
- Muốn nạp lại dữ liệu mẫu từ đầu: `docker compose down -v`, sau đó làm lại bước 2 và 3. Lệnh này xóa dữ liệu local.

## 3. Danh mục nợ kỹ thuật (Technical Debt)

| Vị trí / Mô-đun | Vấn đề hiện tại | Rủi ro | Kế hoạch xử lý |
|---|---|---|---|
| Ảnh sản phẩm | Đang dùng ảnh SVG minh họa tạm (PR #38, issue #39) | Giao diện chưa giống thật khi thực nghiệm người dùng | Thay bằng ảnh thật (ảnh miễn phí tải tay theo danh mục), SVG giữ làm ảnh dự phòng |
| SonarQube Cloud | Cổng chất lượng mới chỉ để báo cáo, chưa chặn merge | Lỗi mới có thể lọt vào `main` nếu không đọc kết quả | Bắt buộc đọc kết quả trước khi merge (AI_REVIEW, R5); xem lại việc chặn merge ở giai đoạn kiểm thử |
| Phiên đăng nhập | JWT sống 24 giờ, chưa có refresh token (NFR-03) | Token lộ thì dùng được đến hết hạn | Ngoài phạm vi tiểu luận; ghi vào hướng phát triển |
| Hạ tầng staging | Máy ảo Azure for Students 4 GB, chạy bằng credit có hạn | Hết credit thì máy dừng, mất môi trường demo | Tắt máy khi không dùng; theo dõi credit trước buổi bảo vệ |
