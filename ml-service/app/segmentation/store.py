"""Ghi, đọc mô hình phân nhóm trong CSDL; gán nhóm khi có đơn Đã giao; huấn luyện lại hằng tuần.

Chỉ ml-service ghi các bảng segment_model, customer_segment, account_segment, và chỉ từ tiến
trình lấy việc (hàm xử lý SEGMENT_ASSIGN và việc mỗi đêm) hoặc lệnh `evaluate --save`, nên không
có 2 nơi ghi đè lên nhau trong lúc chạy bình thường.
"""

import logging
from collections.abc import Callable
from datetime import datetime, timedelta

from sqlalchemy import Connection, text

from app.recompute.queue import RecomputeRequest
from app.segmentation.model import Segment, SegmentModel, train
from app.segmentation.rfm import Rfm, load_rfm

logger = logging.getLogger(__name__)

RETRAIN_INTERVAL = timedelta(days=7)
"""BR-23: huấn luyện lại mỗi tuần. **[Giả định]** tần suất, đổi ở đây."""

RETRAIN_TOLERANCE = timedelta(hours=1)
"""Việc đêm chạy lúc 02:00 nhưng có thể trễ vài phút: lần trước 02:03 thì 7 ngày sau 02:00 vẫn
coi như đã đủ 1 tuần."""


def save_model(conn: Connection, model: SegmentModel, rows: list[Rfm], now: datetime) -> int:
    """Ghi mô hình mới, thay toàn bộ nhóm và kết quả phân nhóm cũ (trong giao dịch của `conn`).
    Trả về mã dòng segment_model."""
    computed_at = now.replace(microsecond=0)
    model_id = conn.execute(
        text(
            "INSERT INTO segment_model (cluster_count, elbow_cluster_count, customer_count, "
            "silhouette, log_recency_mean, log_recency_std, log_frequency_mean, "
            "log_frequency_std, log_monetary_mean, log_monetary_std, trained_at) "
            "VALUES (:k, :elbow, :n, :silhouette, :rm, :rs, :fm, :fs, :mm, :ms, :at)"
        ),
        {
            "k": len(model.segments),
            "elbow": model.elbow_k,
            "n": model.customer_count,
            "silhouette": model.silhouette,
            "rm": model.mean[0],
            "rs": model.std[0],
            "fm": model.mean[1],
            "fs": model.std[1],
            "mm": model.mean[2],
            "ms": model.std[2],
            "at": computed_at,
        },
    ).lastrowid
    conn.execute(text("DELETE FROM account_segment"))
    conn.execute(text("DELETE FROM customer_segment"))
    segment_ids: dict[str, int] = {}
    for segment in model.segments:
        segment_ids[segment.code] = conn.execute(
            text(
                "INSERT INTO customer_segment (model_id, code, name, description, "
                "center_recency, center_frequency, center_monetary, computed_at) "
                "VALUES (:model_id, :code, :name, :description, :r, :f, :m, :at)"
            ),
            {
                "model_id": model_id,
                "code": segment.code,
                "name": segment.name,
                "description": segment.description,
                "r": segment.center[0],
                "f": segment.center[1],
                "m": segment.center[2],
                "at": computed_at,
            },
        ).lastrowid
    _upsert_assignments(conn, rows, model.assign(rows), segment_ids, computed_at)
    return int(model_id)


def _upsert_assignments(
    conn: Connection,
    rows: list[Rfm],
    segments: list[Segment],
    segment_ids: dict[str, int],
    computed_at: datetime,
) -> None:
    """Ghi (hoặc ghi đè) dòng account_segment của từng khách."""
    if not rows:
        return
    conn.execute(
        text(
            "INSERT INTO account_segment (account_id, segment_id, rfm_recency, rfm_frequency, "
            "rfm_monetary, computed_at) VALUES (:account_id, :segment_id, :r, :f, :m, :at) "
            "AS incoming ON DUPLICATE KEY UPDATE segment_id = incoming.segment_id, "
            "rfm_recency = incoming.rfm_recency, rfm_frequency = incoming.rfm_frequency, "
            "rfm_monetary = incoming.rfm_monetary, computed_at = incoming.computed_at"
        ),
        [
            {
                "account_id": row.account_id,
                "segment_id": segment_ids[segment.code],
                "r": row.recency,
                "f": row.frequency,
                "m": row.monetary,
                "at": computed_at,
            }
            for row, segment in zip(rows, segments, strict=True)
        ],
    )


def load_model(conn: Connection) -> tuple[SegmentModel, dict[str, int], datetime] | None:
    """Mô hình đang dùng (của các dòng customer_segment hiện có), mã từng nhóm theo `code` và
    thời điểm huấn luyện; None nếu chưa huấn luyện lần nào."""
    rows = conn.execute(
        text(
            "SELECT s.id, s.code, s.name, s.description, s.center_recency, s.center_frequency, "
            "s.center_monetary, m.cluster_count, m.elbow_cluster_count, m.customer_count, "
            "m.silhouette, m.log_recency_mean, m.log_recency_std, m.log_frequency_mean, "
            "m.log_frequency_std, m.log_monetary_mean, m.log_monetary_std, m.trained_at "
            "FROM customer_segment s JOIN segment_model m ON m.id = s.model_id ORDER BY s.id"
        )
    ).all()
    if not rows:
        return None
    first = rows[0]
    model = SegmentModel(
        mean=(first.log_recency_mean, first.log_frequency_mean, first.log_monetary_mean),
        std=(first.log_recency_std, first.log_frequency_std, first.log_monetary_std),
        segments=tuple(
            Segment(
                code=r.code,
                name=r.name,
                description=r.description,
                center=(r.center_recency, r.center_frequency, r.center_monetary),
                size=0,
                mean_recency=0.0,
                mean_frequency=0.0,
                mean_monetary=0.0,
            )
            for r in rows
        ),
        silhouette=first.silhouette,
        elbow_k=first.elbow_cluster_count,
        customer_count=first.customer_count,
    )
    return model, {r.code: int(r.id) for r in rows}, first.trained_at


def retrain(conn: Connection, now: datetime) -> SegmentModel | None:
    """Huấn luyện trên mọi khách có đơn Đã giao và ghi kết quả; None (không ghi gì) nếu chưa
    đủ dữ liệu, giữ nguyên mô hình cũ nếu có."""
    rows = load_rfm(conn, now)
    model = train(rows)
    if model is None:
        logger.info("Chưa đủ dữ liệu để phân nhóm: %d khách có đơn Đã giao", len(rows))
        return None
    save_model(conn, model, rows, now)
    logger.info(
        "Đã phân nhóm %d khách thành %d nhóm (Silhouette %.3f, điểm gãy K = %d)",
        len(rows),
        len(model.segments),
        model.silhouette,
        model.elbow_k,
    )
    return model


def retrain_if_due(conn: Connection, now: datetime) -> bool:
    """Việc mỗi đêm: huấn luyện lại nếu chưa có mô hình hoặc mô hình đã gần 1 tuần tuổi."""
    current = load_model(conn)
    if current is not None and now - current[2] < RETRAIN_INTERVAL - RETRAIN_TOLERANCE:
        return False
    return retrain(conn, now) is not None


def assign_account(conn: Connection, account_id: int, now: datetime) -> None:
    """Gán nhóm cho 1 khách theo mô hình hiện tại (BR-23), không huấn luyện lại. Chưa có mô hình
    thì huấn luyện lần đầu (đã gồm khách này). Khách không có đơn Đã giao thì xóa dòng (BR-24)."""
    rows = load_rfm(conn, now, [account_id])
    if not rows:
        conn.execute(text("DELETE FROM account_segment WHERE account_id = :id"), {"id": account_id})
        return
    current = load_model(conn)
    if current is None:
        retrain(conn, now)
        return
    model, segment_ids, _ = current
    _upsert_assignments(conn, rows, model.assign(rows), segment_ids, now.replace(microsecond=0))


def make_handler(clock: Callable[[], datetime]) -> Callable[[Connection, RecomputeRequest], None]:
    """Hàm xử lý yêu cầu SEGMENT_ASSIGN cho tiến trình lấy việc."""

    def handle(conn: Connection, request: RecomputeRequest) -> None:
        assign_account(conn, request.account_id, clock())

    return handle
