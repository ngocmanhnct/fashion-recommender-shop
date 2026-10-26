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
