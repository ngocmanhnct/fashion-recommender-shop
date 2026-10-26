"""Điểm vào của ml-service: khởi tạo ứng dụng FastAPI, kết nối MySQL và tiến trình lấy việc."""

import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager
from datetime import timedelta

from fastapi import FastAPI, Request
from sqlalchemy import Connection
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings
from app.db import create_db_engine
from app.recommendation import api as recommendation_api
from app.recommendation.cache import ModelCache
from app.recommendation.data import VIEW_WINDOW_DAYS
from app.recommendation.store import enqueue_all, local_now, make_handler
from app.recompute.queue import RECOMMENDATION, pending_counts
from app.recompute.worker import Handler, RecomputeWorker

# uvicorn chỉ cấu hình log của chính nó; dòng này cho log của app (vd tiến trình lấy việc) hiện ra.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def task_handlers(cache: ModelCache) -> dict[str, Handler]:
    """Hàm xử lý theo loại việc của recompute_request. Loại việc chưa có hàm xử lý thì yêu cầu
    nằm chờ trong CSDL, không mất: SEGMENT_ASSIGN thêm ở Tuần 9."""
    return {RECOMMENDATION: make_handler(cache)}


def nightly_job(cache: ModelCache) -> Callable[[Connection], None]:
    """Việc mỗi đêm và khi khởi động: dựng lại mô hình, yêu cầu tính lại gợi ý cho mọi khách."""

    def run(conn: Connection) -> None:
        cache.invalidate()
        count = enqueue_all(conn, local_now() - timedelta(days=VIEW_WINDOW_DAYS))
        logging.getLogger(__name__).info("Đã yêu cầu tính lại gợi ý (%d dòng đổi)", count)

    return run


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    """Tạo kết nối CSDL và chạy tiến trình lấy việc khi khởi động; dừng khi tắt.

    Không chạm CSDL ở đây: MySQL hay bảng chưa sẵn sàng thì tiến trình lấy việc tự thử lại.
    """
    settings = Settings.from_env()
    engine = create_db_engine(settings)
    # Mô hình gợi ý trong bộ nhớ, dùng chung cho tiến trình lấy việc và API gợi ý tính ngay.
    cache = ModelCache()
    worker = RecomputeWorker(
        engine,
        task_handlers(cache),
        poll_interval_seconds=settings.poll_interval_seconds,
        batch_size=settings.batch_size,
        maintenance=nightly_job(cache),
    )
    app.state.engine = engine
    app.state.model_cache = cache
    app.state.worker = worker
    if settings.worker_enabled:
        worker.start()
    try:
        yield
    finally:
        worker.stop()
        engine.dispose()


app = FastAPI(title="Fashion Shop ML Service", lifespan=lifespan)
app.include_router(recommendation_api.router)


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
