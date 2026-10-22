# AI_REVIEW: QUY TRÌNH KIỂM SOÁT ĐẦU RA CỦA AI

> Phục vụ TC2.3 Mức 5: "quy trình kiểm soát đầu ra AI thành hệ thống: checklist review, đối chiếu tài liệu chính thức, kiểm tra giấy phép mã nguồn và dữ liệu nhạy cảm trong prompt". Nhật ký chi tiết từng lần dùng AI nằm ở `AI_LOG.MD`.

- **Công cụ AI sử dụng:** Claude (Anthropic), dùng cho phân tích, viết tài liệu, sinh mã và review.
- **Nguyên tắc chung:** AI chỉ đề xuất. Mọi thay đổi vào `main` phải qua Pull Request, qua 6 job CI và được sinh viên tự review theo checklist dưới đây. Sinh viên tự commit, push và merge; AI không có quyền ghi vào repository.

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
