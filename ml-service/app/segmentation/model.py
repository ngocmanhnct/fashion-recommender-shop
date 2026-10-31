"""Huấn luyện K-Means trên R, F, M, chọn số nhóm K, đặt tên nhóm và gán khách vào nhóm.

Các bước (trinh-bay/cap-nhat-goi-y-va-phan-nhom.md, mục 4):
1. Đặc trưng: x = ln(1 + R), ln(1 + F), ln(1 + M). F và M lệch phải mạnh (ít khách mua rất
   nhiều), lấy log để vài khách VIP không kéo lệch tâm nhóm.
2. Chuẩn hóa z = (x - mean) / std để 3 đặc trưng có cùng thang đo.
3. Chạy K-Means với K = 1..8; Elbow tìm điểm gãy của đường inertia, Silhouette đo độ tách nhóm.
   Chọn K lớn nhất trong 3..6, không nhỏ hơn điểm gãy, có Silhouette từ 0,3 (KPI của US-19) và
   kém Silhouette tốt nhất không quá 0,05. Điểm gãy là số nhóm tối thiểu để nắm được cấu trúc
   chính; nhiều nhóm hơn thì chăm sóc khách sát hơn (vd tách Khách VIP khỏi Khách thân thiết),
   miễn là các nhóm vẫn tách bạch. Không K nào đạt thì lấy K có Silhouette cao nhất.
4. Đặt tên: ghép mỗi tâm nhóm với 1 kiểu khách mẫu (bảng SEGMENT_TYPES) bằng thuật toán Hungary,
   tổng khoảng cách nhỏ nhất, nên 2 nhóm không bao giờ trùng tên.
5. Gán khách mới: tính z bằng mean, std của lần huấn luyện này rồi chọn tâm gần nhất (BR-23).
"""

import math
from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
from scipy.optimize import linear_sum_assignment
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

from app.segmentation.rfm import Rfm

MIN_CUSTOMERS = 20
"""Ít khách hơn thì không huấn luyện: vài điểm dữ liệu không đủ để nói về "nhóm khách"."""

MIN_CLUSTERS = 3
MAX_CLUSTERS = 6
"""Số nhóm cho phép: dưới 3 nhóm không giúp gì cho tiếp thị, trên 6 nhóm admin khó theo dõi."""

ELBOW_MAX_CLUSTERS = 8
MIN_SILHOUETTE = 0.3
SILHOUETTE_TOLERANCE = 0.05
SILHOUETTE_SAMPLE = 5000
"""Silhouette cần khoảng cách giữa mọi cặp điểm (O(n²)); nhiều khách thì tính trên mẫu."""

RANDOM_STATE = 42
N_INIT = 10


@dataclass(frozen=True)
class SegmentType:
    """Một kiểu khách mẫu: tên, ý nghĩa, gợi ý chăm sóc và vị trí mẫu trong không gian z."""

    code: str
    name: str
    meaning: str
    action: str
    prototype: tuple[float, float, float]  # (R, F, M) dạng z; R lớn = lâu rồi không mua


SEGMENT_TYPES: tuple[SegmentType, ...] = (
    SegmentType(
        "VIP",
        "Khách VIP",
        "Mua nhiều lần, chi tiêu cao nhất và vẫn mua gần đây.",
        "Ưu tiên chăm sóc, tặng quà tri ân, mời dùng thử sản phẩm mới.",
        (-1.0, 1.5, 1.5),
    ),
    SegmentType(
        "LOYAL",
        "Khách thân thiết",
        "Mua đều đặn, chi tiêu khá.",
        "Tích điểm, ưu đãi theo mốc số đơn để giữ thói quen mua.",
        (-0.7, 0.8, 0.4),
    ),
    SegmentType(
        "SAVER",
        "Khách tiết kiệm",
        "Mua khá thường xuyên nhưng giá trị mỗi đơn thấp.",
        "Gửi mã giảm giá, combo giá tốt, gợi ý sản phẩm bán chạy giá mềm.",
        (-0.4, 0.4, -0.6),
    ),
    SegmentType(
        "NEW",
        "Khách mới",
        "Mới mua 1-2 đơn trong thời gian gần đây.",
        "Chào mừng, gợi ý sản phẩm liên quan đơn đầu để có đơn thứ hai.",
        (-1.0, -1.0, -1.0),
    ),
    SegmentType(
        "AT_RISK",
        "Khách có nguy cơ rời bỏ",
        "Từng mua khá nhiều nhưng đã lâu chưa quay lại.",
        "Nhắc lại bằng ưu đãi riêng, hỏi lý do chưa quay lại.",
        (0.9, 0.4, 0.4),
    ),
    SegmentType(
        "LOST",
        "Khách đã lâu không mua",
        "Mua ít và lần mua cuối đã rất lâu.",
        "Chiến dịch kéo lại với ưu đãi lớn; không ưu tiên nếu ngân sách hạn chế.",
        (1.3, -0.8, -0.8),
    ),
)

assert len(SEGMENT_TYPES) >= MAX_CLUSTERS  # mỗi nhóm cần 1 tên riêng


def log_features(rows: Sequence[Rfm]) -> np.ndarray:
    """Ma trận n × 3 các giá trị ln(1 + R), ln(1 + F), ln(1 + M)."""
    raw = np.array([[r.recency, r.frequency, r.monetary] for r in rows], dtype=float)
    return np.log1p(raw.reshape(-1, 3))


@dataclass(frozen=True)
class Segment:
    """Một nhóm khách sau khi huấn luyện."""

    code: str
    name: str
    description: str
    center: tuple[float, float, float]  # tâm nhóm dạng z
    size: int
    mean_recency: float
    mean_frequency: float
    mean_monetary: float


@dataclass(frozen=True)
class KScore:
    """Kết quả thử 1 giá trị K."""

    k: int
    inertia: float
    silhouette: float | None  # None khi K = 1


@dataclass(frozen=True)
class SegmentModel:
    """Mô hình đã huấn luyện: tham số chuẩn hóa và các nhóm (theo thứ tự tâm)."""

    mean: tuple[float, float, float]
    std: tuple[float, float, float]
    segments: tuple[Segment, ...]
    silhouette: float
    elbow_k: int
    customer_count: int
    scores: tuple[KScore, ...] = ()

    def scale(self, rows: Sequence[Rfm]) -> np.ndarray:
        """Đặc trưng dạng z theo tham số chuẩn hóa của mô hình."""
        return (log_features(rows) - np.array(self.mean)) / np.array(self.std)

    def assign(self, rows: Sequence[Rfm]) -> list[Segment]:
        """Nhóm có tâm gần nhất (khoảng cách Euclid trong không gian z) cho từng khách."""
        if not rows:
            return []
        centers = np.array([s.center for s in self.segments])
        distances = np.linalg.norm(self.scale(rows)[:, None, :] - centers[None, :, :], axis=2)
        return [self.segments[i] for i in distances.argmin(axis=1)]


def elbow_point(scores: Sequence[KScore]) -> int:
    """Điểm gãy của đường inertia theo K (phương pháp "kneedle"): điểm xa nhất so với đoạn thẳng
    nối điểm đầu và điểm cuối, sau khi đưa cả 2 trục về [0, 1]."""
    ks = np.array([s.k for s in scores], dtype=float)
    inertia = np.array([s.inertia for s in scores], dtype=float)
    if len(ks) < 3 or inertia[0] == inertia[-1]:
        return int(ks[0])
    x = (ks - ks[0]) / (ks[-1] - ks[0])
    y = (inertia - inertia[-1]) / (inertia[0] - inertia[-1])
    # Đoạn thẳng nối (0, 1) và (1, 0) là y = 1 - x; inertia giảm dần nên đường cong nằm dưới.
    return int(ks[int(np.argmax((1 - x) - y))])


def choose_k(scores: Sequence[KScore], elbow_k: int) -> int:
    """K lớn nhất không nhỏ hơn điểm gãy có Silhouette từ MIN_SILHOUETTE và kém tốt nhất không
    quá SILHOUETTE_TOLERANCE; không có thì K có Silhouette cao nhất."""
    candidates = [s for s in scores if s.silhouette is not None]
    best = max(s.silhouette for s in candidates)
    floor = max(MIN_SILHOUETTE, best - SILHOUETTE_TOLERANCE)
    good = [s for s in candidates if s.k >= elbow_k and s.silhouette >= floor]
    if good:
        return max(s.k for s in good)
    return max(candidates, key=lambda s: s.silhouette).k


def name_segments(centers: np.ndarray) -> list[SegmentType]:
    """Ghép mỗi tâm với 1 kiểu khách mẫu khác nhau, tổng khoảng cách nhỏ nhất (Hungary)."""
    prototypes = np.array([t.prototype for t in SEGMENT_TYPES])
    cost = np.linalg.norm(centers[:, None, :] - prototypes[None, :, :], axis=2)
    rows, cols = linear_sum_assignment(cost)
    chosen = dict(zip(rows.tolist(), cols.tolist(), strict=True))
    return [SEGMENT_TYPES[chosen[i]] for i in range(len(centers))]


def format_money(amount: float) -> str:
    """Số tiền làm tròn đến nghìn đồng, dấu chấm ngăn cách hàng nghìn (vd 1.250.000đ)."""
    return f"{round(amount / 1000) * 1000:,.0f}đ".replace(",", ".")


def describe(
    segment_type: SegmentType, size: int, recency: float, freq: float, money: float
) -> str:
    """Mô tả nhóm cho admin (US-19): ý nghĩa, số liệu trung bình và gợi ý chăm sóc."""
    frequency = f"{freq:.1f}".replace(".", ",")
    return (
        f"{segment_type.meaning} {size} khách; trung bình {frequency} đơn đã giao, tổng chi "
        f"{format_money(money)}, lần mua gần nhất cách đây {round(recency)} ngày. "
        f"Gợi ý: {segment_type.action}"
    )


def _fit(z: np.ndarray, k: int) -> KMeans:
    """K-Means với seed cố định để huấn luyện lại trên cùng dữ liệu ra cùng kết quả."""
    return KMeans(n_clusters=k, n_init=N_INIT, random_state=RANDOM_STATE).fit(z)


@dataclass(frozen=True)
class KSearch:
    """Kết quả thử các giá trị K trên 1 ma trận đặc trưng đã chuẩn hóa."""

    scores: tuple[KScore, ...]
    fits: dict[int, KMeans]  # chỉ các K trong khoảng cho phép
    elbow_k: int
    k: int  # K đã chọn


def search_k(z: np.ndarray) -> KSearch | None:
    """Chạy K-Means với K = 1..8 và chọn K (Elbow + Silhouette, bước 3). None nếu số điểm khác
    nhau không đủ cho 2 nhóm."""
    distinct = len(np.unique(z, axis=0))
    max_k = min(MAX_CLUSTERS, distinct - 1)
    if max_k < 2:
        return None
    min_k = min(MIN_CLUSTERS, max_k)
    sample = min(len(z), SILHOUETTE_SAMPLE)
    scores: list[KScore] = []
    fits: dict[int, KMeans] = {}
    for k in range(1, min(ELBOW_MAX_CLUSTERS, distinct) + 1):
        fit = _fit(z, k)
        silhouette = None
        if min_k <= k <= max_k:
            fits[k] = fit
            silhouette = float(
                silhouette_score(z, fit.labels_, sample_size=sample, random_state=RANDOM_STATE)
            )
        scores.append(KScore(k, float(fit.inertia_), silhouette))
    elbow_k = elbow_point(scores)
    return KSearch(tuple(scores), fits, elbow_k, choose_k(scores, elbow_k))


def standardize(x: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """z = (x - mean) / std theo từng cột; cột mọi khách cùng giá trị thành 0. Trả về z, mean, std."""
    mean = x.mean(axis=0)
    std = x.std(axis=0)
    std[std == 0] = 1.0
    return (x - mean) / std, mean, std


def train(rows: Sequence[Rfm]) -> SegmentModel | None:
    """Huấn luyện trên R, F, M của mọi khách có đơn Đã giao. None nếu chưa đủ dữ liệu: ít hơn
    MIN_CUSTOMERS khách, hoặc số điểm khác nhau không đủ cho 2 nhóm."""
    if len(rows) < MIN_CUSTOMERS:
        return None
    z, mean, std = standardize(log_features(rows))
    search = search_k(z)
    if search is None:
        return None
    scores, elbow_k, k = search.scores, search.elbow_k, search.k
    fit = search.fits[k]
    types = name_segments(fit.cluster_centers_)
    raw = np.array([[r.recency, r.frequency, r.monetary] for r in rows], dtype=float)
    segments = []
    for i, segment_type in enumerate(types):
        members = raw[fit.labels_ == i]
        recency, frequency, monetary = members.mean(axis=0)
        segments.append(
            Segment(
                code=segment_type.code,
                name=segment_type.name,
                description=describe(segment_type, len(members), recency, frequency, monetary),
                center=tuple(float(c) for c in fit.cluster_centers_[i]),
                size=len(members),
                mean_recency=float(recency),
                mean_frequency=float(frequency),
                mean_monetary=float(monetary),
            )
        )
    silhouette = next(s.silhouette for s in scores if s.k == k)
    return SegmentModel(
        mean=tuple(float(v) for v in mean),
        std=tuple(float(v) for v in std),
        segments=tuple(segments),
        silhouette=silhouette if silhouette is not None and math.isfinite(silhouette) else 0.0,
        elbow_k=elbow_k,
        customer_count=len(rows),
        scores=tuple(scores),
    )
