"""Tính R, F, M của khách hàng từ các đơn Đã giao ("đơn hoàn tất", BR-23, BR-24).

- R (recency): số ngày từ lúc đặt đơn Đã giao gần nhất đến thời điểm tính.
- F (frequency): số đơn Đã giao.
- M (monetary): tổng `total_amount` của các đơn Đã giao (VNĐ, đã gồm phí ship và giảm giá).
Khách chưa có đơn Đã giao không có R, F, M và không được phân nhóm (BR-24).
"""

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime

from sqlalchemy import Connection, bindparam, text

SECONDS_PER_DAY = 86400


@dataclass(frozen=True)
class Rfm:
    """R, F, M của 1 khách tại thời điểm tính."""

    account_id: int
    recency: int
    frequency: int
    monetary: int


_SELECT = """
    SELECT o.account_id, MAX(o.created_at) AS last_order_at, COUNT(*) AS frequency,
           SUM(o.total_amount) AS monetary
    FROM orders o
    JOIN account a ON a.id = o.account_id AND a.role = 'CUSTOMER'
    WHERE o.status = 'DELIVERED' {extra}
    GROUP BY o.account_id
    ORDER BY o.account_id
"""
_ALL = text(_SELECT.format(extra=""))
_SOME = text(_SELECT.format(extra="AND o.account_id IN :ids")).bindparams(
    bindparam("ids", expanding=True)
)


def recency_days(last_order_at: datetime, now: datetime) -> int:
    """Số ngày tròn từ `last_order_at` đến `now` (không âm khi đồng hồ 2 máy lệch nhau)."""
    return max(0, int((now - last_order_at).total_seconds() // SECONDS_PER_DAY))


def load_rfm(
    conn: Connection, now: datetime, account_ids: Sequence[int] | None = None
) -> list[Rfm]:
    """R, F, M của mọi khách có đơn Đã giao, hoặc chỉ của `account_ids`; xếp theo mã tài khoản."""
    if account_ids is not None and not account_ids:
        return []
    query = _ALL if account_ids is None else _SOME
    params = {} if account_ids is None else {"ids": list(account_ids)}
    return [
        Rfm(
            account_id=int(row.account_id),
            recency=recency_days(row.last_order_at, now),
            frequency=int(row.frequency),
            monetary=int(row.monetary),
        )
        for row in conn.execute(query, params)
    ]
