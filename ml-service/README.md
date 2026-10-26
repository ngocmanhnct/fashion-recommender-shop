# ml-service

Service FastAPI cho 2 tính năng ML của đề tài: gợi ý sản phẩm (content-based filtering) và phân nhóm khách hàng (RFM + K-Means). Chỉ được Spring Boot gọi nội bộ, không gọi thẳng từ trình duyệt. Từ Tuần 8 đọc/ghi MySQL: tiến trình lấy việc (`app/recompute/`) xử lý bảng `recompute_request` mỗi 2 giây.

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
- Sau khi ghi, mỗi khách có 1 yêu cầu tính lại gợi ý (và phân nhóm nếu có đơn đã giao) trong `recompute_request`; loại việc chưa có hàm xử lý thì nằm chờ.
- Trên staging: `docker compose exec -e SIM_DEMO_PASSWORD=... ml-service uv run --no-sync python -m app.simulation`.

Notebook EDA: `notebooks/eda_du_lieu_mo_phong.ipynb` (đã lưu kết quả chạy). Chạy lại sau khi sinh dữ liệu:

```powershell
uv sync --group eda
uv run --group eda jupyter execute --inplace notebooks/eda_du_lieu_mo_phong.ipynb
uv run ruff format notebooks
```
