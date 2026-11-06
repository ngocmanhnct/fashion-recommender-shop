"""Đánh giá gợi ý và phân nhóm khách hàng trên dữ liệu mua hàng thật của H&M (Kaggle).

Nguồn: cuộc thi "H&M Personalized Fashion Recommendations" (H&M Group, Kaggle, 2022),
https://www.kaggle.com/competitions/h-and-m-personalized-fashion-recommendations. Lịch sử mua
thật của khách H&M từ 20/09/2018 đến 22/09/2020 (cửa hàng và trực tuyến); sản phẩm có loại hàng,
màu, mô tả; khách có tuổi. Điều 7A luật cuộc thi: chỉ dùng phi thương mại, cho nghiên cứu học thuật
và giáo dục. Điều 7B: không chia sẻ lại dữ liệu, nên tệp gốc và tệp đã lọc không bao giờ lên repo;
ai chạy lại thì tự tải sau khi đồng ý luật (data/hm/README.md).

Vì sao: dữ liệu mô phỏng (app/simulation) biết trước kiểu thật của từng khách nên đo được ARI,
nhưng hành vi do chính quy tắc của dự án sinh ra. Script này chạy ĐÚNG mã của ml-service
(ItemVectors, build_copurchase, Recommender, segmentation.model.train) trên hành vi mua thật,
không sửa gì trong mô hình.

Ánh xạ sang cửa hàng:
- 1 mã sản phẩm H&M (product_code, gồm mọi màu của 1 mẫu) = 1 sản phẩm: tên prod_name, mô tả
  detail_desc, danh mục = loại hàng (product_type), không có thương hiệu (cả bộ là 1 hãng), giá =
  trung vị giá bán. Giá trong bộ dữ liệu đã được H&M quy đổi về thang riêng, không phải tiền thật;
  nhân PRICE_SCALE cho thành số nguyên, chỉ dùng để chia 5 khoảng giá và cộng M.
- 1 khách, 1 ngày, 1 kênh bán (cửa hàng hoặc trực tuyến) = 1 đơn Đã giao (dữ liệu không có mã
  hóa đơn). Không có lượt xem, giỏ hàng, khảo sát: phần khởi động lạnh không đánh giá ở đây.
- Mẫu khách: customer_id đã là mã băm SHA-256, nên khách có int(8 ký tự đầu, 16) % 1000 nhỏ hơn
  SAMPLE_PER_MILLE là mẫu ngẫu nhiên, cố định: chạy lại, hay chạy trên tệp đã lọc trước bằng cùng
  quy tắc, đều ra đúng mẫu đó. Chỉ giữ sản phẩm có khách trong mẫu mua.

Gợi ý, 2 cách chia:
1. Giữ lại đơn cuối: dùng chung hàm `split` với evaluate.py (dữ liệu mô phỏng).
2. Cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước REC_HOLDOUT_DAYS
   ngày cuối, đoán sản phẩm MỚI khách mua trong những ngày đó (cuộc thi tính cả sản phẩm mua lại;
   cửa hàng của dự án không gợi ý lại sản phẩm đã mua nên ở đây cũng không tính).
Mỗi cách đánh giá tối đa MAX_CASES khách chọn ngẫu nhiên (seed cố định), kèm khoảng tin cậy 95%
(bootstrap theo khách) cho tỷ lệ trúng và cho chênh lệch giữa hybrid với từng cách khác (bootstrap
ghép cặp: cùng mẫu khách cho cả 2 cách).

Phân nhóm: R, F, M tính đến ngày sau ngày cuối (F = số đơn, M = tổng giá), huấn luyện bằng `train`
như trên cửa hàng; độ tuổi từng nhóm; độ ổn định (huấn luyện lại trên 80% khách, so nhóm của mọi
khách bằng ARI); kiểm tra trên tương lai: phân nhóm bằng dữ liệu trước SEGMENT_HOLDOUT_DAYS ngày
cuối, đo tỷ lệ khách từng nhóm mua lại và mức chi trong những ngày đó.

Chạy trong ml-service/ (không cần MySQL; đọc hết tệp 3,5 GB mất vài phút):
    uv run python -m scripts.eval_hm
Đọc articles.csv, customers.csv, transactions_train.csv (hoặc tệp .zip của từng tệp) trong
data/hm/raw, ghi kết quả vào data/hm/ket-qua.md.
"""

from __future__ import annotations

import argparse
import csv
import io
import math
import statistics
import sys
import time
import zipfile
from collections import Counter, defaultdict
from collections.abc import Callable, Iterable, Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from pathlib import Path

import numpy as np
from sklearn.metrics import adjusted_rand_score

from app.recommendation.data import UserSignals
from app.recommendation.evaluate import (
    BEHAVIOUR_METHODS,
    Case,
    Dataset,
    K,
    Order,
    build_model,
    split,
)
from app.recommendation.features import CatalogItem
from app.segmentation.model import SegmentModel, train
from app.segmentation.rfm import Rfm, recency_days

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "hm" / "raw"
RESULT_FILE = REPO_ROOT / "data" / "hm" / "ket-qua.md"

SAMPLE_PER_MILLE = 50
"""5% khách."""
REC_HOLDOUT_DAYS = 7
"""Cuộc thi chấm trên 7 ngày ngay sau dữ liệu huấn luyện."""
SEGMENT_HOLDOUT_DAYS = 90
MAX_CASES = 10_000
BOOTSTRAP_ROUNDS = 1000
STABILITY_ROUNDS = 20
STABILITY_FRACTION = 0.8
SEED = 2026
PRICE_SCALE = 1_000_000
HYBRID = "Hybrid (mặc định)"
RECENT_POPULAR = f"Bán chạy {REC_HOLDOUT_DAYS} ngày trước mốc (không mô hình)"
AGE_BANDS = ((0, 25, "Dưới 25"), (25, 35, "25–34"), (35, 45, "35–44"), (45, 55, "45–54"))
OLDEST_BAND = "Từ 55"


def _progress(message: str) -> None:
    """Báo tiến độ ra stderr (đọc hết tệp gốc mất vài phút)."""
    print(f"[{time.strftime('%H:%M:%S')}] {message}", file=sys.stderr, flush=True)


# ---- đọc tệp ----


@contextmanager
def open_csv(folder: Path, stem: str) -> Iterator[csv.DictReader]:
    """Đọc từng dòng của <stem>.csv, hoặc tệp .csv trong <stem>.csv.zip / <stem>.zip (tải từ
    Kaggle), không giải nén ra đĩa."""
    plain = folder / f"{stem}.csv"
    if plain.is_file():
        with plain.open(encoding="utf-8", newline="") as handle:
            yield csv.DictReader(handle)
        return
    for path in (folder / f"{stem}.csv.zip", folder / f"{stem}.zip"):
        if not path.is_file():
            continue
        with zipfile.ZipFile(path) as archive:
            members = [n for n in archive.namelist() if n.lower().endswith(".csv")]
            if not members:
                break
            with (
                archive.open(members[0]) as raw,
                io.TextIOWrapper(raw, encoding="utf-8", newline="") as handle,
            ):
                yield csv.DictReader(handle)
            return
    raise FileNotFoundError(f"Không thấy {stem}.csv (hoặc tệp .zip của nó) trong {folder}")


def in_sample(customer_id: str, per_mille: int) -> bool:
    """Khách thuộc mẫu: 8 ký tự đầu của mã khách (mã băm hex) chia 1000 dư nhỏ hơn `per_mille`."""
    try:
        return int(customer_id[:8], 16) % 1000 < per_mille
    except ValueError:
        return False


def _number(value: object) -> float | None:
    """Số của 1 ô; None nếu trống hoặc không phải số."""
    try:
        return float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return None


@dataclass(frozen=True)
class Article:
    """Phần cần dùng của 1 dòng articles.csv (1 màu của 1 mẫu sản phẩm)."""

    product_code: int
    name: str
    type_no: int
    description: str


def load_articles(rows: Iterable[dict[str, str]]) -> dict[int, Article]:
    """article_id → thông tin sản phẩm; bỏ dòng thiếu mã."""
    result = {}
    for row in rows:
        try:
            article_id = int(row["article_id"])
            code = int(row["product_code"])
            type_no = int(row["product_type_no"])
        except (KeyError, TypeError, ValueError):
            continue
        result[article_id] = Article(
            code,
            (row.get("prod_name") or "").strip(),
            type_no,
            (row.get("detail_desc") or "").strip(),
        )
    return result


# ---- chuyển sang dạng của ml-service ----


@dataclass
class Shop:
    """Dữ liệu đã chuyển: sản phẩm, đơn (theo thời gian), tổng tiền mỗi đơn, mã khách gốc."""

    items: list[CatalogItem]
    orders: list[Order]
    totals: dict[int, int]
    accounts: dict[str, int]
    """customer_id gốc → mã tài khoản (số nguyên)."""
    lines: int = 0
    skipped: Counter = field(default_factory=Counter)


def build_shop(
    rows: Iterable[dict[str, str]], articles: dict[int, Article], per_mille: int
) -> Shop:
    """Đọc giao dịch của khách trong mẫu, gom thành đơn (khách, ngày, kênh) và sản phẩm."""
    accounts: dict[str, int] = {}
    orders: dict[tuple[int, str, str], Order] = {}
    totals: Counter = Counter()
    prices: dict[int, list[float]] = defaultdict(list)
    first_article: dict[int, Article] = {}
    skipped: Counter = Counter()
    kept = 0
    for row in rows:
        customer = row.get("customer_id") or ""
        if not in_sample(customer, per_mille):
            continue
        price = _number(row.get("price"))
        article_id = _number(row.get("article_id"))
        article = None if article_id is None else articles.get(int(article_id))
        try:
            day = datetime.strptime(row.get("t_dat") or "", "%Y-%m-%d").replace(tzinfo=UTC)
        except ValueError:
            skipped["Ngày không đọc được"] += 1
            continue
        if article is None:
            skipped["Sản phẩm không có trong articles.csv"] += 1
            continue
        if price is None or price <= 0:
            skipped["Giá không dương"] += 1
            continue
        account = accounts.setdefault(customer, len(accounts) + 1)
        key = (account, row["t_dat"], row.get("sales_channel_id") or "")
        order = orders.get(key)
        if order is None:
            order = Order(len(orders) + 1, account, day, "DELIVERED")
            orders[key] = order
        code = article.product_code
        order.products[code] = order.products.get(code, 0) + 1
        totals[order.id] += round(price * PRICE_SCALE)
        prices[code].append(price)
        first_article.setdefault(code, article)
        kept += 1
    items = [
        CatalogItem(
            code,
            first_article[code].type_no,
            None,
            first_article[code].name,
            first_article[code].description,
            max(1, round(statistics.median(prices[code]) * PRICE_SCALE)),
        )
        for code in sorted(prices)
    ]
    ordered = sorted(orders.values(), key=lambda o: (o.created_at, o.id))
    return Shop(items, ordered, dict(totals), accounts, kept, skipped)


def load_ages(rows: Iterable[dict[str, str]], accounts: dict[str, int]) -> dict[int, int]:
    """Tuổi của các khách trong mẫu (bỏ ô trống)."""
    ages = {}
    for row in rows:
        account = accounts.get(row.get("customer_id") or "")
        age = _number(row.get("age"))
        if account is not None and age is not None and age > 0:
            ages[account] = int(age)
    return ages


# ---- gợi ý ----


def split_by_time(orders: list[Order], cutoff: datetime) -> Dataset:
    """Mô hình chỉ thấy đơn trước `cutoff`; đoán sản phẩm mới khách mua từ `cutoff` trở đi."""
    before = [o for o in orders if o.created_at < cutoff]
    history: dict[int, list[Order]] = defaultdict(list)
    for order in before:
        history[order.account_id].append(order)
    future: dict[int, set[int]] = defaultdict(set)
    for order in orders:
        if order.created_at >= cutoff:
            future[order.account_id].update(order.products)
    cases = []
    for account_id in sorted(future):
        bought = {p for o in history.get(account_id, []) for p in o.products}
        targets = future[account_id] - bought
        if not bought or not targets:
            continue
        signals = UserSignals(
            account_id,
            purchased=bought,
            delivered_orders=len(history[account_id]),
            now=cutoff,
        )
        for order in history[account_id]:
            for product_id in order.products:
                signals.touch(product_id, order.created_at)
        cases.append(Case(signals, targets))
    sold: Counter = Counter()
    for order in before:
        sold.update(order.products)
    pairs = sorted({(o.account_id, p) for o in before for p in o.products})
    return Dataset(cases, [], pairs, sold)


def limit_cases(cases: list[Case], limit: int, seed: int = SEED) -> list[Case]:
    """Tối đa `limit` ca, chọn ngẫu nhiên với seed cố định (giữ thứ tự)."""
    if len(cases) <= limit:
        return cases
    picked = np.random.default_rng(seed).choice(len(cases), limit, replace=False)
    return [cases[i] for i in sorted(picked)]


def recent_popular(orders: list[Order], cutoff: datetime, days: int) -> list[int]:
    """Sản phẩm bán nhiều nhất trong `days` ngày trước `cutoff`, nhiều trước."""
    since = cutoff - timedelta(days=days)
    sold: Counter = Counter()
    for order in orders:
        if since <= order.created_at < cutoff:
            sold.update(order.products)
    return [p for p, _ in sorted(sold.items(), key=lambda x: (-x[1], x[0]))]


def case_scores(
    cases: list[Case], recommend: Callable[[Case], list[int]], k: int = K
) -> tuple[np.ndarray, np.ndarray, set[int]]:
    """Trúng (0/1) và NDCG@k của từng ca, cùng tập sản phẩm đã được gợi ý."""
    hits, ndcgs = [], []
    shown: set[int] = set()
    for case in cases:
        ranked = recommend(case)[:k]
        shown.update(ranked)
        relevant = [1.0 if p in case.targets else 0.0 for p in ranked]
        dcg = sum(r / math.log2(i + 2) for i, r in enumerate(relevant))
        ideal = sum(1 / math.log2(i + 2) for i in range(min(len(case.targets), k)))
        hits.append(1.0 if any(relevant) else 0.0)
        ndcgs.append(dcg / ideal)
    return np.array(hits), np.array(ndcgs), shown


def bootstrap_ci(values: np.ndarray, rounds: int, seed: int = SEED) -> tuple[float, float]:
    """Khoảng tin cậy 95% của trung bình (bootstrap phân vị, lấy lại mẫu theo ca)."""
    if len(values) == 0:
        return 0.0, 0.0
    rng = np.random.default_rng(seed)
    means = values[rng.integers(0, len(values), size=(rounds, len(values)))].mean(axis=1)
    return float(np.percentile(means, 2.5)), float(np.percentile(means, 97.5))


def _decimal(value: float, digits: int = 1) -> str:
    """Số kiểu Việt Nam: dấu chấm ngăn hàng nghìn, dấu phẩy thập phân (vd 1.234,5)."""
    text = f"{value:,.{digits}f}"
    return text.replace(",", "\0").replace(".", ",").replace("\0", ".")


def _percent(value: float) -> str:
    """Tỷ lệ dạng phần trăm (vd 26,3%)."""
    return f"{_decimal(value * 100)}%"


def _signed(value: float) -> str:
    """Chênh lệch theo điểm phần trăm, có dấu (vd +4,2)."""
    return ("+" if value >= 0 else "−") + _decimal(abs(value) * 100)


def _mean(values: np.ndarray) -> float:
    """Trung bình, 0 nếu rỗng."""
    return float(values.mean()) if len(values) else 0.0


def evaluate_recommendations(
    shop: Shop,
    dataset: Dataset,
    title: str,
    rounds: int,
    max_cases: int,
    extra: dict[str, Callable[[Case], list[int]]] | None = None,
) -> str:
    """Bảng chỉ số, khoảng tin cậy và so sánh với hybrid trên 1 cách chia."""
    cases = limit_cases(dataset.behaviour, max_cases)
    _progress(f"{title}: dựng mô hình, {len(cases)}/{len(dataset.behaviour)} ca")
    model = build_model(dataset, shop.items, {})
    methods: dict[str, Callable[[Case], list[int]]] = dict(extra or {})
    for name, weights in BEHAVIOUR_METHODS.items():

        def recommend(case: Case, weights=weights) -> list[int]:
            model.weights = weights
            return [s.product_id for s in model.for_account(case.signals, K)]

        methods[name] = recommend
    lines = [f"### {title}", ""]
    note = (
        f"Số khách cần đoán: {len(dataset.behaviour)}"
        if len(cases) == len(dataset.behaviour)
        else f"Số khách cần đoán: {len(dataset.behaviour)}, đánh giá {len(cases)} khách chọn "
        f"ngẫu nhiên (seed {SEED})"
    )
    lines += [
        f"{note}; K = {K}; {len(shop.items)} sản phẩm.",
        "",
        "| Cách gợi ý | Tỷ lệ trúng | Khoảng tin cậy 95% | NDCG@10 | Độ phủ |",
        "|---|---:|---|---:|---:|",
    ]
    hits: dict[str, np.ndarray] = {}
    for name, recommend in methods.items():
        hit, ndcg, shown = case_scores(cases, recommend)
        hits[name] = hit
        low, high = bootstrap_ci(hit, rounds)
        lines.append(
            f"| {name} | {_percent(_mean(hit))} | {_percent(low)} – {_percent(high)} "
            f"| {_decimal(_mean(ndcg), 3)} | {_percent(len(shown) / max(len(shop.items), 1))} |"
        )
        _progress(f"  {name}: {_percent(_mean(hit))}")
    lines += [
        "",
        "| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |",
        "|---|---:|---|---|",
    ]
    for name, other in hits.items():
        if name == HYBRID:
            continue
        diff = hits[HYBRID] - other
        low, high = bootstrap_ci(diff, rounds)
        verdict = (
            "hybrid tốt hơn" if low > 0 else "hybrid kém hơn" if high < 0 else "chưa phân biệt được"
        )
        lines.append(
            f"| Hybrid − {name} | {_signed(_mean(diff))} | {_signed(low)} – {_signed(high)} "
            f"| {verdict} |"
        )
    return "\n".join(lines)


# ---- phân nhóm ----


def rfm_at(orders: Iterable[Order], totals: dict[int, int], now: datetime) -> list[Rfm]:
    """R, F, M của mọi khách có đơn trước `now` (M theo thang giá nhân PRICE_SCALE)."""
    last: dict[int, datetime] = {}
    count: Counter = Counter()
    money: Counter = Counter()
    for order in orders:
        if order.created_at >= now:
            continue
        account = order.account_id
        last[account] = max(last.get(account, order.created_at), order.created_at)
        count[account] += 1
        money[account] += totals[order.id]
    return [
        Rfm(account, recency_days(last[account], now), count[account], int(money[account]))
        for account in sorted(last)
    ]


def _money(amount: float) -> str:
    """Số tiền theo thang giá của H&M (đã chia lại PRICE_SCALE), 2 chữ số thập phân."""
    return _decimal(amount / PRICE_SCALE, 2)


def _band(age: int) -> str:
    """Nhóm tuổi."""
    for low, high, label in AGE_BANDS:
        if low <= age < high:
            return label
    return OLDEST_BAND


def age_table(model: SegmentModel, rows: list[Rfm], ages: dict[int, int]) -> list[str]:
    """Tỷ lệ khách theo nhóm tuổi trong từng nhóm khách."""
    labels = [label for _, _, label in AGE_BANDS] + [OLDEST_BAND]
    groups: dict[str, list[int]] = defaultdict(list)
    for row, seg in zip(rows, model.assign(rows), strict=True):
        groups[seg.code].append(row.account_id)
    lines = [
        "| Nhóm | Tuổi TB | " + " | ".join(labels) + " | Không rõ tuổi |",
        "|---|---:|" + "---:|" * (len(labels) + 1),
    ]
    for seg in model.segments:
        members = groups.get(seg.code, [])
        known = [ages[a] for a in members if a in ages]
        bands = Counter(_band(a) for a in known)
        total = max(len(members), 1)
        cells = [_percent(bands[label] / total) for label in labels]
        mean_age = _decimal(statistics.mean(known)) if known else "–"
        lines.append(
            f"| {seg.name} | {mean_age} | "
            + " | ".join(cells)
            + f" | {_percent((len(members) - len(known)) / total)} |"
        )
    return lines


def segment_table(model: SegmentModel) -> list[str]:
    """Bảng các nhóm: số khách, R, F, M trung bình."""
    lines = [
        "| Nhóm | Số khách | R TB (ngày) | F TB (đơn) | M TB (thang giá H&M) |",
        "|---|---:|---:|---:|---:|",
    ]
    for seg in model.segments:
        lines.append(
            f"| {seg.name} | {_decimal(seg.size, 0)} | {_decimal(seg.mean_recency, 0)} | "
            f"{_decimal(seg.mean_frequency)} | {_money(seg.mean_monetary)} |"
        )
    return lines


def stability(model: SegmentModel, rows: list[Rfm], rounds: int) -> list[float]:
    """ARI giữa nhóm của mô hình đầy đủ và mô hình huấn luyện lại trên 80% khách ngẫu nhiên."""
    full = [s.code for s in model.assign(rows)]
    rng = np.random.default_rng(SEED)
    scores = []
    for _ in range(rounds):
        picked = sorted(rng.choice(len(rows), int(len(rows) * STABILITY_FRACTION), replace=False))
        again = train([rows[i] for i in picked])
        if again is not None:
            scores.append(float(adjusted_rand_score(full, [s.code for s in again.assign(rows)])))
    return scores


def evaluate_segmentation(
    shop: Shop, ages: dict[int, int], cutoff: datetime, end: datetime, rounds: int
) -> str:
    """Phân nhóm trên toàn bộ dữ liệu, độ tuổi, độ ổn định và kiểm tra trên dữ liệu sau mốc."""
    rows = rfm_at(shop.orders, shop.totals, end)
    _progress(f"Phân nhóm {len(rows)} khách")
    model = train(rows)
    if model is None:
        return f"Chưa đủ dữ liệu để phân nhóm: {len(rows)} khách."
    lines = [
        f"### Toàn bộ dữ liệu (tính đến {end:%d/%m/%Y})",
        "",
        f"Số khách: {_decimal(len(rows), 0)}.",
        "",
        "| K | Inertia | Silhouette |",
        "|---:|---:|---:|",
    ]
    for score in model.scores:
        silhouette = "" if score.silhouette is None else _decimal(score.silhouette, 3)
        lines.append(f"| {score.k} | {_decimal(score.inertia, 1)} | {silhouette} |")
    lines += [
        "",
        (
            f"Điểm gãy (Elbow): K = {model.elbow_k}. Chọn K = {len(model.segments)}, "
            f"Silhouette = {_decimal(model.silhouette, 3)}."
        ),
        "",
        *segment_table(model),
        "",
        "Độ tuổi trong từng nhóm (tỷ lệ trên số khách của nhóm):",
        "",
        *age_table(model, rows, ages),
    ]
    _progress(f"Độ ổn định: {rounds} lần huấn luyện lại")
    aris = stability(model, rows, rounds)
    if aris:
        lines += [
            "",
            (
                f"Độ ổn định: huấn luyện lại {len(aris)} lần, mỗi lần trên "
                f"{int(STABILITY_FRACTION * 100)}% khách chọn ngẫu nhiên, rồi so nhóm của mọi "
                f"khách với mô hình đầy đủ: ARI trung bình {_decimal(statistics.mean(aris), 3)}, "
                f"thấp nhất {_decimal(min(aris), 3)}."
            ),
        ]

    before = rfm_at(shop.orders, shop.totals, cutoff)
    past_model = train(before)
    if past_model is None:
        return "\n".join(lines)
    spent: Counter = Counter()
    for order in shop.orders:
        if order.created_at >= cutoff:
            spent[order.account_id] += shop.totals[order.id]
    groups: dict[str, list[Rfm]] = defaultdict(list)
    for row, seg in zip(before, past_model.assign(before), strict=True):
        groups[seg.code].append(row)
    days = (end - cutoff).days
    lines += [
        "",
        f"### Kiểm tra trên tương lai (phân nhóm đến {cutoff:%d/%m/%Y}, xem {days} ngày sau)",
        "",
        (
            f"Mô hình chỉ thấy dữ liệu trước mốc ({_decimal(len(before), 0)} khách, K = "
            f"{len(past_model.segments)}, Silhouette = {_decimal(past_model.silhouette, 3)}). "
            "Nhóm có ý nghĩa thì nhóm giá trị cao phải mua lại nhiều hơn hẳn nhóm sắp mất."
        ),
        "",
        "| Nhóm (lúc mốc) | Số khách | Tỷ lệ mua lại sau mốc | Chi tiêu TB sau mốc |",
        "|---|---:|---:|---:|",
    ]
    for seg in past_model.segments:
        members = groups.get(seg.code, [])
        back = sum(1 for r in members if spent[r.account_id] > 0)
        mean_spent = sum(spent[r.account_id] for r in members) / max(len(members), 1)
        lines.append(
            f"| {seg.name} | {_decimal(len(members), 0)} "
            f"| {_percent(back / max(len(members), 1))} | {_money(mean_spent)} |"
        )
    back_all = sum(1 for r in before if spent[r.account_id] > 0)
    lines.append(
        f"| Tất cả | {_decimal(len(before), 0)} | {_percent(back_all / max(len(before), 1))} "
        f"| {_money(sum(spent[r.account_id] for r in before) / max(len(before), 1))} |"
    )
    return "\n".join(lines)


# ---- báo cáo ----


def summary(shop: Shop, ages: dict[int, int], per_mille: int) -> str:
    """Số liệu về mẫu dữ liệu."""
    per_customer = Counter(o.account_id for o in shop.orders)
    repeat = sum(1 for n in per_customer.values() if n >= 2)
    first = min(o.created_at for o in shop.orders)
    last = max(o.created_at for o in shop.orders)
    customers = max(len(per_customer), 1)
    lines = [
        "## Dữ liệu",
        "",
        (
            f"Mẫu {_decimal(per_mille / 10)}% khách (8 ký tự đầu của mã khách đổi sang số, chia "
            f"1000 dư nhỏ hơn {per_mille}). Từ {first:%d/%m/%Y} đến {last:%d/%m/%Y}: "
            f"{_decimal(len(per_customer), 0)} khách, {_decimal(len(shop.orders), 0)} đơn "
            f"(khách, ngày, kênh), {_decimal(shop.lines, 0)} lượt mua, "
            f"{_decimal(len(shop.items), 0)} sản phẩm; {_percent(repeat / customers)} khách "
            f"có từ 2 đơn, trung bình {_decimal(len(shop.orders) / customers)} đơn mỗi khách; "
            f"{_percent(len(ages) / customers)} khách có tuổi."
        ),
    ]
    if shop.skipped:
        lines.append("")
        lines += [f"- Bỏ {_decimal(n, 0)} dòng: {reason}." for reason, n in shop.skipped.items()]
    return "\n".join(lines)


def run(
    shop: Shop,
    ages: dict[int, int],
    per_mille: int,
    rounds: int,
    stability_rounds: int,
    max_cases: int,
) -> str:
    """Toàn bộ báo cáo dạng Markdown."""
    if not shop.orders:
        return "Không có giao dịch nào của khách trong mẫu."
    started = time.perf_counter()
    end = max(o.created_at for o in shop.orders) + timedelta(days=1)
    rec_cutoff = end - timedelta(days=REC_HOLDOUT_DAYS)
    segment_cutoff = end - timedelta(days=SEGMENT_HOLDOUT_DAYS)
    popular_before = recent_popular(shop.orders, rec_cutoff, REC_HOLDOUT_DAYS)

    def recent(case: Case) -> list[int]:
        return [p for p in popular_before if p not in case.signals.purchased][:K]

    parts = [
        "# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)",
        "",
        summary(shop, ages, per_mille),
        "",
        "## Gợi ý sản phẩm",
        "",
        evaluate_recommendations(
            shop,
            split(shop.orders, {}, {}),
            "Cách 1: giữ lại đơn cuối (như đánh giá trên dữ liệu mô phỏng)",
            rounds,
            max_cases,
        ),
        "",
        evaluate_recommendations(
            shop,
            split_by_time(shop.orders, rec_cutoff),
            f"Cách 2: cắt theo thời gian (mô hình thấy đến hết "
            f"{rec_cutoff - timedelta(days=1):%d/%m/%Y}, đoán {REC_HOLDOUT_DAYS} ngày sau)",
            rounds,
            max_cases,
            {RECENT_POPULAR: recent},
        ),
        "",
        "## Phân nhóm khách hàng",
        "",
        evaluate_segmentation(shop, ages, segment_cutoff, end, stability_rounds),
        "",
        f"Thời gian chạy (sau khi đọc tệp): {_decimal(time.perf_counter() - started, 0)} giây.",
    ]
    return "\n".join(parts)


def main(argv: list[str] | None = None) -> int:
    """Điểm vào dòng lệnh."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "--sample",
        type=int,
        default=SAMPLE_PER_MILLE,
        help="số phần nghìn khách đưa vào mẫu (mặc định 50 = 5%%)",
    )
    parser.add_argument("--rounds", type=int, default=BOOTSTRAP_ROUNDS, help="số lần bootstrap")
    parser.add_argument(
        "--stability-rounds",
        type=int,
        default=STABILITY_ROUNDS,
        help="số lần huấn luyện lại phân nhóm để đo độ ổn định",
    )
    parser.add_argument(
        "--max-cases", type=int, default=MAX_CASES, help="số khách tối đa mỗi cách chia"
    )
    args = parser.parse_args(argv)
    # Kiểm tra khoảng ngay khi đọc tham số: giá trị sai dừng ở đây (mã 2) thay vì lỗi sau khi đã
    # đọc tệp vài phút. int() tường minh để công cụ quét bảo mật (SonarQube) thấy giá trị đã là
    # số, không còn là chuỗi lấy từ dòng lệnh, trước khi đi vào báo cáo ghi ra tệp.
    sample = int(args.sample)
    rounds = int(args.rounds)
    stability_rounds = int(args.stability_rounds)
    max_cases = int(args.max_cases)
    if not 1 <= sample <= 1000:
        parser.error("--sample phải từ 1 đến 1000 (phần nghìn khách)")
    if min(rounds, stability_rounds, max_cases) < 1:
        parser.error("--rounds, --stability-rounds và --max-cases phải từ 1 trở lên")
    try:
        _progress("Đọc articles.csv")
        with open_csv(RAW_DIR, "articles") as rows:
            articles = load_articles(rows)
        _progress(f"Đọc transactions_train.csv ({len(articles)} sản phẩm-màu)")
        with open_csv(RAW_DIR, "transactions_train") as rows:
            shop = build_shop(rows, articles, sample)
        _progress(f"Đọc customers.csv ({len(shop.accounts)} khách trong mẫu)")
        with open_csv(RAW_DIR, "customers") as rows:
            ages = load_ages(rows, shop.accounts)
    except FileNotFoundError as error:
        print(error, file=sys.stderr)
        return 1
    report = run(shop, ages, sample, rounds, stability_rounds, max_cases)
    RESULT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with RESULT_FILE.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(report + "\n")
    _progress(f"Đã ghi {RESULT_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
