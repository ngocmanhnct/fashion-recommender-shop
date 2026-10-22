# NHẬT KÝ SỬ DỤNG CÔNG CỤ AI (AI_LOG.MD)

> Phục vụ TC2.3 (rubric G3, G4). Quy trình kiểm soát đầu ra AI nằm ở `AI_REVIEW.MD`.

- **Sinh viên:** Đoàn Ngọc Mạnh (23110124). **Công cụ AI:** Claude (Anthropic). Tuần 1–3 ghi theo phiên bản hiển thị lúc đó (Sonnet 5); ngày 30/09 có dùng thêm Claude Code trên máy sinh viên.
- **Phạm vi:** chỉ ghi các buổi dùng AI cho mã nguồn, cấu hình, CI/CD và tài liệu kỹ thuật trong repository. Các buổi dùng AI cho nội dung báo cáo (file tiểu luận, báo cáo tuần, khảo sát) không ghi ở đây.
- **Nguồn:** tổng hợp ngày 22/10/2026 từ Mục B "AI Usage Log" của báo cáo tuần (`docs/bao-cao-tuan-01.docx` đến `-06.docx`, ghi trong từng tuần), nhật ký quyết định của dự án và lịch sử Git/PR/Issues trên GitHub. Bảng dưới là bản tóm tắt; chi tiết từng buổi xem Mục B của báo cáo tuần tương ứng.
- **Quy ước:**
  - Ngày theo giờ Việt Nam, là ngày làm việc thật.
  - Mã commit là commit trên `main` chứa phần việc đó. Sinh viên tự commit, push và merge mọi PR; AI không có quyền ghi vào repository.
  - Dòng đánh dấu (\*): các buổi ngày 22/10, chưa có trong Mục B báo cáo tuần, soạn từ lịch sử trao đổi và lịch sử Git; sẽ ghi vào báo cáo tuần kế tiếp.

## 1. Bảng nhật ký

| STT | Công cụ | Phạm vi / Tệp tin | Prompt chính sử dụng | Mã AI sinh | Phần SV tinh chỉnh | Mã Commit |
|---|---|---|---|---|---|---|
| 1 | Claude (Sonnet 5) | 13/09. Khởi tạo Git, cấu trúc monorepo, `.gitignore`, README | Hướng dẫn từng bước khởi tạo Git repository và cấu trúc monorepo; chẩn đoán lỗi terminal dựa trên log gốc | Hướng dẫn `setup-ky-thuat-tuan1.md`, nội dung `.gitignore`, README | Tự chạy lệnh trên Windows, phát hiện lỗi cú pháp PowerShell và file `.gitignore.txt` | `1492255`, `a9f417b` |
| 2 | Claude | 19/09. Bước 1–2 Tuần 4: khung Spring Boot, Checkstyle, Spotless, Flyway V1 (17 bảng), MySQL Docker | Hướng dẫn dựng khung Spring Boot theo kiến trúc phân lớp và schema MySQL qua Flyway; chẩn đoán lỗi build từ log | Hướng dẫn Bước 1–2, `V1__init_schema.sql`, `docker-compose.yml`, `application.properties` | Tự gõ và chạy; tự sửa `package-info.java` chép sai, `pom.xml` nhân đôi Spotless, cổng 3306 bị chiếm | `628b088`, `7bc75b0` |
| 3 | Claude | 23/09. Bước 3: khung React + Vite, ESLint/Prettier, trang kiểm tra kết nối | Hướng dẫn dựng khung React + Vite, cấu hình ESLint/Prettier và gọi thử API backend; xử lý lỗi phát sinh khi chạy thật | `apiClient.js`, `HealthCheckPage.jsx`, cấu hình lint, `WebConfig.java` | Chọn ESLint thay Oxlint; đổi `java.version` 25 về 21 do công cụ VS Code tự sửa | `912cffb`, `7bea9c0` |
| 4 | Claude + Claude Code | 30/09. Bước 4: khung FastAPI `ml-service` (uv, Ruff, pytest) | Hướng dẫn dựng khung FastAPI cho ml-service; soạn prompt cho Claude Code quy định nội dung từng file và các việc không được làm | `pyproject.toml`, `uv.lock`, `app/`, `tests/test_health.py` | Tự chạy lại pytest, Ruff, `git status` thay vì tin báo cáo của agent; tự commit | `221ccda`, `6d11ef1` |
| 5 | Claude | 10/10. Bước 5: Docker Compose 4 container | Thực hiện Bước 5 (Docker Compose cho 4 container); sinh viên tự thực hiện commit và push | 3 `Dockerfile`, `nginx.conf`, `docker-compose.yml`, sửa cấu hình CORS | Giữ Java 21; tự chạy `--build` và kiểm tra 4 container; tự commit, PR #2 | `8fb8727` |
| 6 | Claude | 10–11/10. Bước 6: GitHub Actions CI 4 job; bật Checkstyle chặn build | Thiết lập GitHub Actions CI cho 3 service; chọn phương án để Checkstyle chặn build khi vi phạm quy ước | `ci.yml`, sửa `pom.xml`, Javadoc, `package-info.java`, 3 README UTF-8, `coding-convention.md` | Tự tách 2 commit, PR #3 | `54e7713`, `8294b78` |
| 7 | Claude | 11/10. Bước 7: SonarQube Cloud + gitleaks | Giải thích vai trò của SonarQube Cloud; tích hợp SonarQube Cloud và gitleaks vào CI ở chế độ chỉ báo cáo | Job Sonar, job gitleaks, `sonar-project.properties` | Tự tạo organization, project, token (token không qua AI); PR #4 | `9210d6f` |
| 8 | Claude | 14/10. Flyway V2 (`user_preference`, `recommendation`, giới hạn giảm voucher) | Viết migration Flyway V2 cho các bảng sở thích, gợi ý và giới hạn giảm giá voucher | Migration V2, sửa đặc tả 3.11–3.13 | Kiểm tra log migration; PR #5 | `0337227` |
| 9 | Claude | 14/10. Đăng ký, đăng nhập JWT, phân quyền, khóa 15 phút (29 file, 28 test) | Xây dựng chức năng đăng ký, đăng nhập JWT và phân quyền; chẩn đoán lỗi 401 và cảnh báo SonarQube | `SecurityConfig`, `JwtService`, `AuthService`, `LoginAttemptService`, test | Tự thêm biến admin vào `.env`; đánh dấu False positive CSRF; PR #6 | `70e22a0` |
| 10 | Claude | 14–15/10. CRUD danh mục, thương hiệu, sản phẩm, biến thể; bộ Postman | Xây dựng API CRUD danh mục, thương hiệu, sản phẩm cho quản trị viên; cập nhật Postman collection cho mọi API mới | 33 file, 12 test; `fashion-shop.postman_collection.json` | Đặt quy tắc Postman; tự thử API; bật ruleset `main`; PR #7 | `6dd4bc2`, `f02613e` |
| 11 | Claude | 15/10. Frontend đăng nhập, đăng ký, trang quản trị (38 file) | Xây dựng giao diện đăng nhập, đăng ký và trang quản trị; chẩn đoán lỗi mất kết nối backend từ log Console | Trang, component, `AuthProvider`, file gọi API | Tự dùng thử và phát hiện lỗi 3 (Mục 2); PR #8 | `efcfa19` |
| 12 | Claude | 16/10. Sửa 3 issue SonarQube của PR #8; Flyway V3 phân loại 2 tầng, `product_event_log`, `recompute_request` | Sửa các issue SonarQube của PR #8; viết migration V3 cho phân loại 2 tầng và hàng đợi tính lại gợi ý | 2 file frontend; 23 file V3 | Đọc SonarQube rồi merge PR #11, #12 | `a82a188`, `2b3947d` |
| 13 | Claude | 16/10. Nạp dữ liệu mẫu Tiki (288 rồi 1.050 sản phẩm) | Xây dựng script nạp dữ liệu mẫu từ bộ Tiki (Kaggle); mở rộng danh mục để minh họa chức năng gợi ý rõ hơn | `build_tiki_catalog.py`, `tiki-catalog.json`, `CatalogSeedService`, test | Kiểm tra giấy phép dữ liệu; PR #13, #14 | `39647d3`, `7c033a8`, `a0d5b8d` |
| 14 | Claude | 18–21/10. Trang người dùng: danh sách, tìm kiếm, lọc, chi tiết (63 file) | Xây dựng trang người dùng: danh sách, tìm kiếm, lọc, chi tiết sản phẩm và ghi nhận lượt xem | API công khai, `ProductSpecifications`, chuẩn hóa NFC, trang người dùng | Đọc SonarQube; PR #15 | `cac704d` |
| 15 | Claude | 21/10. Giỏ hàng (35 file) | Xây dựng chức năng giỏ hàng; chẩn đoán lỗi không đăng nhập được tài khoản quản trị | API giỏ, gộp giỏ, `CartProvider`, trang giỏ | Tự xác định đúng mật khẩu admin cũ; PR #16 | `d561878` |
| 16 | Claude | 21/10. Đặt hàng COD, sổ địa chỉ, đơn của tôi; quản trị đơn hàng US-17 | Xây dựng chức năng đặt hàng COD và quản trị đơn hàng; rà soát kết quả CI và SonarQube của PR #17, #18 | API địa chỉ, đặt hàng, khóa tồn kho, quản trị đơn, trang tương ứng | PR #17, #18 | `606be67`, `4c3d70a`, `d0c7f70` |
| 17 | Claude | 21–22/10. Thanh toán VNPay sandbox, hoàn tiền, tự hủy đơn quá hạn (48 file) | Tích hợp thanh toán và hoàn tiền VNPay sandbox; chẩn đoán lỗi chứng chỉ và issue SonarQube | `VnPayClient`, `PaymentService`, `RefundService`, V4, trang thanh toán | Tự đưa khóa VNPay vào `.env` (không gửi AI); tự thanh toán thẻ test; PR #19 | `bf97222`, `515ce37` |
| 18 (\*) | Claude | 22/10. Đo độ phủ test (JaCoCo, pytest-cov); staging Azure (Caddy, HTTPS); job Deploy staging | Đo độ phủ test và triển khai staging trên Azure với CI tự động deploy; chẩn đoán lỗi deploy từ log | `ci.yml`, `deploy/staging/*`, sửa `pom.xml`, `pyproject.toml`, `sonar-project.properties` | Tự tạo máy ảo, `.env` trên máy ảo, khóa SSH và secret GitHub (khóa không qua AI); PR #22 | `d41df37`, `e42349e` |
| 19 (\*) | Claude | 22/10. Log IPN VNPay; thử thanh toán thật trên staging | Bổ sung log IPN VNPay; hướng dẫn cấu hình IPN URL và kiểm chứng giao dịch thật trên staging | 7 dòng log trong `PaymentController.java` | Tự cấu hình IPN URL, thanh toán thẻ test, gửi log PAID; PR #23 | `df22cc2` |
| 20 (\*) | Claude | 22/10. Bug tracker GitHub Issues: mẫu "Báo lỗi", 12 issue cho lỗi cũ (#25–#36) | Thiết lập GitHub Issues làm bug tracker; ghi nhận các lỗi đã gặp thành issue | `.github/ISSUE_TEMPLATE/bao-loi.yml`, sửa `CLAUDE.md`, nội dung 12 issue | Cho phép AI mở issue qua GitHub API; PR #24 | `92df83d` |
| 21 (\*) | Claude | 22/10. US-23 khảo sát lần đầu rẽ nhánh (41 file, Flyway V5) | Xây dựng chức năng khảo sát sở thích lần đầu theo dạng rẽ nhánh (US-23) | `SurveyService`, `SurveyController`, V5, trang `/survey`, test, Postman | PR #37 | `8ed1681`, `835cde3` |
| 22 (\*) | Claude | 22/10. Ảnh minh họa SVG theo danh mục | Tạo ảnh minh họa SVG theo danh mục, các sản phẩm cùng danh mục cần khác nhau về hình thức | `ProductArt.jsx` (25 hình), sửa thẻ sản phẩm, chi tiết, giỏ | Nhận xét hình còn xấu, quyết định thay bằng ảnh thật tự tải ở tuần sau; PR #38 | `15c780a`, `5817635` |
| 23 (\*) | Claude | 22/10. `AI_REVIEW.MD`, `README.md` mới, thư mục `database/` | Soạn quy trình kiểm soát đầu ra AI (AI_REVIEW), README mới và thư mục database | `AI_REVIEW.MD`, README, `database/README.md` | Tự chạy lệnh dump (`schema.sql`, `seed-data.sql`); PR #40 | `6fbaf84`, `2e57924`, `b5b34b7` |

## 2. Danh mục lỗi / ảo giác của AI đã phát hiện và sửa chữa

Chỉ tính lỗi lọt qua bước giao bài (sinh viên đã nhận file hoặc làm theo). Lỗi AI tự bắt trong lúc rà soát trước khi giao không tính, xem Mục B các báo cáo tuần.

**a) Lỗi trong mã nguồn, cấu hình (truy vết được bằng commit):**

1. **Bỏ qua thư mục cấu hình Maven trong `.gitignore`.** Hướng dẫn Tuần 1 do AI soạn có dòng `backend/.mvn/`, nên `jvm.config` (cờ JVM bắt buộc cho Spotless) chưa từng được commit. Nguyên nhân: AI chép mẫu `.gitignore` chung mà không xét file cấu hình cần giữ. Lỗi nằm im vì Spotless dùng cache nên chưa chạy định dạng thật. Lộ ra ngày 19/09 khi dựng backend (Bước 1–2). (Commit gây lỗi `1492255`, sửa ở `7bc75b0`.)
2. **Checkstyle không bao giờ chặn build.** Cấu hình do AI soạn để mọi quy tắc ở mức warning trong khi plugin chỉ tính error, và plugin dùng Checkstyle 9.3 cũ không đọc được Java 21. Nguyên nhân: chỉ thấy build xanh, không thử viết sai để kiểm tra. AI phát hiện khi chạy thử CI ngày 10/10. (Commit gây lỗi `628b088`, sửa ở `8294b78`; issue #25.)
3. **Gắn token vào cả request `/actuator/health`.** CORS của Actuator chặn header Authorization nên trang kiểm tra kết nối báo "Network Error" sau khi đăng nhập. Nguyên nhân: AI chỉ chạy thử trang này lúc chưa đăng nhập. Sinh viên phát hiện khi dùng thử, sửa trước khi commit. (Commit `efcfa19`; issue #26.)
4. **Gọi `run(...)` không `await` và dùng `autoFocus` ở trang quản trị.** SonarQube Cloud chấm Reliability hạng C trên PR #8. Nguyên nhân: AI chạy ESLint nhưng không có bộ quy tắc của SonarQube, và không đọc kết quả SonarQube trước khi merge. (Commit gây lỗi `efcfa19`, sửa ở `a82a188`; issue #27.)
5. **Script Tiki nhận đường dẫn đọc/ghi từ tham số dòng lệnh** (SonarQube Security: path traversal). Nguyên nhân: viết theo kiểu script tiện dùng, không xét đầu vào không tin cậy. (Commit gây lỗi `39647d3`, sửa ở `7c033a8`; issue #28.)
6. **Câu assert tự so sánh biểu thức với chính nó** nên luôn đúng, không kiểm tra được gì (SonarQube Reliability). (Commit gây lỗi `39647d3`, sửa ở `7c033a8`; issue #29.)
7. **Tên sản phẩm dạng Unicode tách dấu (NFD) làm tìm kiếm không ra.** Script dựng dữ liệu mẫu do AI viết không chuẩn hóa chữ có dấu. Phát hiện khi tự test trang tìm kiếm. (Sửa ở `cac704d`, chuẩn hóa NFC khi ghi; issue #30.)
8. **Lặp lại lỗi thiếu `await` ở sổ địa chỉ.** SonarQube bắt trên PR #17. Nguyên nhân: bài học của PR #8 chưa thành quy tắc bắt buộc. Từ đây "luôn `await run(...)`" được ghi thành quy tắc dự án. (Commit gây lỗi `606be67`, sửa ở `4c3d70a`; issue #31.)
9. **Hoàn tiền VNPay lỗi chứng chỉ "PKIX path building failed".** Sandbox VNPay không gửi chứng chỉ trung gian; AI không thử được với sandbox thật vì mạng chặn. Sinh viên phát hiện khi chạy thật; sửa bằng bật tự tải chứng chỉ trung gian trước khi commit. (Commit `bf97222`; issue #32.)
10. **Điều kiện kiểm tra `null` luôn sai ở `RefundService`** (SonarQube Reliability hạng C). (Commit gây lỗi `bf97222`, sửa ở `515ce37` dùng `Optional`; issue #33.)
11. **File đặc tả bị commit với xuống dòng CRLF**, PR hiện thay đổi cả file. Nguyên nhân: AI đổi kiểu xuống dòng khi ghi file sang máy Windows, cuối file thừa 1 ký tự CR nên Git không tự chuẩn hóa. Lỗi lặp lại ở PR #37 dù đã sửa một lần. (Gây lỗi ở `bf97222`, sửa ở `515ce37`; lặp lại ở `8ed1681`, sửa ở `835cde3`; issue #34.)
12. **SonarQube bỏ qua báo cáo độ phủ Python** vì `coverage.xml` ghi tên file trần (`__init__.py` trùng ở 2 thư mục). AI tự phát hiện khi đọc log SonarQube của PR #22, trước khi merge. (Commit gây lỗi `d41df37`, sửa ở `e42349e`; issue #35.)
13. **Hình cái ví có điều kiện 2 nhánh cùng giá trị** (`v === 1 ? 62 : 62`, SonarQube Reliability hạng C). (Commit gây lỗi `15c780a`, sửa ở `5817635`; issue #39.)

**b) Kiến thức cũ của AI:**

14. **Hướng dẫn xử lý cảnh báo trong tab "Security Hotspots"** của SonarQube Cloud (14/10), trong khi khái niệm này đã bị bỏ. Nguyên nhân: kiến thức của AI về giao diện đã cũ. Sinh viên gửi lại thông báo của SonarQube; quy tắc trong `docs/coding-convention.md` được sửa. (Commit `6dd4bc2`.)

**c) Lỗi quy trình khi làm code (không ảnh hưởng mã nguồn):**

15. **Lệnh viết cho bash/PowerShell khi sinh viên dùng cmd** (13/09: `&&`, `mkdir` nhiều thư mục; 22/10: lệnh `icacls`, `ssh` cú pháp PowerShell). Nguyên nhân: AI không hỏi trước loại terminal. Nay ghi thành quy tắc đưa bản lệnh cho cmd.
16. **AI tự push nhánh và mở PR nháp #1** trước khi sinh viên đồng ý (10/10), trái quy trình sinh viên tự làm Git; sau đó trả lời sai rằng chưa thay đổi gì trên GitHub. Đã đóng PR, đính chính câu trả lời; từ đó AI không push.
17. **Báo đã sửa nhưng file trên máy sinh viên vẫn là bản cũ** (15/10). Nguyên nhân: không đọc lại file trên máy trước khi báo. Từ đó mọi lần ghi file đều so md5.

## 3. Tổng hợp

- **Số lỗi lọt qua bước giao bài:** 17 (13 lỗi mã nguồn, cấu hình; 1 lỗi do kiến thức cũ; 3 lỗi quy trình). Rubric yêu cầu ≥ 5.
- **Ai phát hiện:** SonarQube Cloud 6 lỗi (4, 5, 6, 8, 10, 13); sinh viên tự test hoặc kiểm tra 6 lỗi (3, 7, 9, 14, 15, 17); AI tự phát hiện khi đọc lại CI, diff hoặc log 4 lỗi (2, 11, 12, 16); lộ ra khi cùng chạy lệnh dựng backend 1 lỗi (1).
- **Bài học đã thành quy tắc:**
  - Trong `AI_REVIEW.MD`: đọc kết quả SonarQube trước khi merge (R5, sau lỗi 4); AI không ghi vào repository (Nguyên tắc chung, sau lỗi 16); không đưa secret vào prompt (P1: khóa VNPay, khóa SSH, token SonarQube do sinh viên tự nhập).
  - Trong quy tắc làm việc của AI ở dự án: luôn `await run(...)` (sau lỗi 8); giữ LF cho file tài liệu (sau lỗi 11); so md5 sau mỗi lần ghi file sang máy sinh viên (sau lỗi 17); đưa lệnh cho đúng loại terminal (sau lỗi 15).
- **Đối chiếu Git:** mỗi dòng có mã commit đều kiểm tra được bằng `git show <mã>`. Danh sách issue lỗi: https://github.com/ngocmanhnct/fashion-recommender-shop/issues?q=label%3Abug
