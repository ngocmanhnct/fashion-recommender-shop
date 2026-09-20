# CLAUDE.md — Website bán hàng thời trang tích hợp ML

Repo: fashion-recommender-shop — tiểu luận chuyên ngành (IT/Công nghệ phần mềm, HCMUTE), 1 người làm, 15 tuần (10/09/2026–23/12/2026), GVHD: thầy Nguyễn Minh Đạo.

## Kiến trúc & stack

Monorepo, 3 service \+ 1 CSDL, triển khai qua Docker Compose (1 VPS, không Kubernetes):

- `backend/` — Spring Boot **4.1.1** (Java 21, không phải 3.x — nhiều tên dependency đã đổi, xem "Điểm dễ nhầm" bên dưới), kiến trúc **layered** (package-by-layer, không phải package-by-feature — quyết định có chủ đích, khớp toàn bộ tài liệu đặc tả): `controller` (REST, không chứa business logic) → `service` (business logic, `@Transactional`) → `repository` (Spring Data JPA) → MySQL. Cộng thêm `entity`, `dto` (không để entity lộ thẳng ra API), `security` (JWT), `exception` (+ `@ControllerAdvice`), `client` (gọi FastAPI/VNPay), `config`.  
- `frontend/` — React. Cấu trúc: `pages/` (1 file \= 1 route), `components/`, `hooks/` (tiền tố `use`), `services/` (bọc REST API), `context/`.  
- `ml-service/` — FastAPI (Python). 2 module: Recommendation (content-based filtering, đọc-only) và Segmentation (RFM \+ K-Means, đọc+ghi `account_segment`).  
- MySQL **8.4**, schema quản lý hoàn toàn bằng **Flyway** — không bao giờ để Hibernate tự tạo/sửa bảng (`spring.jpa.hibernate.ddl-auto=validate`, luôn luôn).

**React không bao giờ gọi thẳng FastAPI** — mọi request kể cả xin gợi ý sản phẩm đều qua Spring Boot (`MLServiceClient` gọi nội bộ sang FastAPI qua mạng Docker). Đây là quyết định kiến trúc đã chốt, không phải thiếu sót.

## Nguồn tham khảo đầy đủ

- `docs/dac-ta-yeu-cau.md` — đặc tả đầy đủ: 22 user story, 36 business rule, **từ điển dữ liệu 17 bảng** (Mục 3 — nguồn duy nhất đáng tin cho tên bảng/cột/kiểu/ràng buộc, ERD ở Mục 8 chỉ là bản đơn giản hóa). Đọc mục này trước khi tạo/sửa bất kỳ `@Entity` nào.  
- `docs/coding-convention.md` — quy ước đặt tên, cấu trúc thư mục, cấu hình Checkstyle/Spotless/ESLint/Prettier đầy đủ.

## Điểm dễ nhầm — đã tốn công phát hiện, đừng lặp lại

- Bảng `order` trong ERD/từ điển dữ liệu **thực ra tên là `orders`** trong migration SQL thật — `order` là từ khóa dành riêng của MySQL (`ORDER BY`), đổi tên ở tầng cài đặt, tài liệu đặc tả cố tình giữ tên gốc `order` vì đó là tên nghiệp vụ đúng. `order_item` không đổi (không xung đột).  
- Spring Boot 4.x đổi tên nhiều dependency so với 3.x: `spring-boot-starter-web` → `spring-boot-starter-webmvc`. Flyway cần **2 dependency riêng**: `spring-boot-starter-flyway` (bắt buộc để tự chạy migration) \+ `org.flywaydb:flyway-mysql` (module riêng cho MySQL, không tự có trong starter, không cần `<version>` vì được `spring-boot-starter-parent` quản lý).  
- Checkstyle/Spotless là plugin bên thứ ba — **bắt buộc khai `<version>` tường minh** trong `pom.xml`, không như `spring-boot-maven-plugin` (được quản lý version sẵn). Thiếu version → lỗi khó hiểu "No plugin found for prefix"/"plugin absent from the project".  
- `google-java-format` (Spotless dùng để format Java) cần cờ JVM `--add-exports` để chạy trên JDK 16+ — đã có sẵn ở `backend/.mvn/jvm.config`, đừng xóa.  
- Tiền: luôn BIGINT, đơn vị VNĐ, số nguyên — không bao giờ dùng kiểu số thực (tránh sai số làm tròn).  
- Snake\_case cho cột CSDL, camelCase cho field Java — Spring Data JPA tự ánh xạ qua `CamelCaseToUnderscoresNamingStrategy`, không cần `@Column(name=...)` thủ công trừ khi tên lệch nhiều hơn quy tắc mặc định.

## Quy ước code (tóm tắt — đầy đủ ở `docs/coding-convention.md`)

- Java: Google Java Style (Checkstyle) \+ `google-java-format` (Spotless). Chạy `mvn spotless:apply` trước khi commit (trong `backend/`). Javadoc bắt buộc cho method public ở Controller/Service.  
- React: ESLint (`recommended` \+ `react` \+ `react-hooks`, flat config, KHÔNG dùng Airbnb config) \+ Prettier (`printWidth: 100`, `singleQuote: true`, `semi: true`, `trailingComma: "es5"`).  
- Tên: PascalCase cho class/component, camelCase cho method/biến/hook (tiền tố `use`), UPPER\_SNAKE\_CASE cho hằng số.

## Lệnh hay dùng

\# Backend (chạy trong backend/)

.\\mvnw.cmd spring-boot:run

.\\mvnw.cmd spotless:apply

&nbsp;

\# MySQL (chạy ở gốc repo)

docker compose up \-d mysql

docker compose exec mysql mysql \-uroot \-p\<mật khẩu trong .env\> fashionshop \-e "SHOW TABLES;"

&nbsp;

\# Frontend (chạy trong frontend/, khi đã dựng)

npm run dev

## Trạng thái hiện tại (cập nhật thủ công khi tiến độ đổi)

Tuần 4/15 — dựng khung kỹ thuật. Đã xong: Bước 1 (khung Spring Boot backend), Bước 2 (schema MySQL 17 bảng qua Flyway \+ docker-compose cho MySQL). Chưa làm: Bước 3 (khung React), Bước 4 (khung FastAPI), Bước 5 (Docker Compose đủ 4 container), Bước 6 (GitHub Actions), Bước 7 (SonarCloud \+ gitleaks), Bước 8 (báo cáo Tuần 4). Chi tiết kế hoạch: `docs/ke-hoach-15-tuan.md`.

&nbsp;