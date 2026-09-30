---
title: Coding Convention — Java (Spring Boot), React & Python (FastAPI)
description: Quy ước code dùng cho toàn bộ dự án, thực thi tự động qua Checkstyle/Spotless (Java), ESLint/Prettier (React) và Ruff (Python) trong pipeline CI
---

# Coding Convention

## 0. Mục đích & phạm vi

Đáp ứng mục cuối của TC2.1 Mức 5 (`dac-ta-yeu-cau.md` Mục 12 — yêu cầu phi chức năng): *"Viết coding convention thành văn bản"* (`ke-hoach-15-tuan.md` Tuần 3). Tài liệu này không chỉ mô tả — mọi quy tắc dưới đây đều được **thực thi tự động** qua công cụ đã chọn sẵn ở `ho-so-du-an.md` Mục 3 (Checkstyle, Spotless, ESLint, Prettier; Ruff cho Python — bổ sung 30/09/2026), chạy trong bước "lint" của pipeline GitHub Actions (`build → lint → test → quét bảo mật → đóng gói → deploy`) — vi phạm quy ước làm CI đỏ, không phải chỉ một khuyến nghị suông đọc rồi bỏ qua.

Áp dụng từ Tuần 4 (`ke-hoach-15-tuan.md`, lúc khởi tạo 3 service) — các đoạn code mẫu trong `dac-ta-yeu-cau.md` (Mục 9 sequence diagram) đã đặt tên class đúng theo quy ước ở Mục 1 dưới đây (`OrderController`, `AuthService`, `ProductVariantRepository`...), nên khi code thật sẽ khớp thẳng, không cần đổi tên lại.

---

## 1. Java — Spring Boot (Backend)

### 1.1 Cấu trúc package

Theo layer, đúng kiến trúc layered đã chốt (`ho-so-du-an.md` Mục 3) — cũng là cấu trúc đã vẽ ở component diagram (`dac-ta-yeu-cau.md` Mục 10, Hình 7):

```
com.manhdn.fashionshop
├── controller/       # REST endpoints — @RestController, nhận request, gọi service, không chứa business logic
├── service/          # Business logic — nơi các UPDATE có điều kiện (BR-08, BR-30...), transaction (@Transactional)
├── repository/        # Spring Data JPA — @Repository, interface extends JpaRepository
├── entity/            # @Entity — ánh xạ 1:1 bảng CSDL, khớp từ điển dữ liệu Mục 3 dac-ta-yeu-cau.md
├── dto/               # Request/Response object — không để entity "rò rỉ" thẳng ra API
├── security/          # JWT filter, Spring Security config (Security Layer ở Hình 7)
├── exception/         # Custom exception (vd InsufficientStockException, VoucherExpiredException — đã dùng ở Hình 6b) + @ControllerAdvice xử lý tập trung
├── client/            # Gọi service ngoài — MLServiceClient (gọi FastAPI, Hình 7), VNPayClient
└── config/            # @Configuration — CORS (NFR liên quan ở Mục 11 dac-ta-yeu-cau.md), Docker profile...
```

*Vì sao package-by-layer, không package-by-feature:* khớp đúng cách kiến trúc đã được trình bày xuyên suốt tài liệu đặc tả (controller/service/repository là đơn vị tổ chức chính, không phải "feature module") — nhất quán dễ trình bày khi bảo vệ hơn là đổi cách tổ chức code so với cách đã vẽ sơ đồ.

### 1.2 Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Class, Interface | PascalCase | `OrderService`, `ProductVariantRepository` |
| Method, biến | camelCase | `createOrder()`, `idempotencyKey` |
| Hằng số | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| Package | chữ thường, không gạch dưới | `com.manhdn.fashionshop.service` |
| Cột/bảng CSDL | snake_case | `sku_code`, `order_item` (đã chốt ở Mục 3.4 `dac-ta-yeu-cau.md`) |

*Về lệch quy ước Java (camelCase) vs CSDL (snake_case):* đây là **cố ý, không phải thiếu nhất quán** — mỗi ngôn ngữ/hệ thống dùng đúng chuẩn phổ biến của nó, và Spring Data JPA tự động ánh xạ giữa 2 chuẩn này qua `CamelCaseToUnderscoresNamingStrategy` (mặc định sẵn có, không cần cấu hình tay): field Java `skuCode` tự khớp cột CSDL `sku_code`. Không cần ghi `@Column(name = "sku_code")` thủ công cho từng field trừ khi tên lệch nhiều hơn quy tắc mặc định xử lý được.

### 1.3 Checkstyle — kiểm tra quy tắc

Dùng thẳng ruleset **Google Java Style** (`google_checks.xml`, đóng gói sẵn trong Checkstyle, không tự viết ruleset riêng — tránh tốn thời gian bikeshedding quy tắc cho một dự án 1 người/15 tuần). Điểm chính: dòng ≤ 100 ký tự, thụt lề 2 space, không dùng wildcard import (`import java.util.*`), mỗi class 1 file.

```xml
<!-- pom.xml -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <configuration>
        <configLocation>google_checks.xml</configLocation>
        <failOnViolation>true</failOnViolation>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

### 1.4 Spotless — tự động format

Checkstyle chỉ **báo lỗi**, không tự sửa. Spotless tự **format lại** theo `google-java-format`, chạy trước khi commit hoặc trong CI:

```xml
<!-- pom.xml -->
<plugin>
    <groupId>com.diffplug.spotless</groupId>
    <artifactId>spotless-maven-plugin</artifactId>
    <configuration>
        <java>
            <googleJavaFormat/>
            <removeUnusedImports/>
        </java>
    </configuration>
</plugin>
```

Lệnh dùng hàng ngày: `mvn spotless:apply` (tự sửa format trước khi commit) — CI chạy `mvn spotless:check` (chặn merge nếu có file chưa format).

### 1.5 Javadoc

Bắt buộc cho method **public** ở Controller và Service (đây là "hợp đồng" người khác/hội đồng đọc để hiểu API và business logic mà không cần đọc hết code) — không bắt buộc cho method private hay Repository (interface JPA đã tự mô tả qua tên method, vd `findByIdempotencyKey`).

---

## 2. React (Frontend)

### 2.1 Cấu trúc thư mục

```
src/
├── pages/          # 1 file = 1 trang/route (LoginPage.jsx, CheckoutPage.jsx)
├── components/     # Component dùng lại nhiều nơi (ProductCard, CartItem)
├── hooks/          # Custom hook (useCart, useAuth)
├── services/       # Gọi REST API — mỗi file bọc đúng 1 nhóm endpoint đã định nghĩa ở
│                   # dac-ta-yeu-cau.md Mục 9 (authApi.js → POST /api/auth/login,
│                   # orderApi.js → POST /api/orders...)
└── context/        # React Context (vd AuthContext lưu JWT — hết hạn 24h, NFR-03)
```

### 2.2 Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Component (file + tên) | PascalCase | `ProductCard.jsx` |
| Custom hook | camelCase, tiền tố `use` | `useCart.js` |
| Hàm, biến thường | camelCase | `handleSubmit` |
| Hằng số | UPPER_SNAKE_CASE | `MAX_CART_ITEMS` |

### 2.3 ESLint

Dùng **ESLint 10**, cấu hình dạng **flat config** (`frontend/eslint.config.js`) — đúng cấu hình Vite tự sinh khi khởi tạo dự án (chọn ESLint thay vì Oxlint mặc định), cộng thêm phần nối với Prettier. Gồm 4 phần:

- `eslint:recommended` — bộ rule lõi, đã gồm `no-unused-vars` mức error.
- `eslint-plugin-react-hooks` — bắt lỗi dùng hook sai.
- `eslint-plugin-react-refresh` — cảnh báo file vừa export component vừa export thứ khác (Vite không hot-reload được file đó, phải tải lại cả trang, mất state đang có).
- `eslint-config-prettier` — tắt các rule định dạng có thể xung đột với Prettier; đặt **cuối cùng** để ghi đè các phần trên.

**Không dùng Airbnb config**: bộ quy tắc Airbnb rất chi tiết nhưng thường cần chỉnh sửa nhiều mới hợp với từng dự án — không đáng chi phí cấu hình/bảo trì thêm cho một người làm trong 15 tuần, trong khi các bộ `recommended` trên đã đủ bắt các lỗi quan trọng (hook dùng sai, biến không dùng, biến chưa khai báo, đoạn code không bao giờ chạy tới...).

```js
// frontend/eslint.config.js
import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import eslintConfigPrettier from 'eslint-config-prettier/flat';
import { defineConfig, globalIgnores } from 'eslint/config';

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{js,jsx}'],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
  },
  eslintConfigPrettier,
]);
```

*Cập nhật 23/09/2026 (Tuần 4, lúc dựng khung React) — khác bản gốc của mục này ở 3 điểm:*

1. **Tạm chưa dùng `eslint-plugin-react`.** Bản ổn định mới nhất (7.37.5, tính tới 09/2026) chỉ khai báo hỗ trợ tới ESLint 9 (`^9.7`) — cài chung với ESLint 10 sẽ lỗi xung đột phụ thuộc (ERESOLVE); chỉ bản thử nghiệm (rc) hỗ trợ ESLint 10. Không ép cài (`--legacy-peer-deps`) hay dùng bản rc — cùng nguyên tắc tránh bản thử nghiệm. Mất ít: một phần lý do trước đây phải cài plugin này là để ESLint hiểu biến dùng trong JSX — ESLint 10 đã tự làm được; rule đáng tiếc nhất là `react/jsx-key` (quên `key` khi render danh sách), nhưng React vẫn tự cảnh báo lỗi này trong Console lúc chạy dev. Thêm lại khi có bản ổn định hỗ trợ ESLint 10 (theo dõi ở `ho-so-du-an.md` Mục 6).
2. **Thêm `eslint-plugin-react-refresh`** (mặc định của Vite) **và `eslint-config-prettier`** (bản gốc chưa nối ESLint với Prettier).
3. **Bỏ dòng `"no-unused-vars": "error"` riêng** (`eslint:recommended` đã bật sẵn ở mức error), và sửa 1 ví dụ sai của bản gốc: `eslint:recommended` **không** bắt so sánh lỏng lẻo `==` — rule `eqeqeq` không nằm trong bộ này.

### 2.4 Prettier

File `frontend/.prettierrc.json`:

```json
{
  "printWidth": 100,
  "tabWidth": 2,
  "singleQuote": true,
  "semi": true,
  "trailingComma": "es5"
}
```

*Vì sao `printWidth: 100` (khác mặc định Prettier là 80):* khớp đúng độ dài dòng đã chọn cho Java ở Checkstyle (Mục 1.3) — một chuẩn thống nhất cho cả 2 ngôn ngữ trong cùng dự án, dễ nhớ hơn 2 con số khác nhau.

---

## 3. Python — FastAPI (ml-service)

*Bổ sung 30/09/2026 (Tuần 4, lúc dựng khung `ml-service`) — bản gốc của tài liệu chỉ có Java và React. Mục này viết cùng lúc với code, theo đúng cấu hình đã chạy thật.*

### 3.1 Cấu trúc package

Theo đúng 2 module của component diagram (`dac-ta-yeu-cau.md` Mục 10):

```
ml-service/
├── app/
│   ├── __init__.py          # package gốc — docstring mô tả service
│   ├── main.py              # tạo FastAPI app, khai báo endpoint
│   ├── recommendation/      # Module gợi ý (US-12, US-13) — content-based filtering, chỉ đọc dữ liệu
│   └── segmentation/        # Module phân nhóm (US-19) — RFM + K-Means, đọc + ghi account_segment
└── tests/                   # pytest — các file test_*.py (không cần __init__.py)
```

Mỗi package có `__init__.py` kèm docstring mô tả vai trò — tương đương `package-info.java` ở Mục 1.1: vừa giữ thư mục trong Git, vừa là tài liệu thật.

### 3.2 Quy ước đặt tên (PEP 8)

| Đối tượng | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Module (file), hàm, biến | snake_case | `test_health.py`, `compute_rfm()`, `top_k` |
| Package | chữ thường, ngắn, hạn chế gạch dưới | `recommendation`, `segmentation` |
| Class | PascalCase | `SegmentResult` |
| Hằng số | UPPER_SNAKE_CASE | `DEFAULT_TOP_K` |

*Vì sao khác Java/JS (camelCase cho hàm, biến):* PEP 8 là chuẩn đặt tên chính thức của Python — cùng nguyên tắc "mỗi ngôn ngữ dùng đúng chuẩn phổ biến của nó" đã nêu ở Mục 1.2.

### 3.3 Ruff — kiểm tra lỗi + format

Python trước đây cần 3 công cụ riêng: flake8 (kiểm lỗi), black (format), isort (sắp xếp import). **Ruff** gộp cả 3 thành 1 — vai trò bằng Checkstyle + Spotless bên Java, ESLint + Prettier bên React — chạy nhanh hơn nhiều, cấu hình chung trong `pyproject.toml`:

```toml
# ml-service/pyproject.toml
[tool.ruff]
line-length = 100

[tool.ruff.lint]
extend-select = ["I"]
```

- `line-length = 100` — khớp Checkstyle (Mục 1.3) và Prettier (Mục 2.4): một chuẩn độ dài dòng cho cả 3 ngôn ngữ.
- `extend-select = ["I"]` — giữ bộ rule mặc định của Ruff và **thêm** rule sắp xếp import (`extend-select` = thêm vào bộ mặc định; `select` = thay hẳn bộ mặc định).
- Format theo mặc định của Ruff (tương thích black: nháy kép, thụt lề 4 space) — khác nháy đơn/thụt 2 space bên React là cố ý, theo chuẩn phổ biến của từng ngôn ngữ.

Lệnh dùng hằng ngày (trong `ml-service/`): `uv run ruff format .` (tự sửa format — như `spotless:apply` / `prettier --write`) và `uv run ruff check .` (kiểm lỗi; thêm `--fix` để tự sửa các lỗi sửa được, vd thứ tự import).

### 3.4 Docstring

Bắt buộc cho mọi package (`__init__.py`), cho hàm endpoint (gắn `@app.get`, `@app.post`...) và hàm public của 2 module ML — cùng lý do Javadoc ở Mục 1.5: là "hợp đồng" người đọc/hội đồng hiểu được mà không cần đọc hết code. Không bắt buộc cho hàm test và hàm nội bộ ngắn đã tự mô tả qua tên.

### 3.5 Quản lý thư viện — uv

- Thư viện khai báo trong `pyproject.toml`, khóa đủ phiên bản (kể cả thư viện gián tiếp) trong `uv.lock` — **commit cả hai**, giống `package.json` + `package-lock.json` bên frontend.
- Phiên bản Python chốt **3.13** trong `.python-version` — uv tự tải nếu máy chưa có; image Docker dùng đúng bản này.
- Thư viện chỉ dùng lúc phát triển (pytest, ruff, httpx2) nằm trong `[dependency-groups]` → `dev` — không đưa vào image chạy thật.
- Thêm thư viện bằng `uv add <tên>` (thư viện dev: `uv add --dev <tên>`), chạy lệnh bằng `uv run <lệnh>`, dựng lại môi trường sau khi clone bằng `uv sync`. **Không** dùng `pip install` trực tiếp hay `requirements.txt` — sẽ lệch khỏi `uv.lock`.

*Vì sao uv thay pip + venv:* tài liệu chính thức FastAPI hướng dẫn cài bằng uv trước; khóa phiên bản bằng lockfile; tự quản lý phiên bản Python (local và Docker khớp nhau); `uv run` không cần activate venv — tránh lỗi "running scripts is disabled" khi activate trên Windows PowerShell 5.1. Đã cân nhắc pip + venv + `requirements.txt`: dùng được, nhưng 3 việc đầu phải tự lo bằng tay.

### 3.6 Test — pytest

```toml
# ml-service/pyproject.toml
[tool.pytest.ini_options]
pythonpath = ["."]
testpaths = ["tests"]
```

- `pythonpath = ["."]` **bắt buộc**: dự án không đóng gói thành thư viện nên pytest không tự thấy package `app` — thiếu dòng này, `from app.main import app` báo `ModuleNotFoundError`.
- Test endpoint dùng `fastapi.testclient.TestClient` (gọi thẳng vào app ngay trong tiến trình test, không cần bật server). `TestClient` của Starlette đã chuyển sang thư viện **`httpx2`** (`httpx` gốc không còn bản phát hành mới từ 2024) — `httpx2` nằm trong nhóm dev; thiếu nó pytest in cảnh báo deprecated.
- Tên file `test_*.py`, tên hàm `test_...` mô tả đúng hành vi được kiểm (vd `test_health_returns_up`).

---

## 4. Thực thi trong CI/CD

*(Mục này trước là Mục 3, đổi thành Mục 4 ngày 30/09/2026 khi thêm phần Python — các tham chiếu "Mục 3" trong nhật ký 23/09/2026 của `ho-so-du-an.md` là trỏ tới mục này.)*

Các công cụ trên chạy ở bước **lint** của pipeline GitHub Actions (`ho-so-du-an.md` Mục 3), mỗi lệnh chạy trong đúng thư mục service của nó (`backend/`, `frontend/`, `ml-service/`):

| Bước | Lệnh | Chặn merge nếu |
| :---- | :---- | :---- |
| Java — kiểm tra format | `mvn spotless:check` | Có file chưa format đúng |
| Java — kiểm tra quy tắc | `mvn checkstyle:check` | Vi phạm Google Java Style |
| React — kiểm tra format | `npx prettier --check .` | Có file chưa format đúng |
| React — kiểm tra quy tắc | `npx eslint .` | Có lỗi ESLint |
| Python — kiểm tra format | `uv run ruff format --check .` | Có file chưa format đúng |
| Python — kiểm tra quy tắc | `uv run ruff check .` | Có lỗi Ruff |

Bước **test** chạy thêm `uv run pytest` cho `ml-service` — Python không có bước biên dịch như Java, nên cài đúng thư viện theo `uv.lock` rồi chạy test qua được cũng chính là bằng chứng app khởi động được.

Khuyến nghị thêm (tùy chọn, không bắt buộc Tuần 4): cài **Husky + lint-staged** để tự chạy `spotless:apply`/`prettier --write`/`ruff format` ngay lúc `git commit`, tránh việc CI đỏ chỉ vì quên format — chi phí cài đặt thấp (1 lần), lợi ích giữ pipeline xanh liên tục (đúng mục tiêu đã ghi ở `ke-hoach-15-tuan.md` Tuần 6: *"Giữ pipeline CI xanh liên tục"*).

---

**Tổng cộng:** quy ước đặt tên + cấu trúc thư mục cho cả 3 ngôn ngữ, cấu hình sẵn dùng được cho Checkstyle (Google Java Style) + Spotless (google-java-format) + ESLint 10 (flat config, recommended + react-hooks + react-refresh + eslint-config-prettier — xem cập nhật 23/09/2026 ở Mục 2.3) + Prettier + Ruff (lint + format) + pytest, thư viện Python quản lý bằng uv. Phần Java/React đáp ứng yêu cầu Tuần 3 của `ke-hoach-15-tuan.md`; phần Python bổ sung ở Tuần 4 lúc dựng `ml-service`.
