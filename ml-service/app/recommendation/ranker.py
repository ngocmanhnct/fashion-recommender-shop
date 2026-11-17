"""Mô hình xếp hạng học từ lịch sử mua (learning to rank) cho gợi ý trang chủ.

Công thức hybrid (model.py) cộng các nguồn gợi ý với trọng số cố định. Mô hình ở đây HỌC cách kết
hợp từ chính lịch sử mua của cửa hàng:

1. Ứng viên: với mỗi khách, gom khoảng 250 sản phẩm được phép gợi ý từ 6 nguồn: đang bán chạy
   (D(j) chu kỳ 7 ngày), đang bán chạy trong nhóm tuổi của khách, đang bán chạy trong từng danh
   mục khách hay mua (danh mục chiếm phần lớn lượt mua của khách thì lấy nhiều sản phẩm hơn), giống
   sản phẩm khách đã tương tác (nội dung), hay mua cùng, và điểm công thức hybrid cao nhất.
2. Đặc trưng (FEATURES, 28 số): sản phẩm (bán chạy theo nhiều chu kỳ, mới hay cũ, giá), bán chạy
   trong nhóm tuổi của khách, khách (tuổi, số đơn, bao lâu chưa mua) và độ hợp giữa khách với sản
   phẩm (nội dung, hay mua cùng, điểm công thức hybrid, tỷ lệ danh mục, giá so với giá khách hay
   mua).
3. Học: lấy TRAINING_WEEKS mốc, mỗi mốc lùi 1 tuần từ lúc huấn luyện. Tại mỗi mốc dựng mô hình
   chỉ từ đơn trước mốc, lấy ứng viên và đặc trưng của các khách có mua sản phẩm mới trong 7 ngày
   sau mốc; nhãn = khách đó có mua sản phẩm đó trong 7 ngày đó không. Cây quyết định tăng cường
   độ dốc (HistGradientBoostingClassifier của scikit-learn) học xác suất mua; gợi ý là các ứng
   viên có xác suất cao nhất.
4. Chưa đủ dữ liệu (dưới MIN_POSITIVES lượt mua trúng ứng viên trong các tuần học) thì không có
   mô hình xếp hạng: gợi ý theo hành vi dùng công thức hybrid như trước.

Mô hình học lại mỗi đêm (cache.py); số liệu bán hàng và tương tác của khách lấy mới nhất lúc gợi
ý. Kiểm chứng trên lịch sử mua thật của H&M: scripts/eval_hm.py, data/hm/ket-qua.md.
"""

import math
from collections import Counter, defaultdict
from collections.abc import Iterable, Iterator, Mapping, Sequence
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Protocol

import numpy as np
from sklearn.ensemble import HistGradientBoostingClassifier

from app.recommendation.copurchase import build_copurchase
from app.recommendation.data import ProductStats, UserSignals
from app.recommendation.features import CatalogItem, ItemVectors
from app.recommendation.model import BehaviourParts, Recommender
from app.recommendation.trending import NEVER_SOLD_DAYS, SalesHistory, SalesSnapshot, age_band

TRAINING_WEEKS = 5
"""Số tuần học (thử 3 và 5 trên các tuần kiểm định của H&M: 5 tuần tốt hơn một chút)."""
LABEL_DAYS = 7
"""Nhãn: mua trong chừng này ngày sau mốc (như cách chấm của cuộc thi H&M)."""
CASES_PER_WEEK = 10_000
"""Tối đa số khách mỗi tuần học (chọn ngẫu nhiên, seed cố định), giữ bộ nhớ vừa phải."""
NEGATIVE_RATE = 0.3
"""Giữ chừng này phần ứng viên khách không mua khi học (giữ mọi ứng viên khách đã mua)."""
MIN_POSITIVES = 100
"""Số lượt mua trúng ứng viên tối thiểu trong các tuần học để học mô hình."""

POOL_TREND = 100
POOL_AGE = 50
POOL_CATEGORY = 80
"""Chia theo tỷ lệ danh mục trong lượt mua gần đây của khách (làm tròn lên)."""
POOL_CONTENT = 50
POOL_COPURCHASE = 50
POOL_HYBRID = 50

CATEGORY_HALF_LIFE_DAYS = 30.0
"""Tỷ lệ danh mục của khách: lượt mua chừng này ngày trước nặng bằng nửa lượt mua hôm nay."""

FEATURES = (
    "trend_1d",
    "trend_3d",
    "trend_7d",
    "trend_14d",
    "sold_7d",
    "sold_prev_7d",
    "sold_all",
    "growth_7d",
    "buyers_7d",
    "days_since_first_sale",
    "days_since_last_sale",
    "log_price",
    "price_rank_in_category",
    "category_trend_7d",
    "rank_in_category",
    "rank_overall",
    "age_band_trend_7d",
    "age_band_lift",
    "customer_age",
    "customer_orders",
    "customer_products",
    "days_since_last_purchase",
    "content",
    "content_last_order",
    "copurchase",
    "hybrid_score",
    "category_share",
    "price_vs_customer",
)
"""Tên đặc trưng theo đúng thứ tự cột của `feature_matrix`."""

FEATURE_GROUPS = {
    "Đang bán chạy (theo thời gian)": (
        "trend_1d",
        "trend_3d",
        "trend_7d",
        "trend_14d",
        "sold_7d",
        "sold_prev_7d",
        "growth_7d",
        "buyers_7d",
        "category_trend_7d",
        "rank_in_category",
        "rank_overall",
    ),
    "Sản phẩm mới hay cũ": ("days_since_first_sale", "days_since_last_sale", "sold_all"),
    "Nhóm tuổi": ("age_band_trend_7d", "age_band_lift", "customer_age"),
    "Giống sản phẩm khách đã mua (nội dung)": (
        "content",
        "content_last_order",
        "category_share",
    ),
    "Hay mua cùng": ("copurchase",),
    "Điểm công thức hybrid": ("hybrid_score",),
    "Giá": ("log_price", "price_rank_in_category", "price_vs_customer"),
    "Thói quen của khách": ("customer_orders", "customer_products", "days_since_last_purchase"),
}
"""Nhóm đặc trưng để đo mức đóng góp (eval_hm.py); mỗi đặc trưng thuộc đúng 1 nhóm."""

SECONDS_PER_DAY = 86_400


class PastOrder(Protocol):
    """Phần cần dùng của 1 đơn khác CANCELLED (data.Order hoặc đơn dựng từ dữ liệu H&M)."""

    account_id: int
    created_at: datetime
    status: str
    products: dict[int, int]


@dataclass(frozen=True)
class Case:
    """1 khách tại 1 mốc: dữ liệu trước mốc và các sản phẩm mới khách mua sau mốc."""

    signals: UserSignals
    targets: set[int]


# ---- ứng viên và đặc trưng ----


def _first_allowed(order: np.ndarray, allowed: np.ndarray, limit: int) -> np.ndarray:
    """`limit` hàng đầu tiên của `order` được phép gợi ý."""
    return order[allowed[order]][:limit]


def _best_allowed(values: np.ndarray, allowed: np.ndarray, limit: int) -> np.ndarray:
    """Tối đa `limit` hàng được phép có giá trị dương lớn nhất."""
    rows = np.flatnonzero(allowed & (values > 0))
    if rows.size > limit:
        rows = rows[np.argpartition(-values[rows], limit - 1)[:limit]]
    return rows


def category_share(model: Recommender, signals: UserSignals) -> np.ndarray:
    """Tỷ lệ từng danh mục trong các sản phẩm khách đã mua, lượt mua gần đây nặng hơn."""
    share = np.zeros(model.category_count)
    if signals.now is None:
        return share
    for product_id, at in signals.purchased_at.items():
        row = model.vectors.index.get(product_id)
        if row is None:
            continue
        age = max((signals.now - at).total_seconds(), 0.0) / SECONDS_PER_DAY
        share[model.category_index[row]] += 0.5 ** (age / CATEGORY_HALF_LIFE_DAYS)
    total = share.sum()
    return share / total if total > 0 else share


def candidates(
    model: Recommender, snapshot: SalesSnapshot, signals: UserSignals, parts: BehaviourParts
) -> np.ndarray:
    """Hàng các sản phẩm ứng viên (tăng dần, không trùng), chỉ sản phẩm được phép gợi ý."""
    allowed = parts.allowed
    pools = [_first_allowed(snapshot.by_trend, allowed, POOL_TREND)]
    band = age_band(signals.age)
    if band >= 0:
        pools.append(_first_allowed(snapshot.by_age_band[band], allowed, POOL_AGE))
    share = category_share(model, signals)
    for category in np.flatnonzero(share > 0):
        size = math.ceil(POOL_CATEGORY * share[category])
        pools.append(_first_allowed(snapshot.by_category[category], allowed, size))
    pools.append(_best_allowed(parts.content, allowed, POOL_CONTENT))
    pools.append(_best_allowed(parts.copurchase, allowed, POOL_COPURCHASE))
    pools.append(_best_allowed(parts.base, allowed, POOL_HYBRID))
    return np.unique(np.concatenate(pools)).astype(np.int64)


def feature_matrix(
    model: Recommender,
    snapshot: SalesSnapshot,
    signals: UserSignals,
    parts: BehaviourParts,
    rows: np.ndarray,
) -> np.ndarray:
    """Ma trận đặc trưng (theo FEATURES) của các sản phẩm ở hàng `rows` với khách `signals`."""
    size = len(rows)
    weekly = snapshot.decayed[7.0]
    band = age_band(signals.age)
    if band >= 0:
        in_band = snapshot.age_band_trend[band]
        band_trend = np.log1p(in_band[rows])
        # Tỷ lệ của sản phẩm trong lượt mua của nhóm tuổi so với tỷ lệ trên mọi khách (log).
        band_lift = np.log(
            ((in_band[rows] + 0.5) / (in_band.sum() + 1.0))
            / ((weekly[rows] + 0.5) / (weekly.sum() + 1.0))
        )
    else:
        band_trend = np.log1p(weekly[rows])
        band_lift = np.zeros(size)

    index = model.vectors.index
    bought = [index[p] for p in signals.purchased if p in index]
    times = {p: at for p, at in signals.purchased_at.items() if p in index}
    last_order = np.zeros(size)
    since_purchase = NEVER_SOLD_DAYS
    if times and signals.now is not None:
        latest = max(times.values())
        since_purchase = max((signals.now - latest).total_seconds(), 0.0) / SECONDS_PER_DAY
        matrix = model.vectors.matrix
        profile = np.asarray(
            matrix[[index[p] for p, at in times.items() if at == latest]].mean(axis=0)
        ).ravel()
        norm = np.linalg.norm(profile)
        if norm > 0:
            last_order = np.asarray(matrix[rows] @ (profile / norm)).ravel()
    price_gap = (
        model.log_prices[rows] - model.log_prices[bought].mean() if bought else np.zeros(size)
    )
    share = category_share(model, signals)

    def constant(value: float) -> np.ndarray:
        return np.full(size, value, dtype=np.float64)

    columns = (
        np.log1p(snapshot.decayed[1.0][rows]),
        np.log1p(snapshot.decayed[3.0][rows]),
        np.log1p(weekly[rows]),
        np.log1p(snapshot.decayed[14.0][rows]),
        np.log1p(snapshot.sold_7d[rows]),
        np.log1p(snapshot.sold_prev_7d[rows]),
        np.log1p(snapshot.sold_all[rows]),
        np.log((snapshot.sold_7d[rows] + 1.0) / (snapshot.sold_prev_7d[rows] + 1.0)),
        np.log1p(snapshot.buyers_7d[rows]),
        snapshot.days_since_first_sale[rows],
        snapshot.days_since_last_sale[rows],
        model.log_prices[rows],
        1.0 - model.cheapness[rows],
        np.log1p(snapshot.category_trend[rows]),
        snapshot.rank_in_category[rows],
        snapshot.rank_overall[rows],
        band_trend,
        band_lift,
        constant(signals.age if signals.age is not None else -1),
        constant(signals.delivered_orders),
        constant(len(signals.purchased)),
        constant(since_purchase),
        parts.content[rows],
        last_order,
        parts.copurchase[rows],
        parts.base[rows],
        share[model.category_index[rows]],
        price_gap,
    )
    return np.column_stack(columns).astype(np.float32)


def case_rows(model: Recommender, case: Case) -> tuple[np.ndarray, np.ndarray, np.ndarray] | None:
    """(hàng ứng viên, ma trận đặc trưng, nhãn 0/1) của 1 khách; None nếu không tính được (không
    có tương tác, không có số liệu bán hàng hay không có ứng viên)."""
    signals = case.signals
    weights = model.interaction_weights(signals)
    snapshot = model.snapshot(signals.now) if signals.now is not None else None
    if not weights or snapshot is None:
        return None
    parts = model.behaviour_parts(signals, weights)
    rows = candidates(model, snapshot, signals, parts)
    if rows.size == 0:
        return None
    index = model.vectors.index
    targets = np.fromiter((index[p] for p in case.targets if p in index), dtype=np.int64)
    labels = np.isin(rows, targets).astype(np.int8)
    return rows, feature_matrix(model, snapshot, signals, parts, rows), labels


# ---- mô hình ----


@dataclass(frozen=True)
class TrainingSummary:
    """Dữ liệu đã dùng để học."""

    weeks: int
    cases: int
    rows: int
    positives: int


class Ranker:
    """Mô hình xếp hạng đã học."""

    def __init__(self, classifier: HistGradientBoostingClassifier, summary: TrainingSummary):
        """Bọc mô hình scikit-learn đã học trên đặc trưng FEATURES."""
        self.classifier = classifier
        self.summary = summary

    def predict(self, features: np.ndarray) -> np.ndarray:
        """Xác suất mua của từng dòng đặc trưng."""
        return self.classifier.predict_proba(features)[:, 1]

    def rank(
        self, model: Recommender, signals: UserSignals, parts: BehaviourParts, limit: int
    ) -> tuple[list[int], list[float]] | None:
        """Tối đa `limit` hàng sản phẩm xác suất mua cao nhất kèm xác suất; None nếu không có ứng
        viên hay số liệu bán hàng (khi đó dùng công thức)."""
        snapshot = model.snapshot(signals.now) if signals.now is not None else None
        if snapshot is None:
            return None
        rows = candidates(model, snapshot, signals, parts)
        if rows.size == 0:
            return None
        probability = self.predict(feature_matrix(model, snapshot, signals, parts, rows))
        top = np.argsort(-probability, kind="stable")[:limit]
        return [int(r) for r in rows[top]], [float(p) for p in probability[top]]


@dataclass(frozen=True)
class WeekExamples:
    """Dữ liệu học của 1 mốc."""

    features: np.ndarray
    labels: np.ndarray
    cases: int
    positives: int
    """Số lượt mua trúng ứng viên (trước khi bỏ bớt ứng viên khách không mua)."""


def week_examples(
    model: Recommender, cases: Iterable[Case], rng: np.random.Generator
) -> WeekExamples:
    """Đặc trưng và nhãn của các khách tại 1 mốc; giữ mọi dòng nhãn 1 và NEGATIVE_RATE dòng nhãn
    0 (chọn ngẫu nhiên bằng `rng`)."""
    features, labels = [], []
    count = positives = 0
    for case in cases:
        computed = case_rows(model, case)
        if computed is None:
            continue
        _, x, y = computed
        keep = (y == 1) | (rng.random(len(y)) < NEGATIVE_RATE)
        features.append(x[keep])
        labels.append(y[keep])
        count += 1
        positives += int(y.sum())
    if not features:
        return WeekExamples(
            np.zeros((0, len(FEATURES)), dtype=np.float32), np.zeros(0, np.int8), 0, 0
        )
    return WeekExamples(np.vstack(features), np.concatenate(labels), count, positives)


def fit(weeks: Sequence[WeekExamples], seed: int = 0) -> Ranker | None:
    """Học mô hình từ dữ liệu các tuần; None nếu chưa đủ MIN_POSITIVES lượt mua trúng ứng viên
    hoặc dữ liệu học chỉ có 1 loại nhãn."""
    positives = sum(w.positives for w in weeks)
    if positives < MIN_POSITIVES:
        return None
    features = np.vstack([w.features for w in weeks])
    labels = np.concatenate([w.labels for w in weeks])
    if np.unique(labels).size < 2:
        return None
    classifier = HistGradientBoostingClassifier(
        learning_rate=0.05,
        max_iter=300,
        max_leaf_nodes=31,
        min_samples_leaf=min(200, max(20, len(labels) // 100)),
        l2_regularization=1.0,
        random_state=seed,
    )
    classifier.fit(features, labels)
    summary = TrainingSummary(len(weeks), sum(w.cases for w in weeks), len(labels), positives)
    return Ranker(classifier, summary)


# ---- dữ liệu tại 1 mốc ----


def cases_at(
    orders: Iterable[PastOrder],
    cutoff: datetime,
    until: datetime,
    ages: Mapping[int, int] | None = None,
) -> list[Case]:
    """Khách đã mua trước `cutoff` và mua sản phẩm mới (chưa từng mua) từ `cutoff` đến trước
    `until`; tín hiệu chỉ gồm các đơn trước `cutoff`, tính gợi ý tại `cutoff`. Số đơn Đã giao lấy
    theo trạng thái hiện tại của đơn (không lưu lúc đơn chuyển trạng thái)."""
    history: dict[int, list[PastOrder]] = defaultdict(list)
    future: dict[int, set[int]] = defaultdict(set)
    for order in orders:
        if order.created_at < cutoff:
            history[order.account_id].append(order)
        elif order.created_at < until:
            future[order.account_id].update(order.products)
    ages = ages or {}
    cases = []
    for account_id in sorted(future):
        past = history.get(account_id)
        if not past:
            continue
        delivered = sum(1 for order in past if order.status == "DELIVERED")
        signals = UserSignals(
            account_id, delivered_orders=delivered, now=cutoff, age=ages.get(account_id)
        )
        for order in past:
            for product_id in order.products:
                signals.buy(product_id, order.created_at)
        targets = future[account_id] - signals.purchased
        if targets:
            cases.append(Case(signals, targets))
    return cases


def sample(cases: list[Case], limit: int, seed: int) -> list[Case]:
    """Tối đa `limit` khách chọn ngẫu nhiên (seed cố định), giữ thứ tự."""
    if len(cases) <= limit:
        return cases
    picked = np.random.default_rng(seed).choice(len(cases), limit, replace=False)
    return [cases[i] for i in sorted(picked)]


def model_at(
    vectors: ItemVectors,
    items: Sequence[CatalogItem],
    orders: Iterable[PastOrder],
    cutoff: datetime,
    bands: Mapping[int, int] | None = None,
) -> Recommender:
    """Mô hình gợi ý chỉ thấy các đơn trước `cutoff`, mọi sản phẩm coi như còn hàng (tồn kho
    trong quá khứ không lưu lại)."""
    before = [o for o in orders if o.created_at < cutoff]
    sold: Counter = Counter()
    for order in before:
        sold.update(order.products)
    stats = {item.id: ProductStats(item.price, True, sold.get(item.id, 0), None) for item in items}
    copurchase = build_copurchase(
        ((o.account_id, p) for o in before for p in o.products), vectors.index, len(items)
    )
    sales = SalesHistory(
        ((p, o.created_at, q, o.account_id) for o in before for p, q in o.products.items()),
        vectors.index,
        len(items),
        bands,
    )
    return Recommender(vectors, items, stats, copurchase, {}, sales=sales)


def age_bands(ages: Mapping[int, int]) -> dict[int, int]:
    """Mã tài khoản → nhóm tuổi (bỏ tài khoản không rõ tuổi)."""
    return {account: band for account, age in ages.items() if (band := age_band(age)) >= 0}


def weekly_cutoffs(end: datetime, weeks: int = TRAINING_WEEKS) -> list[datetime]:
    """Các mốc học trước `end`: lùi 1, 2, ..., `weeks` tuần, cũ nhất trước."""
    return [end - timedelta(days=LABEL_DAYS * k) for k in range(weeks, 0, -1)]


def training_weeks(
    vectors: ItemVectors,
    items: Sequence[CatalogItem],
    orders: Sequence[PastOrder],
    ages: Mapping[int, int],
    cutoffs: Iterable[datetime],
    cases_per_week: int = CASES_PER_WEEK,
    seed: int = 0,
) -> Iterator[tuple[datetime, Recommender, list[Case], WeekExamples]]:
    """Từng mốc: mô hình chỉ thấy đơn trước mốc, các khách (đã chọn ngẫu nhiên tối đa
    `cases_per_week`) và dữ liệu học. Dựng lần lượt, mỗi lúc chỉ giữ 1 mô hình."""
    bands = age_bands(ages)
    for number, cutoff in enumerate(cutoffs):
        cases = sample(
            cases_at(orders, cutoff, cutoff + timedelta(days=LABEL_DAYS), ages),
            cases_per_week,
            seed + number,
        )
        model = model_at(vectors, items, orders, cutoff, bands)
        rng = np.random.default_rng(seed + 1000 + number)
        yield cutoff, model, cases, week_examples(model, cases, rng)


def train(
    vectors: ItemVectors,
    items: Sequence[CatalogItem],
    orders: Sequence[PastOrder],
    ages: Mapping[int, int],
    end: datetime,
    weeks: int = TRAINING_WEEKS,
) -> Ranker | None:
    """Học mô hình xếp hạng từ `weeks` tuần trước `end` (nhãn của tuần cuối kết thúc ở `end`);
    None nếu chưa đủ dữ liệu."""
    examples = [
        week
        for _, _, _, week in training_weeks(
            vectors, items, orders, ages, weekly_cutoffs(end, weeks)
        )
    ]
    return fit(examples)
