# CLAUDE.md — Website bán hàng thời trang tích hợp ML

Repo: fashion-recommender-shop — tiểu luận chuyên ngành (IT/Công nghệ phần mềm, HCMUTE), 1 người làm, 15 tuần (10/09/2026–23/12/2026), GVHD: thầy Nguyễn Minh Đạo.

## Kiến trúc & stack

Monorepo, 3 service + 1 CSDL, triển khai qua Docker Compose (1 VPS, không Kubernetes):

- `backend/` — Spring Boot **4.1.1** (Java 21, không phải 3.x — nhiều tên dependency đã đổi, xem "Điểm dễ nhầm" bên dưới), kiến trúc **layered** (package-by-layer, không phải package-by-feature — quyết định có chủ đích, khớp toàn bộ tài liệu đặc tả):
  `controller` (REST, không chứa business logic) → `service` (business logic, `@Transactional`) → `repository` (Spring Data JPA) → MySQL. Cộng thêm `entity`, `dto` (không để entity lộ thẳng ra API), `security` (JWT), `exception` (+ `@ControllerAdvice`), `client` (gọi FastAPI/VNPay), `config`.
- `frontend/` — React + Vite (JavaScript, không TypeScript). Cấu trúc: `pages/` (1 file = 1 route), `components/`, `hooks/` (tiền tố `use`), `services/` (bọc REST API bằng axios — `apiClient.js` dùng chung, base URL đọc `VITE_API_BASE_URL`, mặc định `http://localhost:8080`), `context/`. Chưa có React Router — sẽ thêm ở Tuần 5-6, dùng gói `react-router` (v8; `react-router-dom` đã bị gỡ bỏ).
- `ml-service/` — FastAPI, Python **3.13**, quản lý bằng **uv** (`pyproject.toml` + `uv.lock` + `.python-version`; không dùng pip/`requirements.txt`). Package `app/` gồm 2 module: `recommendation/` (content-based filtering, đọc-only) và `segmentation/` (RFM + K-Means, đọc+ghi `account_segment`). Hiện mới có `GET /health` (trả `{"status": "UP"}`, cùng định dạng `/actuator/health`); ML thật làm từ Tuần 8.
- MySQL **8.4**, schema quản lý hoàn toàn bằng **Flyway** — không bao giờ để Hibernate tự tạo/sửa bảng (`spring.jpa.hibernate.ddl-auto=validate`, luôn luôn).

**React không bao giờ gọi thẳng FastAPI** — mọi request kể cả xin gợi ý sản phẩm đều qua Spring Boot (`MlServiceClient` gọi nội bộ sang FastAPI qua mạng Docker). Đây là quyết định kiến trúc đã chốt, không phải thiếu sót.

## Nguồn tham khảo đầy đủ

- `docs/dac-ta-yeu-cau.md` — đặc tả đầy đủ: 22 user story, 36 business rule, **từ điển dữ liệu 19 bảng** (Mục 3 — nguồn duy nhất đáng tin cho tên bảng/cột/kiểu/ràng buộc, ERD ở Mục 8 chỉ là bản đơn giản hóa). Đọc mục này trước khi tạo/sửa bất kỳ `@Entity` nào.
- `docs/coding-convention.md` — quy ước đặt tên, cấu trúc thư mục, cấu hình Checkstyle/Spotless (Java), ESLint/Prettier (React), Ruff/pytest/uv (Python) đầy đủ.

## Điểm dễ nhầm — đã tốn công phát hiện, đừng lặp lại

- Bảng `order` trong ERD/từ điển dữ liệu **thực ra tên là `orders`** trong migration SQL thật — `order` là từ khóa dành riêng của MySQL (`ORDER BY`), đổi tên ở tầng cài đặt, tài liệu đặc tả cố tình giữ tên gốc `order` vì đó là tên nghiệp vụ đúng. `order_item` không đổi (không xung đột).
- Tương tự, thuộc tính `rank` của lớp `Recommendation` lưu ở cột **`rank_position`** (migration V2): `RANK` là từ khóa dành riêng của MySQL 8 (hàm cửa sổ `RANK()`). Entity khai `@Column(name = "rank_position")`.
- Không bao giờ sửa file migration đã chạy (`V1__...`): Flyway so checksum, sửa file cũ làm mọi máy đã migrate lỗi khi khởi động. Thay đổi lược đồ luôn là file `V<n+1>__...sql` mới.
- Spring Boot 4.x đổi tên nhiều dependency so với 3.x: `spring-boot-starter-web` → `spring-boot-starter-webmvc`. Flyway cần **2 dependency riêng**: `spring-boot-starter-flyway` (bắt buộc để tự chạy migration) + `org.flywaydb:flyway-mysql` (module riêng cho MySQL, không tự có trong starter, không cần `<version>` vì được `spring-boot-starter-parent` quản lý).
- Checkstyle/Spotless là plugin bên thứ ba — **bắt buộc khai `<version>` tường minh** trong `pom.xml`, không như `spring-boot-maven-plugin` (được quản lý version sẵn). Thiếu version → lỗi khó hiểu "No plugin found for prefix"/"plugin absent from the project".
- `google-java-format` (Spotless dùng để format Java) cần cờ JVM `--add-exports` để chạy trên JDK 16+ — đã có sẵn ở `backend/.mvn/jvm.config`, đừng xóa.
- Checkstyle chỉ chặn được build nhờ `<violationSeverity>warning</violationSeverity>` trong `pom.xml`: mọi quy tắc của `google_checks.xml` ở mức warning, còn mặc định plugin chỉ tính mức error. Xóa dòng đó thì vi phạm chỉ còn là cảnh báo, CI vẫn xanh. `pom.xml` cũng chỉ định Checkstyle 14.3.0 vì bản 9.3 mặc định của plugin không đọc được cú pháp Java 21. Checkstyle chạy ở pha `verify`, không chạy khi `spring-boot:run`; kiểm tra nhanh không cần MySQL: `.\mvnw.cmd spotless:check checkstyle:check`.
- Google Style (Checkstyle chặn thật) đòi Javadoc cho **mọi** class và method/constructor public (trừ `@Override`, `@Test`, getter/setter), không riêng Controller/Service — chỗ khác 1 dòng là đủ. Tên có chữ viết tắt viết như 1 từ: `MlServiceClient`, `VnPayClient`, `JwtFilter`. Tài liệu đặc tả/báo cáo ghi `MLServiceClient`, `VNPayClient` là tên khái niệm; lớp Java thật thì đặt theo kiểu trên, đặt `MLServiceClient` là CI đỏ (`AbbreviationAsWordInName`).
- `backend/mvnw` trong git **không có quyền thực thi** (commit từ Windows) — trên Linux (CI, `backend/Dockerfile`) luôn gọi `sh ./mvnw`, đừng đổi thành `./mvnw` (lỗi "Permission denied").
- CI ở `.github/workflows/ci.yml`, chạy trên mỗi PR vào `main` và mỗi commit lên `main`, 6 job: backend (Spotless, rồi `verify` = build + test + Checkstyle, có 1 MySQL 8.4 tạm vì test khởi động cả Spring context), frontend (Prettier, ESLint, build), ml-service (Ruff, pytest), build thử 3 Docker image, SonarQube Cloud (phân tích tĩnh), gitleaks (quét secret). Lệnh trong CI giống lệnh chạy tay ở dưới. `astral-sh/setup-uv` không có tag rút gọn kiểu `@v10`, phải ghi đủ phiên bản.
- SonarQube Cloud (tên mới của SonarCloud) dùng gói Free: chỉ phân tích `main` và PR vào `main`, cổng chất lượng "Sonar way" cố định đòi coverage code mới từ 80%. Coverage đến Tuần 11 mới có, nên cổng chỉ để báo cáo: đừng thêm `sonar.qualitygate.wait=true` hay bắt buộc check "SonarCloud Code Analysis" khi chưa có coverage, nếu không mọi PR đều đỏ. Automatic Analysis trên SonarQube Cloud phải **tắt** (project → Administration → Analysis Method), bật song song là job CI đỏ. Java được phân tích qua file `.class` nên job CI biên dịch backend trước khi quét; thêm thư mục mã nguồn/test mới thì sửa `sonar-project.properties` (ghi thư mục không tồn tại là lần quét lỗi).
- Token SonarQube Cloud chỉ nằm trong secret `SONAR_TOKEN` của repo, không bao giờ ghi vào file (gitleaks có quy tắc riêng bắt token Sonar). Token hết hạn thì job SonarQube Cloud đỏ: tạo token mới rồi cập nhật secret.
- gitleaks quét **toàn bộ lịch sử Git** (checkout `fetch-depth: 0`), nên secret lỡ commit rồi xóa ở commit sau vẫn làm CI đỏ. Gặp secret thật thì coi như đã lộ: (1) vô hiệu ngay ở nơi cấp (token SonarQube Cloud: My Account → Access Tokens → **Revoke**, vì tạo token mới KHÔNG làm token cũ mất hiệu lực; mật khẩu, khóa: đổi giá trị), (2) tạo cái mới, cập nhật GitHub Secret hoặc `.env`, (3) sau đó mới thêm fingerprint gitleaks in ra vào `.gitleaksignore`. Không viết lại lịch sử `main`.
- gitleaks (quy tắc mặc định) chỉ bắt token có định dạng riêng (GitHub, AWS, Sonar...) và chuỗi ngẫu nhiên đứng sau từ khóa như password/secret/token/key. Mật khẩu người tự đặt (`Manh@2004`, `hcmute2026`), chuỗi chỉ có chữ, hay chứa từ thông dụng (`password`, `only`, `shop`...) đều **không** bị bắt, nên `ci_only_password` trong `ci.yml` qua được. Nó cũng không quét `.docx`, ảnh, `package-lock.json`, `mvnw`. gitleaks không thay được quy tắc: secret thật (JWT, VNPay, mật khẩu MySQL) chỉ nằm ở `.env` hoặc GitHub Secrets; báo cáo `.docx` tự kiểm tra trước khi commit. Khi thêm khóa JWT, VNPay (Tuần 5+), cân nhắc `.gitleaks.toml` (`[extend] useDefault = true`) kèm quy tắc riêng cho tên thuộc tính đó.
- Xác thực (Bước 2 Tuần 5): Spring Security + JWT qua `spring-boot-starter-security-oauth2-resource-server` (tên starter của Spring Boot 4; Nimbus có sẵn, không dùng jjwt). Không có lớp `JwtFilter` tự viết: bộ lọc `BearerTokenAuthenticationFilter` dựng sẵn đóng vai trò đó. JWT ký HS256, claim `sub` = account_id, `role` = CUSTOMER/ADMIN, hết hạn 24 giờ. Mọi đường dẫn `/api/admin/**` chỉ cho ADMIN, đặt trong `security/SecurityConfig.java`; API công khai mới (vd xem sản phẩm) phải thêm `permitAll` ở đó, nếu không sẽ trả 401.
- `JWT_SECRET` để trống thì backend tự sinh khóa ngẫu nhiên mỗi lần khởi động (có dòng WARN trong log): mọi token cũ mất hiệu lực sau khi khởi động lại, frontend phải đăng nhập lại. Đặt `JWT_SECRET` (≥32 ký tự) trong `.env` để giữ phiên qua các lần khởi động; ngắn hơn 32 ký tự thì backend không chạy.
- Tài khoản Quản trị viên không đăng ký được qua API (BR-04): đặt `ADMIN_EMAIL`, `ADMIN_PASSWORD` trong `.env`, backend tạo lúc khởi động nếu email đó chưa có tài khoản (không ghi đè mật khẩu nếu đã có).
- Bộ đếm đăng nhập sai (5 lần/15 phút → khóa 15 phút) nằm trong bộ nhớ backend: khởi động lại là xóa hết. Đúng khi chỉ chạy 1 instance backend.
- Lỗi API trả theo chuẩn ProblemDetail (RFC 9457): `{status, title, detail}`, lỗi validate có thêm `errors` (tên trường → thông báo). Thêm exception mới thì khai handler trong `exception/GlobalExceptionHandler.java`.
- `java.version` trong `pom.xml` giữ **21**. Máy dev hiện dùng JDK 24 — biên dịch được cho 21, KHÔNG biên dịch được cho 25. Đừng nâng `java.version` theo JDK trên máy.
- CORS có 2 chỗ tách biệt: `config/WebConfig.java` chỉ áp dụng cho controller thường; endpoint Actuator (`/actuator/*`) có CORS riêng qua `management.endpoints.web.cors.*` trong `application.properties`. Cả 2 dùng chung danh sách origin `app.cors.allowed-origins` (biến môi trường `CORS_ALLOWED_ORIGINS`).
- Frontend dùng ESLint 10 — **chưa dùng `eslint-plugin-react`** vì bản ổn định mới nhất chưa hỗ trợ ESLint 10 (cài sẽ lỗi ERESOLVE). Đừng ép cài bằng `--legacy-peer-deps`.
- Trang React hiện "Network Error" cho **cả** lỗi CORS lẫn backend không chạy — đọc Console trình duyệt mới phân biệt được.
- Mỗi service chỉ chạy ở đúng 1 nơi (không vừa IntelliJ vừa terminal) — tránh "Port 8080 was already in use". IntelliJ có thể dùng JDK riêng: trước khi commit, kiểm tra bằng `.\mvnw.cmd clean spring-boot:run`.
- Trong Docker Compose, container gọi nhau bằng **tên service** (`mysql`, `ml-service`), không phải `localhost`. Backend đọc `DB_HOST`, `ML_SERVICE_URL`, `CORS_ALLOWED_ORIGINS` từ biến môi trường (mặc định khớp lúc chạy trên máy), nên cùng 1 `application.properties` chạy được cả 2 nơi.
- `VITE_API_BASE_URL` được Vite đóng cứng vào JS **lúc build**, không đọc lúc chạy — đổi địa chỉ backend cho frontend Docker phải sửa build arg trong `docker-compose.yml` rồi `--build` lại.
- Chạy cả stack bằng Docker thì tắt backend/ml-service đang chạy trên máy trước (trùng cổng 8080; MySQL 3306 dùng chung được).
- `ml-service` **không cấu hình CORS** — cố ý: trình duyệt không bao giờ gọi FastAPI (Spring Boot gọi nội bộ, server gọi server).
- Chạy FastAPI bằng `uv run fastapi dev app/main.py` (trong `ml-service/`). Chạy thẳng `python app/main.py` hay nút ▶ của VS Code không bật server nào — `app` chỉ là đối tượng, server là uvicorn. Đừng thêm `uvicorn.run()` vào `main.py`.
- Test FastAPI cần `httpx2` (TestClient của Starlette đã bỏ `httpx`) và `pythonpath = ["."]` trong `[tool.pytest.ini_options]` — cả hai đã có sẵn, đừng xóa.
- Tiền: luôn BIGINT, đơn vị VNĐ, số nguyên — không bao giờ dùng kiểu số thực (tránh sai số làm tròn).
- Snake_case cho cột CSDL, camelCase cho field Java — Spring Data JPA tự ánh xạ qua `CamelCaseToUnderscoresNamingStrategy`, không cần `@Column(name=...)` thủ công trừ khi tên lệch nhiều hơn quy tắc mặc định.

## Quy ước code (tóm tắt — đầy đủ ở `docs/coding-convention.md`)

- Java: Google Java Style (Checkstyle) + `google-java-format` (Spotless). Chạy `.\mvnw.cmd spotless:apply` trước khi commit (trong `backend/`). Javadoc bắt buộc cho mọi class và method public (Checkstyle chặn); Controller/Service viết đầy đủ, chỗ khác 1 dòng.
- React: ESLint 10 flat config (`recommended` + `react-hooks` + `react-refresh` + `eslint-config-prettier` đặt cuối; KHÔNG dùng Airbnb config) + Prettier (`printWidth: 100`, `singleQuote: true`, `semi: true`, `trailingComma: "es5"`). Chạy `npx prettier --write .` và `npx eslint .` (trong `frontend/`) trước khi commit.
- Python: PEP 8 + Ruff (`line-length = 100`, thêm rule sắp xếp import `I`); docstring cho mọi package (`__init__.py`) và hàm endpoint. Chạy `uv run ruff format .` và `uv run ruff check .` (trong `ml-service/`) trước khi commit.
- Tên: Java/React — PascalCase cho class/component, camelCase cho method/biến/hook (tiền tố `use`), UPPER_SNAKE_CASE cho hằng số; Python — snake_case cho hàm/biến/module, PascalCase cho class.

## Lệnh hay dùng

Mỗi công cụ tìm 1 file riêng ngay trong thư mục đang đứng: `docker compose` và `git` → gốc repo; `mvnw` → `backend/`; `npm`/`npx` → `frontend/`; `uv` → `ml-service/`.

```powershell
# Backend (chạy trong backend/)
.\mvnw.cmd spring-boot:run
.\mvnw.cmd clean spring-boot:run   # build lại từ đầu, giống CI
.\mvnw.cmd spotless:apply
.\mvnw.cmd verify                  # như CI: build + test + Checkstyle (cần MySQL đang chạy)

# Cả 4 container (chạy ở gốc repo): frontend http://localhost:3000, backend :8080
docker compose up -d --build
docker compose ps                                           # cả 4 phải Up, 3 cái có (healthy)
docker compose exec backend curl -s http://ml-service:8000/health   # backend gọi ml-service
docker compose logs -f backend
docker compose down                                         # thêm -v là XÓA luôn dữ liệu MySQL

# Chỉ MySQL, còn 3 service chạy trên máy để code (chạy ở gốc repo)
docker compose up -d mysql
docker compose exec mysql mysql -uroot -p<mật khẩu trong .env> fashionshop -e "SHOW TABLES;"

# Quét secret bằng gitleaks qua Docker (chạy ở gốc repo). Image ghim theo digest, chỉ đọc, không mạng.
# Sau `git add`, trước `git commit`: quét phần đã stage (file chưa commit không được lệnh thứ 2 thấy)
docker run --rm --network none -v "${PWD}:/repo:ro" zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f git --staged /repo --no-banner --redact -v
# Giống CI: toàn bộ lịch sử Git, mọi nhánh trên máy
docker run --rm --network none -v "${PWD}:/repo:ro" zricethezav/gitleaks:v8.30.1@sha256:c00b6bd0aeb3071cbcb79009cb16a60dd9e0a7c60e2be9ab65d25e6bc8abbb7f git /repo --no-banner --redact -v

# Frontend (chạy trong frontend/)
npm run dev
npx prettier --write .
npx eslint .

# ML service (chạy trong ml-service/)
uv sync                          # sau khi clone: tạo .venv đúng phiên bản trong uv.lock
uv run fastapi dev app/main.py   # http://localhost:8000/health — tài liệu API: /docs
uv run pytest
uv run ruff format .
uv run ruff check .
```

## Trạng thái hiện tại (cập nhật thủ công khi tiến độ đổi)

Tuần 4/15 — dựng khung kỹ thuật. Đã xong: Bước 1 (khung Spring Boot backend), Bước 2 (schema MySQL 17 bảng qua Flyway + docker-compose cho MySQL), Bước 3 (khung React + Vite, trang test gọi `/actuator/health` qua CORS), Bước 4 (khung FastAPI `ml-service` với uv + Ruff + pytest, endpoint `/health`), Bước 5 (Docker Compose đủ 4 container: mỗi service có `Dockerfile`, frontend phục vụ bằng Nginx ở cổng 3000, `ml-service` không mở cổng ra host và giới hạn 1 CPU/1 GB RAM), Bước 6 (GitHub Actions: `.github/workflows/ci.yml`; từ đây mọi thay đổi vào `main` đi qua PR và phải xanh CI), Bước 7 (SonarQube Cloud + gitleaks thành 2 job CI, cấu hình Sonar ở `sonar-project.properties`). Chưa làm: Bước 8 (báo cáo Tuần 4). Chi tiết kế hoạch: `docs/ke-hoach-15-tuan.md`.
