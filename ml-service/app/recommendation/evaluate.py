"""Đánh giá ngoại tuyến mô hình gợi ý trên dữ liệu trong CSDL (thường là dữ liệu mô phỏng).

Cách đánh giá "giữ lại đơn cuối" (leave-last-order-out):
- Khách có ít nhất 2 đơn khác CANCELLED: giấu đơn cuối cùng. Mô hình chỉ thấy các đơn trước đó,
  lượt xem trong 30 ngày trước đơn cuối nhưng bỏ 48 giờ ngay trước đơn (xem SESSION_GAP), và
  khảo sát; "hay mua cùng", "bán chạy" và "đang bán chạy" dựng từ mọi đơn trừ các đơn bị giấu
  ("đang bán chạy" tính tại lúc đặt đơn bị giấu, chỉ từ các đơn trước đó). Gợi ý trúng = sản phẩm
  có trong đơn bị giấu mà khách chưa mua trước đó.
- Khởi động lạnh: khách đã làm khảo sát, chỉ dùng khảo sát để đoán đơn đầu tiên của khách. "Bán
  chạy" ở đây đếm cả các đơn đầu tiên đó nên được lợi một chút; khảo sát vẫn phải vượt nó.

Chỉ số ở K sản phẩm đầu: tỷ lệ trúng (có ít nhất 1 sản phẩm đúng), precision, recall, NDCG và
độ phủ (tỷ lệ sản phẩm trong danh mục từng được gợi ý). Tồn kho không xét (coi như còn hàng) để
sản phẩm hết hàng hôm nay không làm sai kết quả quá khứ.

Chạy: `uv run python -m app.recommendation.evaluate` (trong ml-service/, cần MySQL như khi chạy
ml-service). Thêm `--grid` để thử nhiều bộ trọng số.
"""

import argparse
import itertools
import math
import sys
import time
from collections import Counter, defaultdict
from collections.abc import Callable, Iterable, Iterator
from dataclasses import dataclass, field, replace
from datetime import datetime, timedelta

from sqlalchemy import Connection, Engine, text

from app.config import Settings
from app.db import create_db_engine
from app.recommendation import data
from app.recommendation.copurchase import build_copurchase
from app.recommendation.data import ProductStats, SurveyProfile, UserSignals
from app.recommendation.features import ItemVectors
from app.recommendation.model import DEFAULT_WEIGHTS, Recommender, Weights
from app.recommendation.trending import SalesHistory

K = 10

SESSION_GAP = timedelta(hours=48)
"""Bỏ lượt xem trong 48 giờ ngay trước đơn bị giấu. Khách (thật và mô phỏng) xem đúng sản phẩm sắp
mua ngay trước khi đặt; giữ các lượt xem đó thì sản phẩm cần đoán nằm sẵn trong đầu vào: chỉ cần
gợi ý lại "sản phẩm vừa xem" đã trúng 100% (rò rỉ nhãn, hội đồng thử hỏi 01/11/2026). Bỏ phiên mua
đó thì đo được khả năng đoán từ hành vi trước, đúng câu hỏi của gợi ý."""


@dataclass(slots=True)
class Order:
    """1 đơn khác CANCELLED. `slots` bớt khoảng 40 byte mỗi đơn: toàn bộ dữ liệu H&M có hơn 9 triệu
    đơn."""

    id: int
    account_id: int
    created_at: datetime
    status: str
    products: dict[int, int] = field(default_factory=dict)
    """Mã sản phẩm → số lượng."""


@dataclass(frozen=True)
class PurchasePairs:
    """Cặp (khách, sản phẩm) của các đơn để dựng "hay mua cùng", đọc lại được nhiều lần mà không giữ
    danh sách cặp trong bộ nhớ (dữ liệu H&M có hàng chục triệu cặp). Cặp trùng không sao: ma trận
    "hay mua cùng" chỉ đếm 1 lần mỗi khách."""

    orders: list[Order]

    def __iter__(self) -> Iterator[tuple[int, int]]:
        return ((o.account_id, p) for o in self.orders for p in o.products)


@dataclass
class Case:
    """1 khách cần đoán: dữ liệu mô hình được thấy và các sản phẩm đúng."""

    signals: UserSignals
    targets: set[int]


@dataclass
class Dataset:
    """Dữ liệu đã chia: khách cần đoán, cặp mua để dựng "hay mua cùng", số đã bán và các đơn mô
    hình được thấy (cho "đang bán chạy")."""

    behaviour: list[Case]
    cold_start: list[Case]
    train_pairs: Iterable[tuple[int, int]]
    train_sold: Counter
    train_orders: list[Order] = field(default_factory=list)


def load_orders(conn: Connection) -> list[Order]:
    """Mọi đơn khác CANCELLED kèm sản phẩm, theo thời gian đặt."""
    orders: dict[int, Order] = {}
    for oid, account_id, created_at, status, product_id, quantity in conn.execute(
        text(
            "SELECT o.id, o.account_id, o.created_at, o.status, v.product_id, oi.quantity "
            "FROM orders o "
            "JOIN order_item oi ON oi.order_id = o.id "
            "JOIN product_variant v ON v.id = oi.variant_id "
            "WHERE o.status <> 'CANCELLED' ORDER BY o.created_at, o.id"
        )
    ):
        order = orders.setdefault(oid, Order(oid, account_id, created_at, status))
        order.products[product_id] = order.products.get(product_id, 0) + quantity
    return list(orders.values())


def load_views(conn: Connection) -> dict[int, list[tuple[int, datetime]]]:
    """Lượt xem theo tài khoản."""
    views: dict[int, list[tuple[int, datetime]]] = defaultdict(list)
    for account_id, product_id, viewed_at in conn.execute(
        text("SELECT account_id, product_id, viewed_at FROM product_view_log")
    ):
        views[account_id].append((product_id, viewed_at))
    return views


def split(
    orders: list[Order],
    views: dict[int, list[tuple[int, datetime]]],
    surveys: dict[int, SurveyProfile],
) -> Dataset:
    """Chia dữ liệu theo cách "giữ lại đơn cuối" và tạo các ca khởi động lạnh."""
    by_account: dict[int, list[Order]] = defaultdict(list)
    for order in orders:
        by_account[order.account_id].append(order)
    held_out: set[int] = set()
    behaviour: list[Case] = []
    cold_start: list[Case] = []
    for account_id, history in by_account.items():
        survey = surveys.get(account_id)
        if survey is not None:
            cold_start.append(
                Case(UserSignals(account_id, survey=survey), set(history[0].products))
            )
        if len(history) < 2:
            continue
        last = history[-1]
        earlier = [o for o in history[:-1] if o.created_at <= last.created_at]
        bought = {p for o in earlier for p in o.products}
        targets = set(last.products) - bought
        if not earlier or not targets:
            continue
        since = last.created_at - timedelta(days=data.VIEW_WINDOW_DAYS)
        until = last.created_at - SESSION_GAP
        seen = [(p, at) for p, at in views.get(account_id, []) if since <= at < until]
        signals = UserSignals(
            account_id,
            purchased=bought,
            viewed={p for p, _ in seen},
            delivered_orders=len(earlier),
            survey=survey,
            now=last.created_at,
        )
        for order in earlier:
            for product_id in order.products:
                signals.touch(product_id, order.created_at)
        for product_id, at in seen:
            signals.touch(product_id, at)
        behaviour.append(Case(signals, targets))
        held_out.add(last.id)
    train = [o for o in orders if o.id not in held_out]
    sold: Counter = Counter()
    for order in train:
        sold.update(order.products)
    return Dataset(behaviour, cold_start, PurchasePairs(train), sold, train)


def load_dataset(conn: Connection) -> tuple[Dataset, list, dict[int, str]]:
    """Đọc dữ liệu từ CSDL và chia; trả về thêm danh mục sản phẩm và tên thương hiệu."""
    surveys = {}
    for (account_id,) in conn.execute(
        text("SELECT account_id FROM survey_result WHERE status = 'COMPLETED'")
    ):
        survey = data.load_survey(conn, account_id)
        if survey is not None:
            surveys[account_id] = survey
    dataset = split(load_orders(conn), load_views(conn), surveys)
    return dataset, data.load_catalog(conn), data.load_brand_names(conn)


# ---- chỉ số ----


@dataclass
class Scores:
    """Chỉ số trung bình trên các ca."""

    cases: int
    hit_rate: float
    precision: float
    recall: float
    ndcg: float
    coverage: float


def score_lists(
    cases: list[Case], recommend: Callable[[Case], list[int]], catalog_size: int, k: int = K
) -> Scores:
    """Tính chỉ số cho danh sách gợi ý (K sản phẩm đầu) của từng ca."""
    hits = precision = recall = ndcg = 0.0
    shown: set[int] = set()
    for case in cases:
        ranked = recommend(case)[:k]
        shown.update(ranked)
        relevant = [1.0 if p in case.targets else 0.0 for p in ranked]
        found = sum(relevant)
        hits += found > 0
        precision += found / k
        recall += found / len(case.targets)
        dcg = sum(r / math.log2(i + 2) for i, r in enumerate(relevant))
        ideal = sum(1 / math.log2(i + 2) for i in range(min(len(case.targets), k)))
        ndcg += dcg / ideal
    n = max(len(cases), 1)
    return Scores(
        len(cases), hits / n, precision / n, recall / n, ndcg / n, len(shown) / catalog_size
    )


def build_model(dataset: Dataset, items: list, brands: dict[int, str]) -> Recommender:
    """Mô hình dựng từ phần dữ liệu được thấy (không có đơn bị giấu), coi mọi sản phẩm còn hàng."""
    vectors = ItemVectors(items)
    stats = {
        item.id: ProductStats(item.price, True, dataset.train_sold.get(item.id, 0), None)
        for item in items
    }
    copurchase = build_copurchase(dataset.train_pairs, vectors.index, len(items))
    sales = SalesHistory(
        ((p, o.created_at, q) for o in dataset.train_orders for p, q in o.products.items()),
        vectors.index,
        len(items),
    )
    return Recommender(vectors, items, stats, copurchase, brands, sales=sales)


_SINGLE = Weights(content=0, copurchase=0, popular=0, trending=0, style=0, diversity=0)

PREVIOUS_WEIGHTS = replace(DEFAULT_WEIGHTS, trending=0.0)
"""Bộ trọng số trước khi có "đang bán chạy" (chọn trên dữ liệu mô phỏng), giữ để so sánh."""

BEHAVIOUR_METHODS = {
    "Bán chạy": replace(_SINGLE, popular=1),
    "Đang bán chạy": replace(_SINGLE, trending=1),
    "Nội dung (P(u), cosine)": replace(_SINGLE, content=1),
    "Hay mua cùng": replace(_SINGLE, copurchase=1),
    "Hybrid cũ (chưa có đang bán chạy)": PREVIOUS_WEIGHTS,
    "Hybrid, không ưu tiên gần đây": replace(DEFAULT_WEIGHTS, recent_boost=0),
    "Hybrid (mặc định)": DEFAULT_WEIGHTS,
}
"""Các cách so sánh trên khách có hành vi: từng nguồn riêng và hybrid."""


def run(engine: Engine, grid: bool, out=None) -> dict[str, Scores]:
    """Chạy đánh giá và in bảng kết quả. Trả về chỉ số theo tên cách gợi ý."""
    out = out or sys.stdout
    started = time.perf_counter()
    with engine.connect() as conn:
        dataset, items, brands = load_dataset(conn)
    base = build_model(dataset, items, brands)
    size = len(items)
    results: dict[str, Scores] = {}

    def behaviour(weights: Weights) -> Scores:
        base.weights = weights
        return score_lists(
            dataset.behaviour,
            lambda c: [s.product_id for s in base.for_account(c.signals, K)],
            size,
        )

    results["Xem gần đây (không mô hình)"] = score_lists(dataset.behaviour, _recently_viewed, size)
    for name, weights in BEHAVIOUR_METHODS.items():
        results[name] = behaviour(weights)
    base.weights = DEFAULT_WEIGHTS

    popular = _popular_list(base, K)
    results["Khởi động lạnh: bán chạy"] = score_lists(dataset.cold_start, lambda c: popular, size)
    for name, weights in (
        ("Khởi động lạnh: khảo sát, chỉ cosine + λb", replace(DEFAULT_WEIGHTS, survey_popular=0)),
        ("Khởi động lạnh: khảo sát + bán chạy (mặc định)", DEFAULT_WEIGHTS),
    ):
        base.weights = weights
        results[name] = score_lists(
            dataset.cold_start,
            lambda c: [s.product_id for s in base.for_survey(c.signals.survey, K)],
            size,
        )
    base.weights = DEFAULT_WEIGHTS

    print_table(results, dataset, out)
    if grid:
        print_grid(dataset, behaviour, out)
        print_recency_grid(behaviour, out)
    print(f"\nThời gian chạy: {time.perf_counter() - started:.1f} giây", file=out)
    return results


def _recently_viewed(case: Case) -> list[int]:
    """Mốc so sánh đơn giản: các sản phẩm đã xem mà chưa mua, xem gần nhất trước."""
    signals = case.signals
    viewed = [p for p in signals.viewed if p not in signals.purchased]
    return sorted(viewed, key=lambda p: signals.last_at[p], reverse=True)


def _popular_list(model: Recommender, k: int) -> list[int]:
    """K sản phẩm bán chạy nhất (dữ liệu được thấy)."""
    order = sorted(range(len(model.popularity)), key=lambda r: -model.popularity[r])
    return [int(model.vectors.ids[r]) for r in order[:k]]


def print_table(results: dict[str, Scores], dataset: Dataset, out) -> None:
    """In bảng Markdown các chỉ số."""
    print(
        f"Khách có hành vi: {len(dataset.behaviour)} ca; khởi động lạnh: "
        f"{len(dataset.cold_start)} ca; K = {K}\n",
        file=out,
    )
    print("| Cách gợi ý | Số ca | Tỷ lệ trúng | Precision | Recall | NDCG | Độ phủ |", file=out)
    print("|---|---:|---:|---:|---:|---:|---:|", file=out)
    for name, s in results.items():
        print(
            f"| {name} | {s.cases} | {s.hit_rate:.3f} | {s.precision:.3f} | {s.recall:.3f} "
            f"| {s.ndcg:.3f} | {s.coverage:.3f} |",
            file=out,
        )


def print_grid(dataset: Dataset, behaviour: Callable[[Weights], Scores], out) -> None:
    """Thử các bộ trọng số, in 10 bộ có NDCG cao nhất."""
    rows = []
    for content, copurchase, popular, trending, diversity in itertools.product(
        (0.5, 0.7, 1.0), (0.1, 0.2, 0.4), (0.0, 0.1, 0.2), (0.0, 0.5, 1.0), (0.0, 0.05, 0.1)
    ):
        weights = replace(
            DEFAULT_WEIGHTS,
            content=content,
            copurchase=copurchase,
            popular=popular,
            trending=trending,
            diversity=diversity,
        )
        rows.append((behaviour(weights), weights))
    rows.sort(key=lambda r: -r[0].ndcg)
    print(
        "\n| Nội dung | Mua cùng | Bán chạy | Đang bán chạy | Dàn đều | Tỷ lệ trúng | NDCG "
        "| Độ phủ |",
        file=out,
    )
    print("|---:|---:|---:|---:|---:|---:|---:|---:|", file=out)
    for s, w in rows[:10]:
        print(
            f"| {w.content} | {w.copurchase} | {w.popular} | {w.trending} | {w.diversity} "
            f"| {s.hit_rate:.3f} | {s.ndcg:.3f} | {s.coverage:.3f} |",
            file=out,
        )


def print_recency_grid(behaviour: Callable[[Weights], Scores], out) -> None:
    """Thử các mức ưu tiên tương tác gần đây (β, chu kỳ bán rã) với trọng số trộn mặc định."""
    print("\n| β | Bán rã (ngày) | Tỷ lệ trúng | NDCG | Độ phủ |", file=out)
    print("|---:|---:|---:|---:|---:|", file=out)
    for boost, half_life in itertools.product((0.0, 1.0, 2.0, 3.0, 5.0), (1.0, 3.0, 7.0)):
        weights = replace(DEFAULT_WEIGHTS, recent_boost=boost, recent_half_life_days=half_life)
        s = behaviour(weights)
        print(
            f"| {boost:g} | {half_life:g} | {s.hit_rate:.3f} | {s.ndcg:.3f} | {s.coverage:.3f} |",
            file=out,
        )


def main(argv: list[str] | None = None, engine: Engine | None = None) -> int:
    """Điểm vào dòng lệnh."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--grid", action="store_true", help="thử nhiều bộ trọng số")
    args = parser.parse_args(argv)
    run(engine or create_db_engine(Settings.from_env()), args.grid)
    return 0


if __name__ == "__main__":
    sys.exit(main())
