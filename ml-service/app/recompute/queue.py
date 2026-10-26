"""Các câu SQL trên bảng `recompute_request` (migration V3 của backend).

Mọi thời điểm của bảng là giờ UTC, so bằng `UTC_TIMESTAMP(3)`, không dùng `NOW()` (đổi theo
múi giờ của từng kết nối).
"""

from dataclasses import dataclass
from datetime import datetime

from sqlalchemy import Connection, bindparam, text

RECOMMENDATION = "RECOMMENDATION"
SEGMENT_ASSIGN = "SEGMENT_ASSIGN"

MIN_RETRY_SECONDS = 10
MAX_RETRY_SECONDS = 600
MAX_ERROR_LENGTH = 1000


@dataclass(frozen=True)
class RecomputeRequest:
    """Một yêu cầu "tính lại việc `task_type` cho tài khoản `account_id`" đã đến hạn."""

    account_id: int
    task_type: str
    version: int
    attempts: int
    due_at: datetime


_FETCH_DUE = text(
    """
    SELECT account_id, task_type, version, attempts, due_at
    FROM recompute_request
    WHERE due_at <= UTC_TIMESTAMP(3) AND task_type IN :task_types
    ORDER BY due_at, account_id
    LIMIT :limit
    """
).bindparams(bindparam("task_types", expanding=True))


def fetch_due(conn: Connection, task_types: list[str], limit: int) -> list[RecomputeRequest]:
    """Lấy tối đa `limit` yêu cầu đã đến hạn của các loại việc `task_types`, đến hạn sớm trước."""
    if not task_types:
        return []
    rows = conn.execute(_FETCH_DUE, {"task_types": task_types, "limit": limit})
    return [RecomputeRequest(*row) for row in rows]


def delete_if_unchanged(conn: Connection, request: RecomputeRequest) -> bool:
    """Xóa yêu cầu vừa xử lý xong, chỉ khi không có yêu cầu mới đến trong lúc tính.

    Backend gộp yêu cầu mới vào cùng dòng và tăng `version`, nên `version` đổi nghĩa là phải
    tính lại: giữ dòng để lượt lấy việc sau xử lý tiếp. Trả về True nếu đã xóa.
    """
    result = conn.execute(
        text(
            "DELETE FROM recompute_request "
            "WHERE account_id = :account_id AND task_type = :task_type AND version = :version"
        ),
        {
            "account_id": request.account_id,
            "task_type": request.task_type,
            "version": request.version,
        },
    )
    return result.rowcount == 1


def retry_delay_seconds(attempts: int) -> int:
    """Thời gian chờ trước lần thử lại thứ `attempts`: 10 giây, gấp đôi mỗi lần, tối đa 10 phút."""
    exponent = min(max(attempts - 1, 0), 10)
    return min(MIN_RETRY_SECONDS * 2**exponent, MAX_RETRY_SECONDS)


def record_failure(conn: Connection, request: RecomputeRequest, error: str) -> int | None:
    """Ghi lần tính lỗi: tăng `attempts`, lưu lỗi, lùi `due_at`. Trả về số giây chờ.

    Không làm gì (trả về None) nếu trong lúc tính đã có yêu cầu mới: yêu cầu đó giữ hạn của nó.
    """
    delay = retry_delay_seconds(request.attempts + 1)
    result = conn.execute(
        text(
            "UPDATE recompute_request "
            "SET attempts = attempts + 1, last_error = :error, "
            "    due_at = TIMESTAMPADD(SECOND, :delay, UTC_TIMESTAMP(3)) "
            "WHERE account_id = :account_id AND task_type = :task_type AND version = :version"
        ),
        {
            "error": error[:MAX_ERROR_LENGTH],
            "delay": delay,
            "account_id": request.account_id,
            "task_type": request.task_type,
            "version": request.version,
        },
    )
    return delay if result.rowcount == 1 else None


def pending_counts(conn: Connection) -> dict[str, dict[str, int]]:
    """Số yêu cầu đang chờ theo loại việc: tổng số và số đã đến hạn."""
    rows = conn.execute(
        text(
            "SELECT task_type, COUNT(*), "
            "       COALESCE(SUM(due_at <= UTC_TIMESTAMP(3)), 0) "
            "FROM recompute_request GROUP BY task_type"
        )
    )
    return {task_type: {"total": int(total), "due": int(due)} for task_type, total, due in rows}
