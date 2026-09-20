---

## title: Coding Convention — Java (Spring Boot) & React description: Quy ước code dùng cho toàn bộ dự án, thực thi tự động qua Checkstyle/Spotless (Java) và ESLint/Prettier (React) trong pipeline CI

# Coding Convention

## 0\. Mục đích & phạm vi

Đáp ứng mục cuối của TC2.1 Mức 5 (`dac-ta-yeu-cau.md` Mục 12 — yêu cầu phi chức năng): *"Viết coding convention thành văn bản"* (`ke-hoach-15-tuan.md` Tuần 3). Tài liệu này không chỉ mô tả — mọi quy tắc dưới đây đều được **thực thi tự động** qua công cụ đã chọn sẵn ở `ho-so-du-an.md` Mục 3 (Checkstyle, Spotless, ESLint, Prettier), chạy trong bước "lint" của pipeline GitHub Actions (`build → lint → test → quét bảo mật → đóng gói → deploy`) — vi phạm quy ước làm CI đỏ, không phải chỉ một khuyến nghị suông đọc rồi bỏ qua.

Áp dụng từ Tuần 4 (`ke-hoach-15-tuan.md`, lúc khởi tạo 3 service) — các đoạn code mẫu trong `dac-ta-yeu-cau.md` (Mục 9 sequence diagram) đã đặt tên class đúng theo quy ước ở Mục 1 dưới đây (`OrderController`, `AuthService`, `ProductVariantRepository`...), nên khi code thật sẽ khớp thẳng, không cần đổi tên lại.

---

## 1\. Java — Spring Boot (Backend)

### 1.1 Cấu trúc package

Theo layer, đúng kiến trúc layered đã chốt (`ho-so-du-an.md` Mục 3\) — cũng là cấu trúc đã vẽ ở component diagram (`dac-ta-yeu-cau.md` Mục 10, Hình 7):

com.manhdn.fashionshop

├── controller/       \# REST endpoints — @RestController, nhận request, gọi service, không chứa business logic

├── service/          \# Business logic — nơi các UPDATE có điều kiện (BR-08, BR-30...), transaction (@Transactional)

├── repository/        \# Spring Data JPA — @Repository, interface extends JpaRepository

├── entity/            \# @Entity — ánh xạ 1:1 bảng CSDL, khớp từ điển dữ liệu Mục 3 dac-ta-yeu-cau.md

├── dto/               \# Request/Response object — không để entity "rò rỉ" thẳng ra API

├── security/          \# JWT filter, Spring Security config (Security Layer ở Hình 7\)

├── exception/         \# Custom exception (vd InsufficientStockException, VoucherExpiredException — đã dùng ở Hình 6b) \+ @ControllerAdvice xử lý tập trung

├── client/            \# Gọi service ngoài — MLServiceClient (gọi FastAPI, Hình 7), VNPayClient

└── config/            \# @Configuration — CORS (NFR liên quan ở Mục 11 dac-ta-yeu-cau.md), Docker profile...

*Vì sao package-by-layer, không package-by-feature:* khớp đúng cách kiến trúc đã được trình bày xuyên suốt tài liệu đặc tả (controller/service/repository là đơn vị tổ chức chính, không phải "feature module") — nhất quán dễ trình bày khi bảo vệ hơn là đổi cách tổ chức code so với cách đã vẽ sơ đồ.

### 1.2 Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Class, Interface | PascalCase | `OrderService`, `ProductVariantRepository` |
| Method, biến | camelCase | `createOrder()`, `idempotencyKey` |
| Hằng số | UPPER\_SNAKE\_CASE | `MAX_LOGIN_ATTEMPTS` |
| Package | chữ thường, không gạch dưới | `com.manhdn.fashionshop.service` |
| Cột/bảng CSDL | snake\_case | `sku_code`, `order_item` (đã chốt ở Mục 3.4 `dac-ta-yeu-cau.md`) |

*Về lệch quy ước Java (camelCase) vs CSDL (snake\_case):* đây là **cố ý, không phải thiếu nhất quán** — mỗi ngôn ngữ/hệ thống dùng đúng chuẩn phổ biến của nó, và Spring Data JPA tự động ánh xạ giữa 2 chuẩn này qua `CamelCaseToUnderscoresNamingStrategy` (mặc định sẵn có, không cần cấu hình tay): field Java `skuCode` tự khớp cột CSDL `sku_code`. Không cần ghi `@Column(name = "sku_code")` thủ công cho từng field trừ khi tên lệch nhiều hơn quy tắc mặc định xử lý được.

### 1.3 Checkstyle — kiểm tra quy tắc

Dùng thẳng ruleset **Google Java Style** (`google_checks.xml`, đóng gói sẵn trong Checkstyle, không tự viết ruleset riêng — tránh tốn thời gian bikeshedding quy tắc cho một dự án 1 người/15 tuần). Điểm chính: dòng ≤ 100 ký tự, thụt lề 2 space, không dùng wildcard import (`import java.util.*`), mỗi class 1 file.

\<\!-- pom.xml \--\>

\<plugin\>

&nbsp;&nbsp;&nbsp;&nbsp;\<groupId\>org.apache.maven.plugins\</groupId\>

&nbsp;&nbsp;&nbsp;&nbsp;\<artifactId\>maven-checkstyle-plugin\</artifactId\>

&nbsp;&nbsp;&nbsp;&nbsp;\<configuration\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<configLocation\>google\_checks.xml\</configLocation\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<failOnViolation\>true\</failOnViolation\>

&nbsp;&nbsp;&nbsp;&nbsp;\</configuration\>

&nbsp;&nbsp;&nbsp;&nbsp;\<executions\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<execution\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<goals\>\<goal\>check\</goal\>\</goals\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<phase\>verify\</phase\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\</execution\>

&nbsp;&nbsp;&nbsp;&nbsp;\</executions\>

\</plugin\>

### 1.4 Spotless — tự động format

Checkstyle chỉ **báo lỗi**, không tự sửa. Spotless tự **format lại** theo `google-java-format`, chạy trước khi commit hoặc trong CI:

\<\!-- pom.xml \--\>

\<plugin\>

&nbsp;&nbsp;&nbsp;&nbsp;\<groupId\>com.diffplug.spotless\</groupId\>

&nbsp;&nbsp;&nbsp;&nbsp;\<artifactId\>spotless-maven-plugin\</artifactId\>

&nbsp;&nbsp;&nbsp;&nbsp;\<configuration\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<java\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<googleJavaFormat/\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\<removeUnusedImports/\>

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;\</java\>

&nbsp;&nbsp;&nbsp;&nbsp;\</configuration\>

\</plugin\>

Lệnh dùng hàng ngày: `mvn spotless:apply` (tự sửa format trước khi commit) — CI chạy `mvn spotless:check` (chặn merge nếu có file chưa format).

### 1.5 Javadoc

Bắt buộc cho method **public** ở Controller và Service (đây là "hợp đồng" người khác/hội đồng đọc để hiểu API và business logic mà không cần đọc hết code) — không bắt buộc cho method private hay Repository (interface JPA đã tự mô tả qua tên method, vd `findByIdempotencyKey`).

---

## 2\. React (Frontend)

### 2.1 Cấu trúc thư mục

src/

├── pages/          \# 1 file \= 1 trang/route (LoginPage.jsx, CheckoutPage.jsx)

├── components/     \# Component dùng lại nhiều nơi (ProductCard, CartItem)

├── hooks/          \# Custom hook (useCart, useAuth)

├── services/       \# Gọi REST API — mỗi file bọc đúng 1 nhóm endpoint đã định nghĩa ở

│                   \# dac-ta-yeu-cau.md Mục 9 (authApi.js → POST /api/auth/login,

│                   \# orderApi.js → POST /api/orders...)

└── context/        \# React Context (vd AuthContext lưu JWT — hết hạn 24h, NFR-03)

### 2.2 Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
| :---- | :---- | :---- |
| Component (file \+ tên) | PascalCase | `ProductCard.jsx` |
| Custom hook | camelCase, tiền tố `use` | `useCart.js` |
| Hàm, biến thường | camelCase | `handleSubmit` |
| Hằng số | UPPER\_SNAKE\_CASE | `MAX_CART_ITEMS` |

### 2.3 ESLint

Dùng `eslint:recommended` \+ `eslint-plugin-react` \+ `eslint-plugin-react-hooks` (bộ khuyến nghị chuẩn cho React hiện tại, cấu hình dạng **flat config** — `eslint.config.js`, chuẩn từ ESLint 9 trở lên). **Không dùng Airbnb config**: bộ quy tắc Airbnb rất chi tiết nhưng thường cần chỉnh sửa nhiều mới hợp với từng dự án — không đáng chi phí cấu hình/bảo trì thêm cho một người làm trong 15 tuần, trong khi bộ `recommended` đã đủ bắt các lỗi quan trọng (hook dùng sai, biến không dùng, so sánh lỏng lẻo `==`...).

// eslint.config.js

import js from "@eslint/js";

import react from "eslint-plugin-react";

import reactHooks from "eslint-plugin-react-hooks";

&nbsp;

export default \[

&nbsp;&nbsp;js.configs.recommended,

&nbsp;&nbsp;{

&nbsp;&nbsp;&nbsp;&nbsp;plugins: { react, "react-hooks": reactHooks },

&nbsp;&nbsp;&nbsp;&nbsp;rules: {

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;...react.configs.recommended.rules,

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;...reactHooks.configs.recommended.rules,

&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;"no-unused-vars": "error",

&nbsp;&nbsp;&nbsp;&nbsp;},

&nbsp;&nbsp;},

\];

### 2.4 Prettier

{

&nbsp;&nbsp;"printWidth": 100,

&nbsp;&nbsp;"tabWidth": 2,

&nbsp;&nbsp;"singleQuote": true,

&nbsp;&nbsp;"semi": true,

&nbsp;&nbsp;"trailingComma": "es5"

}

*Vì sao `printWidth: 100` (khác mặc định Prettier là 80):* khớp đúng độ dài dòng đã chọn cho Java ở Checkstyle (Mục 1.3) — một chuẩn thống nhất cho cả 2 ngôn ngữ trong cùng dự án, dễ nhớ hơn 2 con số khác nhau.

---

## 3\. Thực thi trong CI/CD

Cả 4 công cụ chạy ở bước **lint** của pipeline GitHub Actions (`ho-so-du-an.md` Mục 3):

| Bước | Lệnh | Chặn merge nếu |
| :---- | :---- | :---- |
| Java — kiểm tra format | `mvn spotless:check` | Có file chưa format đúng |
| Java — kiểm tra quy tắc | `mvn checkstyle:check` | Vi phạm Google Java Style |
| React — kiểm tra format | `npx prettier --check .` | Có file chưa format đúng |
| React — kiểm tra quy tắc | `npx eslint .` | Có lỗi ESLint |

Khuyến nghị thêm (tùy chọn, không bắt buộc Tuần 4): cài **Husky \+ lint-staged** để tự chạy `spotless:apply`/`prettier --write` ngay lúc `git commit`, tránh việc CI đỏ chỉ vì quên format — chi phí cài đặt thấp (1 lần), lợi ích giữ pipeline xanh liên tục (đúng mục tiêu đã ghi ở `ke-hoach-15-tuan.md` Tuần 6: *"Giữ pipeline CI xanh liên tục"*).

---

**Tổng cộng:** quy ước đặt tên \+ cấu trúc thư mục cho cả 2 ngôn ngữ, cấu hình sẵn dùng được cho Checkstyle (Google Java Style) \+ Spotless (google-java-format) \+ ESLint (flat config, recommended \+ react \+ react-hooks) \+ Prettier — đáp ứng đúng yêu cầu Tuần 3 của `ke-hoach-15-tuan.md`. Đóng gói vào Tuần 4 lúc khởi tạo 3 service, không cần quyết định thêm gì mới.

&nbsp;