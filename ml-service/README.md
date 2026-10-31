# ml-service

Service FastAPI cho 2 tính năng ML của đề tài: gợi ý sản phẩm (hybrid: lọc theo nội dung, hay mua cùng, khảo sát, bán chạy) và phân nhóm khách hàng (RFM + K-Means). Chỉ được Spring Boot gọi nội bộ, không gọi thẳng từ trình duyệt. Từ Tuần 8 đọc/ghi MySQL: tiến trình lấy việc (`app/recompute/`) xử lý bảng `recompute_request` mỗi 2 giây (lượt lấy đủ 50 yêu cầu thì lấy tiếp ngay, không chờ).

## Chạy trên máy (cần uv: https://docs.astral.sh/uv/)

```powershell
uv sync                         # tạo .venv, cài đúng phiên bản theo uv.lock
uv run fastapi dev app/main.py  # http://localhost:8000/health, tài liệu API ở /docs
uv run pytest                   # chạy test (test CSDL cần MySQL, xem dưới)
uv run ruff check .             # kiểm tra lint
uv run ruff format .            # format code
```

## Kết nối MySQL

Dùng cùng biến môi trường với backend; mặc định khớp MySQL chạy bằng `docker compose up -d mysql` trên máy:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_HOST` / `DB_PORT` | `localhost` / `3306` | Máy chủ MySQL (trong Docker là `mysql`) |
| `DB_NAME` | `fashionshop` | CSDL của shop |
| `DB_USERNAME` / `MYSQL_ROOT_PASSWORD` | `root` / `changeme_local_dev` | Tài khoản MySQL |
| `RECOMPUTE_WORKER_ENABLED` | `true` | Bật tiến trình lấy việc |
| `RECOMPUTE_POLL_SECONDS` / `RECOMPUTE_BATCH_SIZE` | `2` / `50` | Nhịp lấy việc, số yêu cầu mỗi lượt |

`uv run` không đọc file `.env`: nạp `MYSQL_ROOT_PASSWORD` vào terminal trước (cmd: `set MYSQL_ROOT_PASSWORD=...`, PowerShell: `$env:MYSQL_ROOT_PASSWORD="..."`).

- `GET /recompute/status`: tiến trình lấy việc có chạy, có giữ khóa không, số yêu cầu đang chờ theo loại việc.
- Chỉ 1 tiến trình lấy việc trên 1 CSDL (khóa `GET_LOCK`). Chạy trên máy trong khi container ml-service cũng chạy thì bản sau chỉ đứng chờ.
- Test CSDL tạo lại `fashionshop_mltest` và chạy các migration của backend; không có MySQL thì các test đó được bỏ qua.

## Dữ liệu hành vi mô phỏng (Tuần 8)

Shop chưa có khách thật nên gợi ý và phân nhóm khách hàng dùng dữ liệu mô phỏng: khoảng 300 khách thuộc 7 kiểu (VIP, khách quen, tiết kiệm, mới, sắp rời bỏ, đã rời bỏ, chỉ xem) với lượt xem, thêm vào giỏ, đơn hàng 12 tháng gần nhất, đánh giá, khảo sát. Cần nạp danh mục sản phẩm mẫu trước (backend tự nạp lần đầu chạy).

```powershell
# cmd: set SIM_DEMO_PASSWORD=...   PowerShell: $env:SIM_DEMO_PASSWORD="..."
uv run python -m app.simulation              # xóa dữ liệu mô phỏng cũ rồi sinh lại (khoảng 10 giây)
uv run python -m app.simulation --reset-only # chỉ xóa
uv run python -m app.simulation --help       # số khách (--customers), seed (--seed)...
```

- Mọi tài khoản mô phỏng có email `@mophong.test`: xóa, sinh lại chỉ đụng các tài khoản này, không đụng tài khoản thật hay danh mục. Chạy trong 1 giao dịch, lỗi giữa chừng thì CSDL giữ nguyên.
- Cùng seed và cùng danh mục thì ra cùng dữ liệu (thời gian tính lùi từ lúc chạy). Không trừ tồn kho.
- 6 tài khoản demo, mỗi tài khoản cho thấy 1 trường hợp gợi ý/phân nhóm: `demo.vip`, `demo.tietkiem`, `demo.ngungmua`, `demo.chixem`, `demo.khaosat`, `demo.moi` (`@mophong.test`). Mật khẩu là biến môi trường `SIM_DEMO_PASSWORD` (ít nhất 8 ký tự), không ghi vào code hay git; không đặt thì tài khoản demo không đăng nhập được. Khách thường `kh0001@mophong.test`... không đăng nhập được.
- Sau khi ghi, mỗi khách có 1 yêu cầu tính lại gợi ý (và phân nhóm nếu có đơn đã giao) trong `recompute_request`; ml-service đang chạy sẽ tính gợi ý cho cả 300 khách trong khoảng 40 giây, yêu cầu phân nhóm nằm chờ đến Tuần 9.
- Trên staging: `docker compose exec -e SIM_DEMO_PASSWORD=... ml-service uv run --no-sync python -m app.simulation`.

Notebook EDA: `notebooks/eda_du_lieu_mo_phong.ipynb` (đã lưu kết quả chạy). Chạy lại sau khi sinh dữ liệu:

```powershell
uv sync --group eda
uv run --group eda jupyter execute --inplace notebooks/eda_du_lieu_mo_phong.ipynb
uv run ruff format notebooks
```

## Gợi ý sản phẩm (Tuần 8)

Package `app/recommendation/`. Mô hình dựng trong bộ nhớ từ MySQL (khoảng 0,3 giây với 1.050 sản phẩm), dựng lại khi sản phẩm, tồn kho, đơn hàng hay đánh giá đổi (kiểm tra mỗi 5 giây).

- **Vector sản phẩm** (`features.py`): TF-IDF tên + mô tả (từ đơn và cặp 2 từ), one-hot danh mục, thương hiệu, 5 khoảng giá; trọng số khối 0,45 / 0,30 / 0,15 / 0,10. Độ tương đồng cosine = tích vô hướng.
- **Hay mua cùng** (`copurchase.py`): cosine giữa 2 sản phẩm theo tập khách đã mua (đơn khác CANCELLED), co các cặp ít khách chung.
- **Trang chủ** (`model.py`, nguồn theo BR-38): đủ dữ liệu hành vi (BR-19) thì trộn 0,7 × cosine với hồ sơ P(u) (đã mua 5, trong giỏ 3, yêu thích 2, đã xem 1, khảo sát 0,5) + 0,2 × hay mua cùng + 0,1 × bán chạy + 0,15 × điểm cộng theo kiểu người mua; chỉ có khảo sát thì cosine với vector khảo sát + 0,2 × bán chạy + 0,15 × điểm cộng, sản phẩm trong khoảng giá khách chọn đứng trước; không có gì thì không có danh sách (trang chủ hiện bán chạy). Không gợi ý sản phẩm đã mua, đang trong giỏ, hết hàng; trừ điểm sản phẩm trùng danh mục để danh sách đa dạng. Mỗi khách lưu 20 sản phẩm kèm lý do vào bảng `recommendation`.
- **Ưu tiên tương tác gần đây** (`model.py`): trọng số mỗi tương tác ở trên nhân thêm 1 + β × 0,5^(tuổi tính bằng ngày / bán rã), β = 3, bán rã 1 ngày: món vừa thêm giỏ hay vừa xem nặng gấp 4, sau 1 ngày gấp 2,5, sau 1 tuần gần như bình thường. Nhờ vậy thêm 1 món vào giỏ làm trung bình 1,6 trên 8 món trang chủ đổi (trước 0,5). Lý do ghi "bạn vừa thêm vào giỏ / vừa xem" khi tương tác trong 24 giờ.
- **Tính lại** (`store.py`): hàm xử lý yêu cầu RECOMMENDATION của tiến trình lấy việc (khoảng 20 ms mỗi khách). Lúc khởi động và 02:00 giờ Việt Nam mỗi đêm, ghi yêu cầu cho mọi khách có danh sách, có đơn Đã giao, có lượt xem 30 ngày hoặc đã làm khảo sát (BR-20).
- **API cho backend** (khoảng 5 ms): `GET /recommendations/related/{productId}?limit=4` (sản phẩm liên quan, US-12), `POST /recommendations/survey` (gợi ý theo khảo sát, US-23; body `categoryIds`, `brandId`, `priceMin`, `priceMax`, `style`, `minRating`, `excludeProductIds`, `limit`), `POST /recommendations/bought-together` ("Thường được mua cùng" ở giỏ hàng, chức năng 20; body `productIds` 1–50 mã, `limit`; cộng cột ma trận hay mua cùng của các sản phẩm trong giỏ, chưa ai mua cùng thì rỗng). Xem `/docs`.

Đánh giá ngoại tuyến (giấu đơn cuối của mỗi khách rồi đo tỷ lệ trúng, precision, recall, NDCG ở 10 sản phẩm đầu; khởi động lạnh: đoán đơn đầu tiên chỉ từ khảo sát):

```powershell
uv run python -m app.recommendation.evaluate          # vài giây
uv run python -m app.recommendation.evaluate --grid   # thử thêm 192 bộ trọng số và 15 bộ β × bán rã (vài phút)
```

Bảng kết quả có thêm dòng "Hybrid, không ưu tiên gần đây" (β = 0) để so. Lưu ý: tỷ lệ trúng tăng khi bật ưu tiên gần đây (49% → 64% trên dữ liệu mô phỏng) phần lớn vì mô phỏng luôn cho khách xem món trước khi mua, nên không coi đó là số đo hiệu quả thật; CTR trên trang chủ mới là số đo thật.

## Phân nhóm khách hàng (Tuần 9)

Package `app/segmentation/` (US-19, BR-23, BR-24). Chỉ khách có ít nhất 1 đơn Đã giao mới được phân nhóm.

- **R, F, M** (`rfm.py`): R = số ngày từ lúc đặt đơn Đã giao gần nhất, F = số đơn Đã giao, M = tổng `total_amount` các đơn Đã giao.
- **Huấn luyện** (`model.py`): đặc trưng ln(1 + R), ln(1 + F), ln(1 + M), chuẩn hóa z; K-Means với K = 1..8. Elbow (điểm gãy của inertia) và Silhouette: chọn K lớn nhất trong 3..6, không nhỏ hơn điểm gãy, Silhouette từ 0,3 và kém tốt nhất không quá 0,05. Cần ít nhất 20 khách. Tên nhóm (VIP, thân thiết, tiết kiệm, mới, có nguy cơ rời bỏ, đã lâu không mua) gán bằng cách ghép tâm nhóm với kiểu khách mẫu gần nhất (thuật toán Hungary, không trùng tên); mô tả ghi số liệu trung bình của nhóm và gợi ý chăm sóc.
- **Lưu** (`store.py`, migration V6): `segment_model` (tham số chuẩn hóa, K, Silhouette, mỗi lần huấn luyện 1 dòng), `customer_segment` (nhóm của lần mới nhất, tâm dạng z), `account_segment` (nhóm và R, F, M của từng khách).
- **Khi nào chạy**: yêu cầu SEGMENT_ASSIGN (backend ghi khi đơn sang Đã giao) gán khách vào nhóm có tâm gần nhất, không huấn luyện lại; chưa có mô hình thì huấn luyện lần đầu. Việc mỗi đêm huấn luyện lại khi mô hình đã đủ 1 tuần; chưa đủ thì gán lại mọi khách vào tâm gần nhất theo R, F, M mới nhất (R tăng mỗi ngày dù khách không mua, nên khách lâu không mua được chuyển nhóm ngay đêm đó).

Huấn luyện ngay và in bảng Elbow/Silhouette, các nhóm, so với kiểu khách mô phỏng (chỉ số ARI):

```powershell
uv run python -m app.segmentation.evaluate          # chỉ in, không ghi
uv run python -m app.segmentation.evaluate --save   # ghi mô hình vào CSDL (vd sau khi chạy lại mô phỏng)
uv run python -m app.segmentation.evaluate --features   # so các bộ đặc trưng ngoài R, F, M (không ghi)
```

**Đặc trưng ngoài R, F, M** (`features.py`, góp ý 30/10: thêm nếu có tác động): chạy cùng quy trình huấn luyện cho 9 bộ đặc trưng (thêm AOV = M/F, giá trung bình mỗi món, L = thời gian gắn bó của LRFM, khoảng cách trung bình giữa 2 đơn, số sản phẩm xem 90 ngày; hoặc thay M bằng AOV, giá mỗi món) rồi so ARI với kiểu khách mô phỏng. Trên 3 bộ dữ liệu mô phỏng (seed 2026, 2027, 2028) không bộ nào hơn R, F, M (ARI trung bình 0,46): LRFM 0,48 (chênh trong khoảng dao động giữa các seed), các bộ còn lại 0,36 – 0,47. Giá mỗi món của khách tiết kiệm thấp hơn khách quen (trung vị 78.000đ so với 134.000đ) nhưng dao động trong nhóm lớn, thêm vào chỉ làm nhiễu khoảng cách K-Means. Vì vậy mô hình giữ R, F, M.
