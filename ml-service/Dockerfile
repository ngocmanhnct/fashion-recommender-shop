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

# Container chỉ được 1 CPU (docker-compose.yml). numpy (OpenBLAS) và scikit-learn (OpenMP) mặc định
# mở 1 luồng cho mỗi lõi của máy; với hàng nghìn phép tính nhỏ của việc gợi ý, các luồng phụ chỉ
# tốn công đồng bộ và giành CPU với luồng chính (đo 17/11/2026: chậm gấp đôi khi máy rảnh, gấp
# hàng chục lần khi máy bận). Giữ 1 luồng.
ENV OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

EXPOSE 8000
# "fastapi run" là chế độ production (không tự nạp lại khi sửa code như "fastapi dev").
CMD ["uv", "run", "--no-sync", "fastapi", "run", "app/main.py", "--host", "0.0.0.0", "--port", "8000"]
