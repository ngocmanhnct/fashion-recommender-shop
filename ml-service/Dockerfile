# Bước 5 Tuần 4 — image cho ml-service (FastAPI).
FROM python:3.13-slim
WORKDIR /app

# Cài uv qua pip (cùng công cụ quản lý gói đang dùng trên máy dev, xem README).
RUN pip install --no-cache-dir uv==0.12.24

# Cài đúng phiên bản thư viện ghi trong uv.lock, bỏ nhóm dev (pytest, ruff...).
# Chép 2 file này trước mã nguồn để Docker dùng lại cache khi chỉ sửa code.
COPY pyproject.toml uv.lock .python-version ./
RUN uv sync --locked --no-dev --no-install-project

COPY app/ app/

EXPOSE 8000
# "fastapi run" là chế độ production (không tự nạp lại khi sửa code như "fastapi dev").
CMD ["uv", "run", "--no-sync", "fastapi", "run", "app/main.py", "--host", "0.0.0.0", "--port", "8000"]
