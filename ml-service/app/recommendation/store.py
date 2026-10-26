"""Ghi danh sách gợi ý trang chủ vào bảng `recommendation` và yêu cầu tính lại hàng loạt.

Chỉ ml-service ghi bảng `recommendation` (BR-20), và chỉ qua hàm xử lý yêu cầu RECOMMENDATION
của tiến trình lấy việc, nên không có 2 nơi ghi đè lên nhau.
"""

from collections.abc import Callable, Sequence
from datetime import UTC, datetime

from sqlalchemy import Connection, text

from app.recommendation.cache import ModelCache
from app.recommendation.data import load_signals
from app.recommendation.model import Suggestion
from app.recompute.queue import RECOMMENDATION, RecomputeRequest

MAX_REASON_LENGTH = 255


def local_now() -> datetime:
    """Giờ địa phương không kèm múi giờ, cùng kiểu với các cột DATETIME do backend ghi."""
    return datetime.now(UTC).astimezone().replace(tzinfo=None)


def replace_recommendations(
    conn: Connection, account_id: int, suggestions: Sequence[Suggestion], generated_at: datetime
) -> None:
    """Xóa danh sách cũ của tài khoản rồi ghi danh sách mới (trong giao dịch của `conn`)."""
    conn.execute(text("DELETE FROM recommendation WHERE account_id = :id"), {"id": account_id})
    if not suggestions:
        return
    conn.execute(
        text(
            "INSERT INTO recommendation "
            "(account_id, product_id, rank_position, score, reason, generated_at) "
            "VALUES (:account_id, :product_id, :rank, :score, :reason, :generated_at)"
        ),
        [
            {
                "account_id": account_id,
                "product_id": s.product_id,
                "rank": rank,
                "score": s.score,
                "reason": s.reason[:MAX_REASON_LENGTH],
                "generated_at": generated_at,
            }
            for rank, s in enumerate(suggestions, start=1)
        ],
    )


def make_handler(
    cache: ModelCache, clock: Callable[[], datetime] = local_now
) -> Callable[[Connection, RecomputeRequest], None]:
    """Hàm xử lý yêu cầu RECOMMENDATION cho tiến trình lấy việc."""

    def handle(conn: Connection, request: RecomputeRequest) -> None:
        now = clock()
        model = cache.get(conn)
        signals = load_signals(conn, request.account_id, now)
        suggestions = [] if model is None or signals is None else model.for_account(signals)
        replace_recommendations(conn, request.account_id, suggestions, now.replace(microsecond=0))

    return handle


_ENQUEUE_ALL = text(
    """
    INSERT INTO recompute_request (account_id, task_type, due_at, version, attempts, created_at)
    SELECT * FROM (
        SELECT a.id AS account_id, :task_type AS task_type,
               UTC_TIMESTAMP(3) AS due_at, 1 AS version, 0 AS attempts,
               UTC_TIMESTAMP(3) AS created_at
        FROM account a
        WHERE a.role = 'CUSTOMER' AND (
            EXISTS (SELECT 1 FROM recommendation r WHERE r.account_id = a.id)
            OR EXISTS (SELECT 1 FROM orders o
                       WHERE o.account_id = a.id AND o.status = 'DELIVERED')
            OR EXISTS (SELECT 1 FROM product_view_log v
                       WHERE v.account_id = a.id AND v.viewed_at >= :since)
            OR EXISTS (SELECT 1 FROM survey_result s
                       WHERE s.account_id = a.id AND s.status = 'COMPLETED')
        )
    ) AS incoming
    ON DUPLICATE KEY UPDATE due_at = LEAST(recompute_request.due_at, incoming.due_at),
                            version = recompute_request.version + 1
    """
)


def enqueue_all(conn: Connection, since: datetime) -> int:
    """Yêu cầu tính lại gợi ý cho mọi khách có danh sách, có đơn Đã giao, có lượt xem từ `since`
    hoặc đã làm khảo sát (mỗi đêm và khi ml-service khởi động). Trả về số dòng MySQL báo đổi.

    Bắt được các thay đổi không gắn với hành động của khách: sản phẩm hết hàng hay bị xóa, lượt
    xem cũ trôi ra ngoài 30 ngày, đêm trước bị lỡ vì máy tắt.
    """
    return conn.execute(_ENQUEUE_ALL, {"since": since, "task_type": RECOMMENDATION}).rowcount
