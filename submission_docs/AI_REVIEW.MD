# AI_REVIEW: QUY TRÌNH KIỂM SOÁT ĐẦU RA CỦA AI

> Phục vụ TC2.3 Mức 5: "quy trình kiểm soát đầu ra AI thành hệ thống: checklist review, đối chiếu tài liệu chính thức, kiểm tra giấy phép mã nguồn và dữ liệu nhạy cảm trong prompt". Nhật ký chi tiết từng lần dùng AI nằm ở `AI_LOG.MD`.

- **Công cụ AI sử dụng:** Claude (Anthropic), dùng cho phân tích, viết tài liệu, sinh mã và review.
- **Nguyên tắc chung:** Mọi thay đổi vào `main` phải qua Pull Request, qua 6 job CI và review theo checklist dưới đây.
  - Đến hết 30/10: AI chỉ đề xuất; sinh viên tự commit, push, review và merge; AI không có quyền ghi vào repository.
  - Từ 31/10: sinh viên giao cho AI quyền push, mở PR và merge dưới tài khoản của sinh viên, kèm các điều kiện ở Mục 6.

## 1. Bảo mật trong prompt (trước khi gửi cho AI)

| # | Quy tắc | Cách thực hiện trong dự án |
|---|---|---|
| P1 | Không đưa secret vào prompt: mật khẩu MySQL, `JWT_SECRET`, `VNPAY_HASH_SECRET`, khóa SSH staging, token GitHub/SonarQube | Secret chỉ nằm trong `.env` (đã gitignore), GitHub Secrets và `~/fashionshop/.env` trên máy ảo staging. Khi cần AI hỗ trợ cấu hình, chỉ đưa file mẫu `.env.example` |
| P2 | Không đưa dữ liệu cá nhân thật | Dữ liệu khảo sát đưa vào dự án đã bỏ cột email (`survey_data/khao-sat-phieu-tra-loi.csv`). Dữ liệu sản phẩm lấy từ bộ Tiki công khai |
| P3 | Chỉ đưa đúng phần mã cần thiết | Mỗi phiên làm một bước nhỏ, gắn với một nhánh và một PR |
| P4 | Nếu lỡ đưa secret vào prompt hoặc commit | Thu hồi và tạo secret mới ngay, sau đó ghi vào AI_LOG |

## 2. Checklist review mã do AI sinh (mỗi Pull Request)

| # | Hạng mục | Kiểm tra | Công cụ, minh chứng |
|---|---|---|---|
| R1 | Hiểu từng dòng | Tự giải thích được mọi đoạn AI viết; đoạn nào chưa hiểu thì hỏi lại hoặc viết lại | Phục vụ vấn đáp ≥ 5 vị trí ngẫu nhiên |
| R2 | Đúng nghiệp vụ | Đối chiếu với US, tiêu chí chấp nhận (AC) và business rule (BR) trong đặc tả | `docs/dac-ta-yeu-cau.md` |
| R3 | Build và test | 6 job CI xanh: Backend, Frontend, ML service, Docker images, SonarQube Cloud, gitleaks | Lịch sử GitHub Actions |
| R4 | Ca biên và ca âm | Có test cho dữ liệu rỗng, sai định dạng, thiếu token, vượt tồn kho | Test JUnit (unit, integration), pytest; Postman collection cho API |
| R5 | Phân tích tĩnh | Đọc kết quả SonarQube của PR (New issues, Security, Reliability) **trước khi merge**, không chỉ nhìn dấu xanh | SonarQube Cloud |
| R6 | Định dạng và quy tắc | Spotless, Checkstyle, Prettier, ESLint, Ruff đều qua | `docs/coding-convention.md` |
| R7 | Secret | gitleaks không báo lộ khóa; không có giá trị thật trong mã hoặc file cấu hình | Job gitleaks |
| R8 | Tài liệu đi kèm | API mới hoặc thay đổi thì cập nhật Postman collection; schema mới thì thêm migration Flyway và cập nhật từ điển dữ liệu | `docs/postman/`, `db/migration/` |

## 3. Đối chiếu tài liệu chính thức

Mỗi khi AI dùng một thư viện, cấu hình hoặc API, sinh viên kiểm tra lại trên tài liệu chính thức của đúng phiên bản đang dùng:
- Spring Boot, Spring Security
- React và react-router
- FastAPI, scikit-learn
- Flyway, MySQL 8.4
- tài liệu tích hợp VNPay sandbox

Nếu AI dùng hàm không tồn tại hoặc cú pháp của phiên bản cũ, sinh viên sửa theo tài liệu chính thức và ghi lỗi vào AI_LOG.

## 4. Giấy phép mã nguồn và dữ liệu

| # | Kiểm tra | Cách thực hiện |
|---|---|---|
| L1 | Thư viện bên thứ ba | Chỉ cài qua trình quản lý gói chính thức (Maven, npm, uv), có khóa phiên bản (`package-lock.json`, `uv.lock`), giấy phép mã nguồn mở |
| L2 | Đoạn mã AI có thể chép nguyên văn từ nguồn khác | Không nhận khối mã dài không rõ nguồn; ưu tiên viết theo tài liệu chính thức |
| L3 | Dữ liệu | Bộ dữ liệu Tiki trên Kaggle chỉ dùng cho mục đích học tập, giấy phép ghi tại `data/tiki/README.md`; dữ liệu gốc không đưa vào git |

## 5. Ví dụ checklist đã bắt được lỗi của AI

| PR | Lỗi do AI sinh | Bước phát hiện | Cách sửa |
|---|---|---|---|
| #8 | Frontend gọi `run()` mà không `await` ở 2 chỗ; dùng `autoFocus` (SonarQube: Reliability C) | R5, nhưng PR đã lỡ merge trước khi đọc SonarQube | Sửa ở PR #11; từ đó R5 bắt buộc đọc **trước khi merge** |
| #13 | Script nhận đường dẫn đọc/ghi từ tham số dòng lệnh (SonarQube Security: path traversal); một test so sánh một biểu thức với chính nó (Reliability) | R5, R4 | Cố định đường dẫn theo gốc repo; test so với giá trị hằng. Sửa trên cùng nhánh trước khi merge |

Danh sách đầy đủ ≥ 5 lỗi AI kèm phân tích nguyên nhân và commit sửa nằm ở `AI_LOG.MD`.

## 6. Giai đoạn AI được ghi vào repository (từ 31/10/2026)

Ngày 31/10, sinh viên cho phép Claude Code (chạy trên môi trường cloud) tự tạo nhánh, commit, push, mở PR, mở issue và merge, để kịp các chức năng còn lại trong bản cam kết. Commit ghi tên tác giả "Ngoc Manh" theo yêu cầu của sinh viên; việc AI làm phần nào được kê khai ở `AI_LOG.MD` (dòng 36 trở đi).

| # | Điều kiện | Cách thực hiện |
|---|---|---|
| A1 | Sinh viên quyết định phạm vi | Mỗi chức năng bắt đầu từ yêu cầu của sinh viên; khi có nhiều cách làm, AI đưa các phương án đánh số kèm khuyến nghị, sinh viên chọn |
| A2 | Chỉ merge khi qua cổng tự động | Đủ 6 job CI xanh (Backend, Frontend, ML service, Docker images, SonarQube Cloud, gitleaks), SonarQube 0 issue mới; AI đọc log CI và kết quả SonarQube trước khi merge (R3, R5) |
| A3 | Mỗi lỗi một issue | Lỗi CI, SonarQube hay tổng duyệt bắt được đều mở issue theo mẫu, PR sửa ghi `Fixes #n` |
| A4 | Không đưa secret cho AI | Giữ nguyên P1: khóa VNPay, mật khẩu Gmail, mật khẩu admin, khóa SSH do sinh viên tự đặt trong `.env` và GitHub Secrets |
| A5 | Rà soát độc lập định kỳ | 01–03/11: tổng duyệt bằng 3 agent rà code và 1 agent đóng vai hội đồng; tìm 17 lỗi (#93–#106, #108, #109, #111), trong đó có lỗi rò rỉ nhãn khi đánh giá gợi ý (#99) |
| A6 | Sinh viên kiểm chứng sau | Sinh viên dùng thử trên staging và tự giải thích được các phần quan trọng trước buổi bảo vệ (R1). Ngày 04–05/11 sinh viên tự dùng thử và tìm ra 3 lỗi (#124 khảo sát gợi ý sai giới tính, #130 ảnh trùng, #132 thiếu lựa chọn màu) cùng luồng đánh giá sản phẩm quá rườm rà (sửa ở PR #134) |
| A7 | Cập nhật thư viện không tự động merge | PR Dependabot chỉ merge từng cái sau khi CI xanh trên bản đã gộp `main` mới nhất; PR làm đỏ CI thì đóng và ghi lý do (04/11: merge 8 PR, đóng #116 nâng Java 25 vì healthcheck backend báo unhealthy, chặn đề xuất Java lớn hơn 21 ở PR #128) |

Rủi ro của giai đoạn này: sinh viên không đọc từng dòng trước khi merge, nên R1 được bù bằng A5, A6 và bộ câu hỏi ôn tập về các PR #68–#110. Các lỗi AI gây ra trong giai đoạn này và cách phát hiện ghi ở `AI_LOG.MD` Mục 2 (d) và (e).
