"""Giữ mô hình gợi ý trong bộ nhớ, dựng lại khi dữ liệu đổi.

- Vector sản phẩm (TF-IDF, mất khoảng nửa giây với 1.000 sản phẩm) dựng lại khi danh mục sản
  phẩm đổi: thêm, sửa, xóa sản phẩm hay biến thể, hoặc giá thấp nhất của sản phẩm đổi.
- Tồn kho, số đã bán, lượt bán theo thời gian (cho điểm "đang bán chạy", tính tại lúc gợi ý nên
  không cần dựng lại khi thời gian trôi), điểm đánh giá và ma trận "hay mua cùng" (vài chục mili
  giây) dựng lại khi có đơn hàng, đánh giá hay tồn kho đổi.
- Mô hình xếp hạng (ranker.py) học lại khi gọi `train_ranker` (mỗi đêm và lúc khởi động, trong
  tiến trình lấy việc), không học lại sau mỗi đơn: học mất vài giây, còn API phải trả lời ngay.
Kiểm tra dấu hiệu thay đổi tối đa mỗi CHECK_INTERVAL_SECONDS giây. Tiến trình lấy việc và các
request API dùng chung 1 bộ đệm, nên việc dựng lại được khóa; mô hình đã dựng không bao giờ sửa,
chỉ thay bằng bản mới.
"""

import logging
import threading
import time
from datetime import datetime

from sqlalchemy import Connection

from app.recommendation import data, ranker
from app.recommendation.copurchase import build_copurchase
from app.recommendation.features import CatalogItem, ItemVectors
from app.recommendation.model import Recommender
from app.recommendation.ranker import Ranker
from app.recommendation.trending import SalesHistory

logger = logging.getLogger(__name__)

CHECK_INTERVAL_SECONDS = 5.0


class ModelCache:
    """Bộ đệm mô hình gợi ý dùng chung trong 1 tiến trình ml-service."""

    def __init__(self, check_interval_seconds: float = CHECK_INTERVAL_SECONDS) -> None:
        """Bộ đệm rỗng; mô hình dựng ở lần gọi `get` đầu tiên."""
        self.check_interval_seconds = check_interval_seconds
        self._lock = threading.Lock()
        self._model: Recommender | None = None
        self._items: list[CatalogItem] = []
        self._vectors: ItemVectors | None = None
        self._catalog_signature: tuple | None = None
        self._activity_signature: tuple | None = None
        self._checked_at = float("-inf")
        self._ranker: Ranker | None = None
        self.built_at: float | None = None

    def get(self, conn: Connection) -> Recommender | None:
        """Mô hình hiện tại, dựng lại trước nếu dữ liệu đã đổi; None nếu chưa có sản phẩm nào."""
        with self._lock:
            now = time.monotonic()
            if now - self._checked_at < self.check_interval_seconds:
                return self._model
            self._refresh(conn)
            self._checked_at = now
            return self._model

    def train_ranker(self, conn: Connection, now: datetime) -> Ranker | None:
        """Học lại mô hình xếp hạng từ lịch sử đơn đến `now` (giờ địa phương như cột DATETIME);
        chưa đủ dữ liệu thì bỏ mô hình cũ, gợi ý theo hành vi dùng công thức hybrid."""
        with self._lock:
            self._refresh(conn)
            vectors, items = self._vectors, self._items
        if vectors is None:
            return None
        started = time.perf_counter()
        learned = ranker.train(
            vectors, items, data.load_orders(conn), data.load_ages(conn, now), now
        )
        with self._lock:
            self._ranker = learned
            self._activity_signature = None  # lần `get` sau dựng lại mô hình kèm bộ xếp hạng mới
            self._checked_at = float("-inf")
        if learned is None:
            logger.info("Chưa đủ dữ liệu để học mô hình xếp hạng, gợi ý dùng công thức hybrid")
        else:
            logger.info(
                "Đã học mô hình xếp hạng: %d tuần, %d khách, %d lượt mua trúng ứng viên (%.0f ms)",
                learned.summary.weeks,
                learned.summary.cases,
                learned.summary.positives,
                (time.perf_counter() - started) * 1000,
            )
        return learned

    def invalidate(self) -> None:
        """Bắt lần `get` sau dựng lại toàn bộ (vd mỗi đêm)."""
        with self._lock:
            self._catalog_signature = None
            self._activity_signature = None
            self._checked_at = float("-inf")

    def _refresh(self, conn: Connection) -> None:
        """Dựng lại phần đã đổi."""
        catalog_signature = data.catalog_signature(conn)
        activity_signature = data.activity_signature(conn)
        if (
            catalog_signature == self._catalog_signature
            and activity_signature == self._activity_signature
        ):
            return
        started = time.perf_counter()
        stats = data.load_stats(conn)
        rebuilt_vectors = False
        if (
            self._vectors is None
            or catalog_signature != self._catalog_signature
            or self._prices_changed(stats)
        ):
            items = data.load_catalog(conn)
            if not items:
                self._model, self._vectors, self._items = None, None, []
                self._catalog_signature = catalog_signature
                self._activity_signature = activity_signature
                return
            self._vectors = ItemVectors(items)
            self._items = items
            rebuilt_vectors = True
        copurchase = build_copurchase(
            data.load_purchase_pairs(conn), self._vectors.index, len(self._items)
        )
        bands = ranker.age_bands(data.load_ages(conn, datetime.now()))
        sales = SalesHistory(data.load_sales(conn), self._vectors.index, len(self._items), bands)
        self._model = Recommender(
            self._vectors,
            self._items,
            stats,
            copurchase,
            data.load_brand_names(conn),
            sales=sales,
            ranker=self._ranker,
        )
        self._catalog_signature = catalog_signature
        self._activity_signature = activity_signature
        self.built_at = time.time()
        logger.info(
            "Đã dựng mô hình gợi ý (%d sản phẩm%s%s) trong %.0f ms",
            len(self._items),
            ", dựng lại vector" if rebuilt_vectors else "",
            ", có mô hình xếp hạng" if self._ranker is not None else "",
            (time.perf_counter() - started) * 1000,
        )

    def _prices_changed(self, stats: dict[int, data.ProductStats]) -> bool:
        """Giá thấp nhất của sản phẩm nào đó đã khác lúc dựng vector (khối tầm giá cũ)."""
        return any(item.id in stats and stats[item.id].price != item.price for item in self._items)
