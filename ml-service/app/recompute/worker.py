"""Tiến trình lấy việc: mỗi 2 giây đọc các yêu cầu đến hạn trong `recompute_request` và xử lý.

Quy tắc (trinh-bay/cap-nhat-goi-y-va-phan-nhom.md, mục 6):
- Chỉ 1 tiến trình lấy việc cho mỗi CSDL, giữ khóa `GET_LOCK('recompute_worker', 0)` trên một kết
  nối riêng. Chạy thử ml-service trên máy trong khi Docker cũng chạy thì bản sau chỉ đứng chờ.
- Mỗi lượt đọc là một giao dịch mới (MySQL mặc định REPEATABLE READ, giữ giao dịch cũ sẽ đọc
  lại dữ liệu cũ).
- Xử lý xong thì ghi kết quả và xóa yêu cầu trong cùng 1 giao dịch, chỉ xóa khi `version` chưa
  đổi; xử lý lỗi thì giữ yêu cầu và lùi hạn thử lại.
- MySQL chưa chạy hoặc chưa có bảng (backend chưa chạy Flyway) thì ghi log và thử lại ở lượt
  sau, không làm sập ml-service. `/health` không chạm CSDL nên Docker vẫn thấy ml-service chạy.
"""

import logging
import threading
import time
from collections.abc import Callable
from datetime import UTC, datetime

from sqlalchemy import Connection, Engine, text
from sqlalchemy.exc import SQLAlchemyError

from app.recompute.queue import (
    RecomputeRequest,
    delete_if_unchanged,
    fetch_due,
    record_failure,
)

logger = logging.getLogger(__name__)

LOCK_NAME = "recompute_worker"

Handler = Callable[[Connection, RecomputeRequest], None]
"""Hàm xử lý 1 loại việc: tính và ghi kết quả qua `conn` (đang trong giao dịch của worker)."""


def _utcnow() -> datetime:
    """Giờ UTC hiện tại, dạng không kèm múi giờ như cột DATETIME của MySQL."""
    return datetime.now(UTC).replace(tzinfo=None)


def _describe_db_error(exc: SQLAlchemyError) -> str:
    """Mô tả ngắn lỗi CSDL để ghi log, nhận ra 2 trường hợp hay gặp lúc khởi động."""
    code = getattr(getattr(exc, "orig", None), "args", (None,))[0]
    if code == 1146:
        return "CSDL chưa có bảng recompute_request (backend chưa chạy Flyway?)"
    if code in (2003, 2006, 2013):
        return "Chưa kết nối được MySQL"
    return f"{type(exc).__name__}: {exc}".splitlines()[0]


class RecomputeWorker:
    """Vòng lấy việc chạy trong 1 luồng nền của ml-service."""

    def __init__(
        self,
        engine: Engine,
        handlers: dict[str, Handler],
        poll_interval_seconds: float = 2.0,
        batch_size: int = 50,
        lock_retry_seconds: float = 30.0,
        error_log_interval_seconds: float = 60.0,
    ) -> None:
        """Nhận engine, các hàm xử lý theo loại việc và nhịp lấy việc."""
        self.engine = engine
        self.handlers = dict(handlers)
        self.poll_interval_seconds = poll_interval_seconds
        self.batch_size = batch_size
        self.lock_retry_seconds = lock_retry_seconds
        self.error_log_interval_seconds = error_log_interval_seconds
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self._lock_conn: Connection | None = None
        self._next_lock_attempt = 0.0
        self._last_error_logged: tuple[str, float] | None = None
        self.last_poll_at: datetime | None = None
        self.last_error: str | None = None
        self.processed = 0
        self.failed = 0

    # ---- vòng đời ----

    def start(self) -> None:
        """Chạy vòng lấy việc trong luồng nền (daemon, tự dừng khi tiến trình thoát)."""
        if self._thread is not None:
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, name="recompute-worker", daemon=True)
        self._thread.start()
        logger.info(
            "Bắt đầu lấy việc mỗi %.1f giây, loại việc: %s",
            self.poll_interval_seconds,
            ", ".join(sorted(self.handlers)) or "chưa có",
        )

    def stop(self, timeout: float = 10.0) -> None:
        """Dừng vòng lấy việc (sau việc đang làm dở) và nhả khóa."""
        self._stop.set()
        if self._thread is not None:
            self._thread.join(timeout)
            self._thread = None
        self._drop_lock(release=True)

    def _run(self) -> None:
        """Thân luồng nền: lặp tick() đến khi có lệnh dừng."""
        while not self._stop.is_set():
            self.tick()
            self._stop.wait(self.poll_interval_seconds)

    def tick(self) -> int:
        """Một lượt: giữ khóa rồi xử lý các yêu cầu đến hạn. Trả về số yêu cầu đã lấy.

        Lỗi CSDL không lan ra ngoài: ghi lại ở `last_error`, lượt sau thử lại.
        """
        try:
            if not self._ensure_lock():
                return 0
            count = self.poll_once()
            self.last_error = None
            return count
        except SQLAlchemyError as exc:
            self._drop_lock(release=False)
            self._report_error(_describe_db_error(exc))
            return 0

    # ---- khóa: chỉ 1 tiến trình lấy việc ----

    @property
    def holds_lock(self) -> bool:
        """True nếu tiến trình này đang giữ khóa lấy việc."""
        return self._lock_conn is not None

    def _ensure_lock(self) -> bool:
        """Giữ khóa `recompute_worker`; kiểm tra lại mỗi lượt vì kết nối có thể đã mất."""
        if self._lock_conn is not None:
            held = self._lock_conn.execute(
                text("SELECT IS_USED_LOCK(:name) = CONNECTION_ID()"), {"name": LOCK_NAME}
            ).scalar()
            self._lock_conn.commit()
            if held == 1:
                return True
            logger.warning("Mất khóa lấy việc, sẽ xin lại")
            self._drop_lock(release=False)
        if time.monotonic() < self._next_lock_attempt:
            return False
        conn = self.engine.connect()
        try:
            got = conn.execute(text("SELECT GET_LOCK(:name, 0)"), {"name": LOCK_NAME}).scalar()
            conn.commit()
        except SQLAlchemyError:
            conn.invalidate()
            raise
        if got == 1:
            self._lock_conn = conn
            logger.info("Đã giữ khóa lấy việc '%s'", LOCK_NAME)
            return True
        conn.invalidate()
        self._next_lock_attempt = time.monotonic() + self.lock_retry_seconds
        self._report_error("Một tiến trình ml-service khác đang lấy việc trên CSDL này, chờ")
        return False

    def _drop_lock(self, release: bool) -> None:
        """Bỏ kết nối giữ khóa. Đóng hẳn kết nối (không trả về pool) để MySQL nhả khóa."""
        conn, self._lock_conn = self._lock_conn, None
        if conn is None:
            return
        if release:
            try:
                conn.execute(text("SELECT RELEASE_LOCK(:name)"), {"name": LOCK_NAME})
            except SQLAlchemyError:
                pass  # kết nối đã hỏng thì MySQL cũng đã nhả khóa
        conn.invalidate()

    # ---- lấy việc ----

    def poll_once(self) -> int:
        """Đọc các yêu cầu đến hạn (giao dịch mới) rồi xử lý lần lượt. Trả về số yêu cầu."""
        with self.engine.begin() as conn:
            requests = fetch_due(conn, sorted(self.handlers), self.batch_size)
        self.last_poll_at = _utcnow()
        for request in requests:
            if self._stop.is_set():
                break
            self._process(request)
        return len(requests)

    def _process(self, request: RecomputeRequest) -> None:
        """Xử lý 1 yêu cầu; lỗi của hàm xử lý thì ghi vào yêu cầu để thử lại sau."""
        handler = self.handlers[request.task_type]
        started = time.perf_counter()
        try:
            with self.engine.begin() as conn:
                handler(conn, request)
                deleted = delete_if_unchanged(conn, request)
        except Exception as exc:  # noqa: BLE001 - lỗi nào của hàm xử lý cũng phải ghi để thử lại
            self.failed += 1
            with self.engine.begin() as conn:
                delay = record_failure(conn, request, f"{type(exc).__name__}: {exc}")
            logger.warning(
                "Lỗi khi tính %s cho tài khoản %d (lần %d): %s. %s",
                request.task_type,
                request.account_id,
                request.attempts + 1,
                exc,
                f"Thử lại sau {delay} giây" if delay else "Đã có yêu cầu mới, giữ hạn của nó",
            )
            return
        self.processed += 1
        # Số đo cho Chương 5: thời gian từ lúc đến hạn tới lúc xong, và thời gian tính.
        waited_ms = max((_utcnow() - request.due_at).total_seconds() * 1000, 0)
        logger.info(
            "Đã tính %s cho tài khoản %d: xong sau hạn %.0f ms, tính %.0f ms%s",
            request.task_type,
            request.account_id,
            waited_ms,
            (time.perf_counter() - started) * 1000,
            "" if deleted else "; có yêu cầu mới trong lúc tính, lượt sau tính lại",
        )

    # ---- trạng thái, log ----

    def _report_error(self, message: str) -> None:
        """Lưu lỗi gần nhất; cùng 1 lỗi chỉ ghi log mỗi phút 1 lần (vòng lặp chạy mỗi 2 giây)."""
        self.last_error = message
        now = time.monotonic()
        last = self._last_error_logged
        if last is None or last[0] != message or now - last[1] >= self.error_log_interval_seconds:
            logger.warning("%s", message)
            self._last_error_logged = (message, now)

    def status(self) -> dict[str, object]:
        """Trạng thái để xem nhanh qua `/recompute/status`."""
        return {
            "running": self._thread is not None and self._thread.is_alive(),
            "holdsLock": self.holds_lock,
            "taskTypes": sorted(self.handlers),
            "lastPollAt": self.last_poll_at.isoformat() + "Z" if self.last_poll_at else None,
            "processed": self.processed,
            "failed": self.failed,
            "lastError": self.last_error,
        }
