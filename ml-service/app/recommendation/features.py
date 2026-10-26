"""Vector đặc trưng của sản phẩm (lọc theo nội dung, mục 1.1.2 của báo cáo).

Mỗi sản phẩm là 1 vector gồm 4 khối, mỗi khối chuẩn hóa độ dài về 1 rồi nhân với căn bậc hai trọng
số của khối, nên tích vô hướng của 2 vector xấp xỉ trung bình có trọng số độ tương đồng cosine của
từng khối:
- chữ: TF-IDF trên tên và mô tả (công thức (1.1)), từ đơn và cặp 2 từ liền nhau (tiếng Việt viết
  tách âm tiết, "thể thao" là 2 âm tiết);
- danh mục: one-hot;
- thương hiệu: one-hot (sản phẩm không có thương hiệu thì khối này bằng 0);
- tầm giá: 5 khoảng giá chia theo ngũ phân vị của giá thấp nhất, khoảng bên cạnh được 0,5 để hai
  sản phẩm giá gần nhau vẫn giống nhau một phần.
Cả vector được chuẩn hóa độ dài về 1, nên độ tương đồng cosine (công thức (1.2)) là tích vô hướng.
"""

import math
import unicodedata
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

import numpy as np
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.preprocessing import normalize

BLOCK_WEIGHTS = {"text": 0.45, "category": 0.30, "brand": 0.15, "price": 0.10}
"""Trọng số của từng khối trong độ tương đồng giữa 2 sản phẩm."""

PRICE_BUCKETS = 5
NEIGHBOUR_BUCKET_WEIGHT = 0.5

# Từ xuất hiện ở hầu hết sản phẩm, không giúp phân biệt sản phẩm (max_df lo phần còn lại).
STOP_WORDS = [
    "và",
    "cho",
    "của",
    "có",
    "với",
    "các",
    "là",
    "được",
    "trong",
    "khi",
    "không",
    "này",
    "một",
    "những",
    "sản",
    "phẩm",
    "hàng",
    "chính",
    "hãng",
    "cao",
    "cấp",
]


@dataclass(frozen=True)
class CatalogItem:
    """Thông tin 1 sản phẩm dùng để dựng vector."""

    id: int
    category_id: int
    brand_id: int | None
    name: str
    description: str
    price: int
    """Giá thấp nhất trong các biến thể."""


def normalize_text(value: str) -> str:
    """Chữ thường, dạng dựng sẵn (NFC) để chữ gõ tách dấu vẫn trùng với chữ dựng sẵn."""
    return unicodedata.normalize("NFC", value or "").lower()


class ItemVectors:
    """Ma trận vector của các sản phẩm và cách dựng vector sở thích từ khảo sát."""

    def __init__(self, items: Sequence[CatalogItem]) -> None:
        """Dựng vector cho mọi sản phẩm trong `items` (thứ tự hàng của ma trận theo `items`)."""
        if not items:
            raise ValueError("Không có sản phẩm nào để dựng vector")
        self.ids = np.array([item.id for item in items], dtype=np.int64)
        self.index = {item.id: row for row, item in enumerate(items)}
        self.category_ids = np.array([item.category_id for item in items], dtype=np.int64)
        self.brand_ids = np.array(
            [-1 if item.brand_id is None else item.brand_id for item in items], dtype=np.int64
        )
        self.prices = np.array([item.price for item in items], dtype=np.int64)

        self.vectorizer = TfidfVectorizer(
            preprocessor=normalize_text,
            token_pattern=r"(?u)\b[^\W\d_]{2,}\b",
            ngram_range=(1, 2),
            min_df=2,
            max_df=0.3,
            sublinear_tf=True,
            stop_words=STOP_WORDS,
        )
        text = self.vectorizer.fit_transform(f"{item.name} {item.description}" for item in items)

        self.category_columns = {cid: col for col, cid in enumerate(sorted(set(self.category_ids)))}
        self.brand_columns = {
            bid: col for col, bid in enumerate(sorted({b for b in self.brand_ids if b >= 0}))
        }
        log_prices = np.log10(np.maximum(self.prices, 1))
        self.price_edges = np.quantile(log_prices, np.linspace(0, 1, PRICE_BUCKETS + 1)[1:-1])

        blocks = {
            "text": text,
            "category": self._one_hot(
                [self.category_columns[c] for c in self.category_ids], len(self.category_columns)
            ),
            "brand": self._one_hot(
                [self.brand_columns.get(b) for b in self.brand_ids], len(self.brand_columns)
            ),
            "price": self._price_block(log_prices),
        }
        self.matrix = self._combine(blocks)

    # ---- các khối ----

    def _one_hot(self, columns: Iterable[int | None], width: int) -> sparse.csr_matrix:
        """Ma trận one-hot, mỗi hàng 1 cột (None = hàng toàn 0)."""
        rows, cols = [], []
        for row, col in enumerate(columns):
            if col is not None:
                rows.append(row)
                cols.append(col)
        return sparse.csr_matrix(
            (np.ones(len(rows)), (rows, cols)), shape=(len(self.ids), max(width, 1))
        )

    def price_bucket_weights(self, log_price: float) -> np.ndarray:
        """Trọng số của 5 khoảng giá cho 1 mức giá: khoảng chứa giá là 1, khoảng bên cạnh 0,5."""
        bucket = int(np.searchsorted(self.price_edges, log_price, side="right"))
        weights = np.zeros(PRICE_BUCKETS)
        weights[bucket] = 1.0
        for neighbour in (bucket - 1, bucket + 1):
            if 0 <= neighbour < PRICE_BUCKETS:
                weights[neighbour] = NEIGHBOUR_BUCKET_WEIGHT
        return weights

    def _price_block(self, log_prices: np.ndarray) -> sparse.csr_matrix:
        """Khối tầm giá của mọi sản phẩm."""
        return sparse.csr_matrix(np.vstack([self.price_bucket_weights(p) for p in log_prices]))

    @staticmethod
    def _combine(blocks: dict[str, sparse.spmatrix]) -> sparse.csr_matrix:
        """Chuẩn hóa từng khối, nhân căn bậc hai trọng số, ghép lại rồi chuẩn hóa cả vector."""
        scaled = [
            normalize(sparse.csr_matrix(blocks[name]), norm="l2") * math.sqrt(weight)
            for name, weight in BLOCK_WEIGHTS.items()
        ]
        return normalize(sparse.hstack(scaled, format="csr"), norm="l2")

    # ---- vector sở thích từ khảo sát ----

    def survey_vector(
        self,
        category_ids: Sequence[int],
        brand_id: int | None,
        price_min: int,
        price_max: int | None,
    ) -> np.ndarray | None:
        """Vector sở thích s(u) cùng dạng vector sản phẩm, từ các câu trả lời khảo sát.

        Khối chữ bằng 0; khối danh mục chia đều cho các danh mục đã chọn; khối thương hiệu là
        thương hiệu đã chọn (nếu có); khối giá là các khoảng giá giao với tầm giá đã chọn. Trả về
        None nếu không có danh mục nào còn trong danh mục sản phẩm.
        """
        categories = [self.category_columns[c] for c in category_ids if c in self.category_columns]
        if not categories:
            return None
        category = np.zeros(max(len(self.category_columns), 1))
        category[categories] = 1.0
        brand = np.zeros(max(len(self.brand_columns), 1))
        if brand_id is not None and brand_id in self.brand_columns:
            brand[self.brand_columns[brand_id]] = 1.0
        price = self._price_range_weights(price_min, price_max)
        text = np.zeros(len(self.vectorizer.vocabulary_))
        blocks = {"text": text, "category": category, "brand": brand, "price": price}
        parts = []
        for name, weight in BLOCK_WEIGHTS.items():
            block = blocks[name]
            norm = np.linalg.norm(block)
            parts.append(block / norm * math.sqrt(weight) if norm > 0 else block)
        vector = np.concatenate(parts)
        return vector / np.linalg.norm(vector)

    def _price_range_weights(self, price_min: int, price_max: int | None) -> np.ndarray:
        """Khoảng giá giao với [price_min, price_max] được 1; không giới hạn trên thì tới cuối."""
        low = int(np.searchsorted(self.price_edges, math.log10(max(price_min, 1)), side="right"))
        high = (
            PRICE_BUCKETS - 1
            if price_max is None
            else int(np.searchsorted(self.price_edges, math.log10(max(price_max, 1)), side="right"))
        )
        weights = np.zeros(PRICE_BUCKETS)
        weights[low : high + 1] = 1.0
        return weights
