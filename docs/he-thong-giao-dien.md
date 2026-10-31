# Hệ thống giao diện

Giao diện frontend không dùng thư viện UI. Toàn bộ màu, chữ, bo góc, đổ bóng, tốc độ chuyển động
khai bằng biến CSS (design token) ở `:root` đầu `frontend/src/index.css`. Đổi giao diện là đổi
biến, không sửa từng quy tắc.

Hướng thiết kế: **thanh lịch xanh rêu** (Mạnh chọn ngày 31/10/2026 trong 3 hướng mẫu). Nền kem,
xanh rêu cho thao tác chính, vàng đồng làm điểm nhấn, tiêu đề chữ có chân.

## Màu

| Biến | Giá trị | Dùng cho |
| --- | --- | --- |
| `--bg` | `#f7f4ee` | Nền trang (kem) |
| `--surface` | `#fffdf8` | Thẻ, bảng, ô nhập |
| `--surface-muted` | `#efebe2` | Nền ảnh sản phẩm, tiêu đề bảng |
| `--text` | `#1d2420` | Chữ chính |
| `--muted` | `#69706a` | Chữ phụ |
| `--border` | `#e4dfd3` | Viền |
| `--primary` | `#1f5f4a` | Nút chính, liên kết (xanh rêu) |
| `--primary-hover` | `#174a39` | Nút chính khi rê chuột |
| `--accent` | `#b9853a` | Điểm nhấn: số thứ tự, gạch chân menu, số trên giỏ hàng (vàng đồng) |
| `--price` | `#a8432c` | Giá tiền |
| `--star` | `#c8932f` | Sao đánh giá |
| `--deep` | `#163f32` | Dải trên cùng, phần mở đầu trang chủ, chân trang, menu quản trị (rêu đậm) |
| `--on-deep`, `--on-deep-muted` | `#f1ece1`, `#a9bdb3` | Chữ trên nền rêu đậm |
| `--danger`, `--success-*`, `--error-*`, `--warning-*` | | Thông báo lỗi, thành công, cảnh báo |

## Chữ

- Tiêu đề (`h1`–`h3`, logo): **Lora** 500/600 (`--font-heading`), có dấu tiếng Việt.
- Nội dung: **Be Vietnam Pro** 400/500/600 (`--font-body`), phông làm riêng cho tiếng Việt.
- Cả 2 tự lưu trữ qua gói `@fontsource/lora`, `@fontsource/be-vietnam-pro` (import trong
  `main.jsx`), không gọi Google Fonts.
- Cỡ chữ nền 15px, dòng 1.6. Nhãn nhỏ phía trên tiêu đề dùng lớp `.eyebrow` (chữ hoa, giãn chữ,
  màu vàng đồng).

## Bo góc, đổ bóng

`--radius` 14px (thẻ, bảng), `--radius-sm` 9px (ô nhập, liên kết menu), `--radius-pill` (nút, nhãn).
`--shadow-soft` cho khối đứng yên, `--shadow-card` cho thẻ khi rê chuột.

## Bố cục

- **Thanh trên cùng** (`components/Layout.jsx`, dính trên đầu khi cuộn): dải rêu đậm (miễn phí giao
  hàng từ 500.000 ₫, lời chào, sổ địa chỉ, đăng xuất), thanh chính (logo, ô tìm, lối tắt Yêu
  thích, Đơn hàng, Giỏ hàng hoặc Quản trị, Đăng nhập), hàng menu danh mục.
- **Menu danh mục** (`components/CategoryMenu.jsx`): danh mục gom thành 5 nhóm theo từ đầu tên
  (`services/categoryGroups.js`: Giày, Dép & sandal, Túi & ví, Balo & vali, Phụ kiện). Rê chuột
  hoặc bấm nhóm để mở bảng danh mục con; Esc, bấm ra ngoài, chuyển trang thì đóng. Thêm danh mục có
  từ đầu mới thì thêm vào `CATEGORY_GROUPS`, không thì vào nhóm Phụ kiện.
- **Màn hình dưới 960px**: menu danh mục và lối tắt tài khoản thu vào ngăn kéo trượt từ trái, mở
  bằng nút ☰; ô tìm xuống dòng riêng; chỉ còn biểu tượng giỏ hàng.
- **Chân trang**: rêu đậm, 4 cột (giới thiệu, mua sắm, giao hàng và thanh toán, dự án). Tên liên
  kết ở đây cố ý khác các liên kết trên thanh trên cùng để test E2E tìm theo tên không bị trùng.
- **Trang chủ**: phần mở đầu chia đôi (lời giới thiệu + 3 sản phẩm bán chạy nhất xếp chồng nghiêng
  như ảnh dán), khối gợi ý cho bạn, thẻ nhóm danh mục, sản phẩm bán chạy, 3 bước giải thích cách gợi
  ý hoạt động (`#cach-goi-y`).
- **Danh sách sản phẩm, chi tiết, giỏ hàng**: bảng lọc, ảnh sản phẩm và khối tạm tính đứng yên khi
  cuộn trên màn hình rộng (`position: sticky`, cách đầu trang `--header-height`).
- **Quản trị** (`components/AdminLayout.jsx`, danh sách mục ở `services/adminMenu.js`): menu bên
  trái nền rêu đậm, mỗi nhóm (Cửa hàng, Bán hàng, Khách hàng) là 1 khối có viền, tiêu đề vàng đồng;
  mỗi mục có biểu tượng và 1 dòng mô tả, tên mục không trùng tên nhóm. Trên nội dung có dòng vị
  trí "nhóm › mục". Màn hình dưới 720px: menu nằm trên nội dung, ẩn dòng mô tả.

## Chuyển động

| Hiệu ứng | Ở đâu |
| --- | --- |
| Nội dung trang hiện dần, trượt nhẹ lên khi chuyển trang | `.page-transition` trong `Layout` (theo đường dẫn); trang quản trị chỉ làm cho phần nội dung, menu đứng yên |
| Gợn sóng tại điểm bấm | `hooks/usePressRipple.js`: 1 bộ lắng nghe chung cho `.btn`, `.chip`, `.icon-link`, `.product-card`, `.category-menu-link` |
| Nút lún nhẹ khi bấm, mũi tên trong nút chính nhích sang phải khi rê chuột | `.btn:active`, `.btn-primary .icon` |
| Thẻ sản phẩm nhấc lên, ảnh phóng nhẹ khi rê chuột; thẻ trong lưới hiện lần lượt | `.product-card`, `.product-grid > li` |
| Khối trang chủ hiện dần khi cuộn tới | `components/Reveal.jsx` (IntersectionObserver; không có thì hiện ngay) |
| Bảng danh mục trượt xuống, gạch chân vàng đồng chạy ra | `.category-panel`, `.category-menu-link::after` |
| Số trên giỏ hàng nảy lên khi thêm hàng, trái tim "Yêu thích" đập 1 nhịp | `.cart-badge` (vẽ lại theo `key`), `.wishlist-btn.is-liked` |

Người dùng bật "giảm chuyển động" trong hệ điều hành (`prefers-reduced-motion`) thì mọi hiệu ứng
tắt, khối hiện dần hiện ngay, không vẽ gợn sóng.

## Biểu tượng

`components/Icon.jsx`: vài biểu tượng nét mảnh vẽ bằng SVG (giỏ, tim, hóa đơn, người, menu, đóng,
mũi tên, mũi tên xuống). Biểu tượng luôn đi kèm chữ (hiện, hoặc ẩn cho trình đọc màn hình trên màn
hình hẹp), nên tự nó `aria-hidden`.

## Khi thêm giao diện mới

- Dùng biến, không viết mã màu trực tiếp.
- Nút: `.btn` + `.btn-primary` (thao tác chính, 1 nút mỗi khối), `.btn-ghost` (phụ),
  `.btn-danger` (xóa, hủy); `.btn-lg` cho nút lớn ở trang chủ.
- Khối có tiêu đề: `.product-section` + `.section-header` (tiêu đề bên trái, liên kết "Xem tất cả"
  bên phải).
- Hiệu ứng mới đặt trong mục "Hiệu ứng" cuối `index.css` và phải tắt được bằng
  `prefers-reduced-motion`.
