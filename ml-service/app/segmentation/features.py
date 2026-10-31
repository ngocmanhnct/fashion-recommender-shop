"""Thử thêm đặc trưng ngoài R, F, M cho K-Means và đo xem có giúp tách kiểu khách tốt hơn không.

Góp ý 30/10/2026: thêm đặc trưng ngoài R, F, M cũng được nếu nó có tác động. Module này tính
thêm vài đặc trưng hay gặp trong tài liệu phân nhóm khách hàng rồi chạy đúng quy trình của
`model.train` (ln(1 + x), chuẩn hóa z, K-Means, chọn K bằng Elbow + Silhouette) cho từng bộ đặc
trưng, so nhóm tìm được với kiểu khách mô phỏng bằng ARI. Chỉ để đo, mô hình thật vẫn dùng R, F, M.

- AOV: giá trị trung bình mỗi đơn = M / F.
- Giá TB mỗi món: tổng tiền hàng / tổng số món, để tách khách hay mua món rẻ.
- L (LRFM): thời gian gắn bó, số ngày từ đơn Đã giao đầu tiên đến đơn gần nhất.
- Khoảng cách mua: L / (F - 1), số ngày trung bình giữa 2 đơn (0 khi chỉ có 1 đơn).
- Lượt xem: số sản phẩm khác nhau đã xem trong 90 ngày.
"""

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime, timedelta

import numpy as np
from sklearn.metrics import adjusted_rand_score
from sqlalchemy import Connection, text

from app.segmentation.model import search_k, standardize
from app.segmentation.rfm import SECONDS_PER_DAY, recency_days

VIEW_WINDOW_DAYS = 90


@dataclass(frozen=True)
class CustomerFeatures:
    """Các đặc trưng của 1 khách có đơn Đã giao."""

    account_id: int
    recency: float
    frequency: float
    monetary: float
    avg_order_value: float
    avg_item_price: float
    tenure: float
    purchase_gap: float
    viewed_products: float


FEATURE_LABELS = {
    "recency": "R",
    "frequency": "F",
    "monetary": "M",
    "avg_order_value": "AOV",
    "avg_item_price": "giá TB/món",
    "tenure": "L",
    "purchase_gap": "khoảng cách mua",
    "viewed_products": "lượt xem 90 ngày",
}

RFM = ("recency", "frequency", "monetary")
FEATURE_SETS: tuple[tuple[str, ...], ...] = (
    RFM,
    (*RFM, "avg_order_value"),
    (*RFM, "avg_item_price"),
    ("tenure", *RFM),
    (*RFM, "purchase_gap"),
    (*RFM, "viewed_products"),
    ("recency", "frequency", "avg_order_value"),
    ("recency", "frequency", "avg_item_price"),
    (*RFM, "avg_order_value", "avg_item_price"),
)
"""Các bộ đặc trưng đem so; bộ đầu là bộ đang dùng (R, F, M)."""

_SELECT = text(
    """
    SELECT o.account_id, MIN(o.created_at) AS first_at, MAX(o.created_at) AS last_at,
           COUNT(*) AS frequency, SUM(o.total_amount) AS monetary,
           SUM(i.quantity) AS items, SUM(i.goods) AS goods
    FROM orders o
    JOIN account a ON a.id = o.account_id AND a.role = 'CUSTOMER'
    JOIN (SELECT order_id, SUM(quantity) AS quantity, SUM(line_total) AS goods
          FROM order_item GROUP BY order_id) i ON i.order_id = o.id
    WHERE o.status = 'DELIVERED'
    GROUP BY o.account_id
    ORDER BY o.account_id
    """
)
_VIEWS = text(
    """
    SELECT account_id, COUNT(DISTINCT product_id) AS viewed
    FROM product_view_log
    WHERE viewed_at >= :since AND account_id IS NOT NULL
    GROUP BY account_id
    """
)


def load_features(conn: Connection, now: datetime) -> list[CustomerFeatures]:
    """Đặc trưng của mọi khách có đơn Đã giao (đơn không có dòng hàng nào thì không tính)."""
    since = now - timedelta(days=VIEW_WINDOW_DAYS)
    viewed = {int(r.account_id): int(r.viewed) for r in conn.execute(_VIEWS, {"since": since})}
    result = []
    for row in conn.execute(_SELECT):
        frequency = int(row.frequency)
        monetary = float(row.monetary)
        tenure = max(0.0, (row.last_at - row.first_at).total_seconds() / SECONDS_PER_DAY)
        result.append(
            CustomerFeatures(
                account_id=int(row.account_id),
                recency=recency_days(row.last_at, now),
                frequency=frequency,
                monetary=monetary,
                avg_order_value=monetary / frequency,
                avg_item_price=float(row.goods) / max(1, int(row.items)),
                tenure=tenure,
                purchase_gap=tenure / (frequency - 1) if frequency > 1 else 0.0,
                viewed_products=viewed.get(int(row.account_id), 0),
            )
        )
    return result


@dataclass(frozen=True)
class FeatureSetResult:
    """Kết quả 1 bộ đặc trưng."""

    features: tuple[str, ...]
    k: int
    silhouette: float
    ari: float

    @property
    def label(self) -> str:
        """Tên bộ đặc trưng, vd "R, F, M + AOV"."""
        return ", ".join(FEATURE_LABELS[f] for f in self.features)


def compare(
    rows: Sequence[CustomerFeatures],
    personas: dict[int, str],
    feature_sets: Sequence[tuple[str, ...]] = FEATURE_SETS,
) -> list[FeatureSetResult]:
    """Chạy quy trình phân nhóm cho từng bộ đặc trưng trên các khách biết kiểu thật; bỏ qua bộ
    không đủ dữ liệu để chia nhóm."""
    labelled = [r for r in rows if r.account_id in personas]
    if not labelled:
        return []
    truth = [personas[r.account_id] for r in labelled]
    results = []
    for features in feature_sets:
        x = np.log1p(np.array([[getattr(r, f) for f in features] for r in labelled], dtype=float))
        search = search_k(standardize(x.reshape(len(labelled), len(features)))[0])
        if search is None:
            continue
        silhouette = next(s.silhouette for s in search.scores if s.k == search.k)
        ari = adjusted_rand_score(truth, search.fits[search.k].labels_)
        results.append(FeatureSetResult(features, search.k, silhouette or 0.0, float(ari)))
    return results


def report(results: Sequence[FeatureSetResult]) -> str:
    """Bảng Markdown: bộ đặc trưng, K chọn, Silhouette, ARI và chênh lệch so với R, F, M."""
    base = next((r.ari for r in results if r.features == RFM), None)
    lines = [
        "| Đặc trưng | K | Silhouette | ARI | So với R, F, M |",
        "|---|---:|---:|---:|---:|",
    ]
    for r in results:
        diff = "" if base is None or r.features == RFM else f"{r.ari - base:+.3f}"
        lines.append(f"| {r.label} | {r.k} | {r.silhouette:.3f} | {r.ari:.3f} | {diff} |")
    return "\n".join(lines)
