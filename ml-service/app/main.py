"""Điểm vào của ml-service: khởi tạo ứng dụng FastAPI, kết nối MySQL và tiến trình lấy việc."""

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings
from app.db import create_db_engine
from app.recompute.queue import pending_counts
from app.recompute.worker import Handler, RecomputeWorker

# uvicorn chỉ cấu hình log của chính nó; dòng này cho log của app (vd tiến trình lấy việc) hiện ra.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

# Hàm xử lý theo loại việc của recompute_request. Loại việc chưa có hàm xử lý thì yêu cầu nằm chờ
# trong CSDL, không mất: RECOMMENDATION thêm ở Bước 3 Tuần 8, SEGMENT_ASSIGN ở Tuần 9.
TASK_HANDLERS: dict[str, Handler] = {}


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Tạo kết nối CSDL và chạy tiến trình lấy việc khi khởi động; dừng khi tắt.

    Không chạm CSDL ở đây: MySQL hay bảng chưa sẵn sàng thì tiến trình lấy việc tự thử lại.
    """
    settings = Settings.from_env()
    engine = create_db_engine(settings)
    worker = RecomputeWorker(
        engine,
        TASK_HANDLERS,
        poll_interval_seconds=settings.poll_interval_seconds,
        batch_size=settings.batch_size,
    )
    app.state.engine = engine
    app.state.worker = worker
    if settings.worker_enabled:
        worker.start()
    try:
        yield
    finally:
        worker.stop()
        engine.dispose()


app = FastAPI(title="Fashion Shop ML Service", lifespan=lifespan)


@app.get("/health")
def health() -> dict[str, str]:
    """Trả về trạng thái service — cùng định dạng với /actuator/health của Spring Boot.

    Cố ý không kiểm tra CSDL: backend chờ ml-service chạy, mà Flyway (tạo bảng) lại nằm trong
    backend, nên kiểm tra CSDL ở đây có thể làm cả hệ thống đứng chờ nhau.
    """
    return {"status": "UP"}


@app.get("/recompute/status")
def recompute_status(request: Request) -> dict[str, object]:
    """Trạng thái tiến trình lấy việc và số yêu cầu đang chờ theo loại việc (để kiểm tra)."""
    status = request.app.state.worker.status()
    try:
        with request.app.state.engine.connect() as conn:
            status["pending"] = pending_counts(conn)
    except SQLAlchemyError as exc:
        status["pending"] = None
        status["pendingError"] = f"{type(exc).__name__}: {exc}".splitlines()[0]
    return status
