"""Đánh giá gợi ý và phân nhóm khách hàng trên dữ liệu mua hàng thật của H&M (Kaggle).

Nguồn: cuộc thi "H&M Personalized Fashion Recommendations" (H&M Group, Kaggle, 2022),
https://www.kaggle.com/competitions/h-and-m-personalized-fashion-recommendations. Lịch sử mua
thật của khách H&M từ 20/09/2018 đến 22/09/2020 (cửa hàng và trực tuyến); sản phẩm có loại hàng,
màu, mô tả; khách có tuổi. Điều 7A luật cuộc thi: chỉ dùng phi thương mại, cho nghiên cứu học thuật
và giáo dục. Điều 7B: không chia sẻ lại dữ liệu, nên tệp gốc và tệp đã lọc không bao giờ lên repo;
ai chạy lại thì tự tải sau khi đồng ý luật (data/hm/README.md).

Vì sao: dữ liệu mô phỏng (app/simulation) biết trước kiểu thật của từng khách nên đo được ARI,
nhưng hành vi do chính quy tắc của dự án sinh ra. Script này chạy ĐÚNG mã của ml-service
(ItemVectors, build_copurchase, Recommender, ranker, segmentation.model.train) trên hành vi mua
thật, không sửa gì trong mô hình.

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
- Phần sản phẩm: mặc định mọi sản phẩm. `--products shoes-bags-accessories` chỉ giữ nhóm hàng
  (product_group_name) Shoes, Bags, Accessories, cùng loại với hàng của website (giày, túi, phụ
  kiện): đơn, khách, R/F/M và gợi ý chỉ tính trên phần này, như thể H&M chỉ bán những loại hàng
  đó (mô hình xếp hạng học trên chính phần này, như website học trên đơn của chính nó; cấu hình
  giữ nguyên).

Gợi ý: cắt theo thời gian như cách chấm của cuộc thi. Mô hình chỉ thấy dữ liệu trước mốc, đoán sản
phẩm MỚI khách mua trong REC_HOLDOUT_DAYS ngày sau mốc (cuộc thi tính cả sản phẩm mua lại; cửa
hàng của dự án không gợi ý lại sản phẩm đã mua nên ở đây cũng không tính); trúng = ít nhất 1 trong
K sản phẩm gợi ý được mua. Mô hình xếp hạng (ranker.py) học trên TRAINING_WEEKS tuần ngay trước mốc,
đúng như trên website (mỗi tuần học chỉ dùng dữ liệu trước tuần đó). Cấu hình của nó (số tuần học,
đặc trưng, ứng viên) chọn trên các tuần trước tuần kiểm tra của mẫu 5% khách; báo cáo ghi lại tuần
kiểm định ngay trước tuần kiểm tra, mức đóng góp của từng nhóm đặc trưng trên tuần đó, rồi mới đo
tuần kiểm tra (7 ngày cuối của dữ liệu). Mỗi tuần đánh giá tối đa MAX_CASES khách chọn ngẫu nhiên
(seed cố định), kèm khoảng tin cậy 95% (bootstrap theo khách) cho tỷ lệ trúng và cho chênh lệch
giữa mô hình xếp hạng với từng cách khác (bootstrap ghép cặp: cùng mẫu khách cho cả 2 cách).
"Công thức hybrid" là cách website dùng khi chưa đủ dữ liệu học (model.Weights).

Cần bao nhiêu dữ liệu (`--data-size`, căn cứ của ranker.MIN_POSITIVES): chia mẫu thành các phần
rời nhau theo mã khách băm, cỡ PART_SIZES phần nghìn khách H&M; mỗi phần như 1 cửa hàng riêng (chỉ
thấy đơn của khách trong phần), học mô hình xếp hạng bỏ ngưỡng rồi so với công thức ở
VALIDATION_WEEKS tuần kiểm định (không dùng tuần kiểm tra). Ghi data/hm/ket-qua-luong-du-lieu.md.

Phân nhóm: R, F, M tính đến ngày sau ngày cuối (F = số đơn, M = tổng giá), huấn luyện bằng `train`
như trên cửa hàng; độ tuổi từng nhóm; độ ổn định (huấn luyện lại trên 80% khách, so nhóm của mọi
khách bằng ARI); kiểm tra trên tương lai: phân nhóm bằng dữ liệu trước SEGMENT_HOLDOUT_DAYS ngày
cuối, đo tỷ lệ khách từng nhóm mua lại và mức chi trong những ngày đó.

Chạy trong ml-service/ (không cần MySQL; đọc hết tệp 3,5 GB mất vài phút):
    uv run python -m scripts.eval_hm
    uv run python -m scripts.eval_hm --products shoes-bags-accessories
    uv run python -m scripts.eval_hm --data-size
Đọc articles.csv, customers.csv, transactions_train.csv (hoặc tệp .zip của từng tệp) trong
data/hm/raw, ghi kết quả vào data/hm/ket-qua.md (chỉ giày, túi, phụ kiện:
data/hm/ket-qua-giay-tui-phu-kien.md).
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
from array import array
from collections import Counter, defaultdict
from collections.abc import Callable, Iterable, Iterator, Sequence
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from operator import attrgetter
from pathlib import Path

import numpy as np
from sklearn.metrics import adjusted_rand_score
from threadpoolctl import threadpool_limits

from app.recommendation import ranker
from app.recommendation.data import Order
from app.recommendation.evaluate import K
from app.recommendation.features import CatalogItem, ItemVectors
from app.recommendation.model import DEFAULT_WEIGHTS, Recommender, Weights
from app.recommendation.ranker import Case
from app.segmentation.model import SegmentModel, train
from app.segmentation.rfm import Rfm, recency_days

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "hm" / "raw"
RESULT_DIR = REPO_ROOT / "data" / "hm"

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
RANKER = "Mô hình xếp hạng (mặc định)"
FORMULA = "Công thức hybrid (khi chưa đủ dữ liệu học)"
RECENT_POPULAR = f"Bán chạy {REC_HOLDOUT_DAYS} ngày trước mốc (không mô hình)"
SINGLE_SOURCES = {
    "Đang bán chạy": Weights(content=0, copurchase=0, popular=0, trending=1, style=0, diversity=0),
    "Bán chạy (mọi thời điểm)": Weights(
        content=0, copurchase=0, popular=1, trending=0, style=0, diversity=0
    ),
    "Nội dung (P(u), cosine)": Weights(
        content=1, copurchase=0, popular=0, trending=0, style=0, diversity=0
    ),
    "Hay mua cùng": Weights(content=0, copurchase=1, popular=0, trending=0, style=0, diversity=0),
}
"""Từng nguồn gợi ý riêng lẻ của công thức hybrid."""
AGE_BANDS = ((0, 25, "Dưới 25"), (25, 35, "25–34"), (35, 45, "35–44"), (45, 55, "45–54"))
OLDEST_BAND = "Từ 55"
PART_SIZES = (2, 5, 10, 25)
"""--data-size: cỡ mỗi phần khách (phần nghìn khách H&M), mỗi phần như 1 cửa hàng riêng."""
VALIDATION_WEEKS = 3
"""--data-size: số tuần kiểm định ngay trước tuần kiểm tra."""
DATA_SIZE_RESULT = "ket-qua-luong-du-lieu.md"
TOP_TYPES = 10
"""Số loại hàng nhiều lượt mua nhất ghi trong phần mô tả dữ liệu."""


@dataclass(frozen=True)
class Scope:
    """Phần sản phẩm đưa vào đánh giá và tên tệp kết quả (trong data/hm)."""

    groups: frozenset[str] | None
    """Nhóm hàng (product_group_name) được giữ; None là mọi sản phẩm."""
    label: str
    result_name: str


ALL_PRODUCTS = Scope(None, "mọi sản phẩm", "ket-qua.md")
SCOPES = {
    "all": ALL_PRODUCTS,
    "shoes-bags-accessories": Scope(
        frozenset({"Shoes", "Bags", "Accessories"}),
        "giày, túi, phụ kiện",
        "ket-qua-giay-tui-phu-kien.md",
    ),
}
"""Giá trị của --products."""


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


def customer_bucket(customer_id: str) -> int:
    """Số 0–999 của khách: 8 ký tự đầu của mã khách (mã băm hex) chia 1000 lấy dư."""
    return int(customer_id[:8], 16) % 1000


def in_sample(customer_id: str, per_mille: int) -> bool:
    """Khách thuộc mẫu: `customer_bucket` nhỏ hơn `per_mille`."""
    try:
        return customer_bucket(customer_id) < per_mille
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
    group: str = ""
    """Nhóm hàng (product_group_name), vd Shoes, Accessories, Garment Upper body."""
    type_name: str = ""


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
            (row.get("product_group_name") or "").strip(),
            (row.get("product_type_name") or "").strip(),
        )
    return result


# ---- chuyển sang dạng của ml-service ----


@dataclass
class Shop:
    """Dữ liệu đã chuyển: sản phẩm, đơn (theo thời gian), tổng tiền mỗi đơn, mã khách gốc."""

    items: list[CatalogItem]
    orders: list[Order]
    totals: Sequence[int]
    """Tổng giá của đơn, theo mã đơn (mã từ 1, ô 0 bỏ trống)."""
    accounts: dict[str, int]
    """customer_id gốc → mã tài khoản (số nguyên)."""
    lines: int = 0
    skipped: Counter = field(default_factory=Counter)
    outside: int = 0
    """Lượt mua của khách trong mẫu nhưng ngoài các nhóm hàng được giữ."""
    types: Counter = field(default_factory=Counter)
    """Số lượt mua theo loại hàng (product_type_name)."""


def build_shop(
    rows: Iterable[dict[str, str]],
    articles: dict[int, Article],
    per_mille: int,
    groups: frozenset[str] | None = None,
) -> Shop:
    """Đọc giao dịch của khách trong mẫu, gom thành đơn (khách, ngày, kênh) và sản phẩm; có
    `groups` thì chỉ giữ lượt mua sản phẩm thuộc các nhóm hàng đó."""
    accounts: dict[str, int] = {}
    orders: dict[tuple[int, datetime, str], Order] = {}
    # Mảng số thay cho dict/list số Python: toàn bộ dữ liệu có khoảng 30 triệu lượt mua.
    totals = array("q", [0])
    prices: dict[int, array] = defaultdict(lambda: array("d"))
    first_article: dict[int, Article] = {}
    days: dict[str, datetime] = {}
    skipped: Counter = Counter()
    types: Counter = Counter()
    kept = outside = 0
    for row in rows:
        customer = row.get("customer_id") or ""
        if not in_sample(customer, per_mille):
            continue
        price = _number(row.get("price"))
        article_id = _number(row.get("article_id"))
        article = None if article_id is None else articles.get(int(article_id))
        t_dat = row.get("t_dat") or ""
        day = days.get(t_dat)
        if day is None:
            try:
                day = datetime.strptime(t_dat, "%Y-%m-%d").replace(tzinfo=UTC)
            except ValueError:
                skipped["Ngày không đọc được"] += 1
                continue
            days[t_dat] = day
        if article is None:
            skipped["Sản phẩm không có trong articles.csv"] += 1
            continue
        if groups is not None and article.group not in groups:
            outside += 1
            continue
        if price is None or price <= 0:
            skipped["Giá không dương"] += 1
            continue
        account = accounts.setdefault(customer, len(accounts) + 1)
        # Khóa dùng lại đối tượng ngày đã đọc, không giữ chuỗi ngày riêng của từng dòng.
        key = (account, day, row.get("sales_channel_id") or "")
        order = orders.get(key)
        if order is None:
            order = Order(len(orders) + 1, account, day, "DELIVERED")
            orders[key] = order
            totals.append(0)
        code = article.product_code
        order.products[code] = order.products.get(code, 0) + 1
        totals[order.id] += round(price * PRICE_SCALE)
        prices[code].append(price)
        first_article.setdefault(code, article)
        types[article.type_name or str(article.type_no)] += 1
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
    # Sắp xếp ổn định giữ thứ tự tạo (mã đơn tăng dần) khi cùng ngày, không cần khóa phụ.
    ordered = sorted(orders.values(), key=attrgetter("created_at"))
    return Shop(items, ordered, totals, accounts, kept, skipped, outside, types)


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


def recent_popular(orders: list[Order], cutoff: datetime, days: int) -> list[int]:
    """Sản phẩm bán nhiều nhất trong `days` ngày trước `cutoff`, nhiều trước."""
    since = cutoff - timedelta(days=days)
    sold: Counter = Counter()
    for order in orders:
        if since <= order.created_at < cutoff:
            sold.update(order.products)
    return [p for p, _ in sorted(sold.items(), key=lambda x: (-x[1], x[0]))]


def by_time(cases: list[Case]) -> list[Case]:
    """Xếp ca theo thời điểm đoán: điểm "đang bán chạy" chỉ tính lại khi thời điểm đổi (thứ tự
    không đổi chỉ số, mọi cách gợi ý dùng cùng danh sách ca)."""
    return sorted(cases, key=lambda c: c.signals.now.timestamp() if c.signals.now else 0.0)


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


def _ids(model: Recommender, case: Case, weights: Weights, learned: ranker.Ranker | None):
    """Mã K sản phẩm gợi ý cho 1 khách bằng bộ trọng số và mô hình xếp hạng cho trước."""
    model.weights = weights
    model.ranker = learned
    return [s.product_id for s in model.for_account(case.signals, K)]


def methods_for(
    model: Recommender,
    learned: ranker.Ranker | None,
    popular_before: list[int],
    single_sources: bool,
) -> dict[str, Callable[[Case], list[int]]]:
    """Các cách gợi ý so sánh trên 1 tuần (mô hình xếp hạng đứng đầu nếu đã học được)."""
    methods: dict[str, Callable[[Case], list[int]]] = {}
    if learned is not None:
        methods[RANKER] = lambda case: _ids(model, case, DEFAULT_WEIGHTS, learned)
    methods[FORMULA] = lambda case: _ids(model, case, DEFAULT_WEIGHTS, None)

    def recent(case: Case) -> list[int]:
        return [p for p in popular_before if p not in case.signals.purchased][:K]

    methods[RECENT_POPULAR] = recent
    if single_sources:
        for name, weights in SINGLE_SOURCES.items():
            methods[name] = lambda case, weights=weights: _ids(model, case, weights, None)
    return methods


def compare(
    cases: list[Case],
    methods: dict[str, Callable[[Case], list[int]]],
    rounds: int,
    products: int,
) -> list[str]:
    """Bảng tỷ lệ trúng của các cách gợi ý và bảng chênh lệch giữa cách đầu tiên với từng cách
    khác (khoảng tin cậy ghép cặp)."""
    lines = [
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
            f"| {_decimal(_mean(ndcg), 3)} | {_percent(len(shown) / max(products, 1))} |"
        )
        _progress(f"  {name}: {_percent(_mean(hit))}")
    best, *others = hits
    lines += [
        "",
        "| So sánh | Chênh lệch tỷ lệ trúng (điểm %) | Khoảng tin cậy 95% | Kết luận |",
        "|---|---:|---|---|",
    ]
    short = best.split(" (")[0]
    for name in others:
        diff = hits[best] - hits[name]
        low, high = bootstrap_ci(diff, rounds)
        verdict = "tốt hơn" if low > 0 else "kém hơn" if high < 0 else "chưa phân biệt được"
        lines.append(
            f"| {short} − {name} | {_signed(_mean(diff))} | {_signed(low)} – {_signed(high)} "
            f"| {verdict} |"
        )
    return lines


def _top_hits(groups: np.ndarray, labels: np.ndarray, score: np.ndarray, cases: int) -> np.ndarray:
    """Trúng (0/1) của từng khách khi lấy K ứng viên điểm cao nhất của khách đó."""
    order = np.lexsort((-score, groups))
    grouped, labelled = groups[order], labels[order]
    first = np.flatnonzero(np.r_[True, grouped[1:] != grouped[:-1]])
    rank = np.arange(len(grouped)) - np.repeat(first, np.diff(np.r_[first, len(grouped)]))
    top = rank < K
    return (np.bincount(grouped[top], weights=labelled[top], minlength=cases) > 0).astype(float)


def feature_importance(
    model: Recommender, learned: ranker.Ranker, cases: list[Case], rounds: int
) -> list[str]:
    """Mức đóng góp của từng nhóm đặc trưng: xáo trộn giá trị cả nhóm giữa các dòng ứng viên
    (mô hình giữ nguyên), đo tỷ lệ trúng giảm bao nhiêu."""
    features, labels, groups = [], [], []
    for number, case in enumerate(cases):
        computed = ranker.case_rows(model, case)
        if computed is not None:
            _, x, y = computed
            features.append(x)
            labels.append(y)
            groups.append(np.full(len(y), number, dtype=np.int64))
    if not features:
        return ["Không có ứng viên nào để đo."]
    matrix, label, group = np.vstack(features), np.concatenate(labels), np.concatenate(groups)
    full = _top_hits(group, label, learned.predict(matrix), len(cases))
    rng = np.random.default_rng(SEED)
    lines = [
        f"Đủ đặc trưng: tỷ lệ trúng {_percent(_mean(full))}.",
        "",
        "| Nhóm đặc trưng xáo trộn | Tỷ lệ trúng | Giảm (điểm %) | Khoảng tin cậy 95% |",
        "|---|---:|---:|---|",
    ]
    for name, members in ranker.FEATURE_GROUPS.items():
        shuffled = matrix.copy()
        columns = [ranker.FEATURES.index(m) for m in members]
        shuffled[:, columns] = matrix[rng.permutation(len(matrix))][:, columns]
        hit = _top_hits(group, label, learned.predict(shuffled), len(cases))
        low, high = bootstrap_ci(full - hit, rounds)
        lines.append(
            f"| {name} | {_percent(_mean(hit))} | {_signed(_mean(full - hit))} "
            f"| {_signed(low)} – {_signed(high)} |"
        )
    return lines


def _training_note(learned: ranker.Ranker | None, cutoff: datetime) -> str:
    """1 câu về dữ liệu học của mô hình xếp hạng dùng cho tuần bắt đầu ở `cutoff`."""
    first = cutoff - timedelta(days=ranker.LABEL_DAYS * ranker.TRAINING_WEEKS)
    if learned is None:
        return (
            f"Mô hình xếp hạng: chưa đủ dữ liệu để học (cần ít nhất {_decimal(ranker.MIN_POSITIVES, 0)} lượt "
            f"mua trúng ứng viên trong {ranker.TRAINING_WEEKS} tuần trước mốc), chỉ có công thức."
        )
    info = learned.summary
    return (
        f"Mô hình xếp hạng học trên {info.weeks} tuần từ {first:%d/%m} đến "
        f"{cutoff - timedelta(days=1):%d/%m/%Y}: {_decimal(info.cases, 0)} lượt khách, "
        f"{_decimal(info.rows, 0)} dòng ứng viên, {_decimal(info.positives, 0)} lượt mua trúng "
        f"ứng viên ({len(ranker.FEATURES)} đặc trưng)."
    )


def evaluate_week(
    shop: Shop,
    model: Recommender,
    learned: ranker.Ranker | None,
    cases: list[Case],
    cutoff: datetime,
    rounds: int,
    single_sources: bool,
) -> list[str]:
    """Bảng so sánh các cách gợi ý trên tuần bắt đầu ở `cutoff`."""
    popular = recent_popular(shop.orders, cutoff, REC_HOLDOUT_DAYS)
    return [
        _training_note(learned, cutoff),
        "",
        *compare(
            cases, methods_for(model, learned, popular, single_sources), rounds, len(shop.items)
        ),
    ]


def evaluate_recommendations(
    shop: Shop, ages: dict[int, int], rounds: int, max_cases: int
) -> list[str]:
    """Tuần kiểm định (kèm mức đóng góp của đặc trưng) và tuần kiểm tra."""
    end = max(o.created_at for o in shop.orders) + timedelta(days=1)
    test_cutoff = end - timedelta(days=REC_HOLDOUT_DAYS)
    validation_cutoff = test_cutoff - timedelta(days=REC_HOLDOUT_DAYS)
    vectors = ItemVectors(shop.items)
    weeks: dict[datetime, ranker.WeekExamples] = {}
    lines: list[str] = []
    cutoffs = ranker.weekly_cutoffs(test_cutoff, ranker.TRAINING_WEEKS + 1)
    for cutoff, model, cases, week in ranker.training_weeks(
        vectors, shop.items, shop.orders, ages, cutoffs, max_cases, SEED
    ):
        weeks[cutoff] = week
        _progress(f"Tuần {cutoff:%d/%m}: {week.cases} khách, {week.positives} lượt mua trúng")
        if cutoff != validation_cutoff:
            continue
        learned = ranker.fit([weeks[c] for c in ranker.weekly_cutoffs(cutoff) if c in weeks])
        cases = by_time(cases)
        lines += [
            f"### Tuần kiểm định {cutoff:%d/%m}–{test_cutoff - timedelta(days=1):%d/%m/%Y}",
            "",
            (
                f"Mô hình thấy đến hết {cutoff - timedelta(days=1):%d/%m/%Y}; đánh giá "
                f"{_decimal(len(cases), 0)} khách. Tuần này dùng để chọn cấu hình, không phải kết quả."
            ),
            "",
            *evaluate_week(shop, model, learned, cases, cutoff, rounds, False),
        ]
        if learned is not None:
            lines += [
                "",
                "#### Đóng góp của từng nhóm đặc trưng (tuần kiểm định)",
                "",
                (
                    "Xáo trộn giá trị 1 nhóm đặc trưng giữa mọi dòng ứng viên (mô hình giữ nguyên): "
                    "tỷ lệ trúng giảm càng nhiều thì nhóm đó càng quan trọng."
                ),
                "",
                *feature_importance(model, learned, cases, rounds),
            ]
    learned = ranker.fit([weeks[c] for c in ranker.weekly_cutoffs(test_cutoff) if c in weeks])
    model = ranker.model_at(vectors, shop.items, shop.orders, test_cutoff, ranker.age_bands(ages))
    cases = by_time(
        ranker.sample(ranker.cases_at(shop.orders, test_cutoff, end, ages), max_cases, SEED)
    )
    _progress(f"Tuần kiểm tra {test_cutoff:%d/%m}: {len(cases)} khách")
    lines += [
        "",
        f"### Tuần kiểm tra {test_cutoff:%d/%m}–{end - timedelta(days=1):%d/%m/%Y} (kết quả)",
        "",
        (
            f"Mô hình thấy đến hết {test_cutoff - timedelta(days=1):%d/%m/%Y}, đoán "
            f"{REC_HOLDOUT_DAYS} ngày sau; đánh giá {_decimal(len(cases), 0)} khách; K = {K}; "
            f"{_decimal(len(shop.items), 0)} sản phẩm."
        ),
        "",
        *evaluate_week(shop, model, learned, cases, test_cutoff, rounds, True),
    ]
    return lines


# ---- cần bao nhiêu dữ liệu (--data-size) ----


@dataclass
class PartWeek:
    """1 phần khách ở 1 tuần kiểm định: lượt mua trúng ứng viên lúc học và trúng (0/1) của từng
    khách với mô hình xếp hạng và với công thức."""

    positives: int
    ranker_hits: np.ndarray
    formula_hits: np.ndarray


def evaluate_part(
    shop: Shop,
    ages: dict[int, int],
    vectors: ItemVectors,
    accounts: set[int],
    cutoffs: list[datetime],
    validation: set[datetime],
) -> list[PartWeek]:
    """1 phần khách như 1 cửa hàng riêng: chỉ thấy đơn của khách trong phần, học mô hình xếp hạng
    như website nhưng bỏ ngưỡng MIN_POSITIVES, so với công thức ở từng tuần kiểm định."""
    orders = [o for o in shop.orders if o.account_id in accounts]
    part_ages = {account: age for account, age in ages.items() if account in accounts}
    weeks: dict[datetime, ranker.WeekExamples] = {}
    results = []
    for cutoff, model, cases, week in ranker.training_weeks(
        vectors, shop.items, orders, part_ages, cutoffs, MAX_CASES, SEED
    ):
        weeks[cutoff] = week
        if cutoff not in validation:
            continue
        used = [weeks[c] for c in ranker.weekly_cutoffs(cutoff) if c in weeks]
        learned = ranker.fit(used, min_positives=1)
        if learned is None or not cases:
            continue
        cases = by_time(cases)
        ranked, formula = (
            case_scores(
                cases, lambda case, m=m, current=model: _ids(current, case, DEFAULT_WEIGHTS, m)
            )[0]
            for m in (learned, None)
        )
        results.append(PartWeek(sum(w.positives for w in used), ranked, formula))
    return results


def _size_row(size: int, customers: list[int], results: list[PartWeek], rounds: int) -> str:
    """1 dòng bảng: gộp mọi phần, mọi tuần kiểm định của 1 cỡ phần."""
    label = f"{_decimal(size / 10)}% ({_decimal(statistics.mean(customers), 0)} khách)"
    if not results:
        return f"| {label} | 0 | — | — | — | — | — | — | chưa học được |"
    ranked = np.concatenate([r.ranker_hits for r in results])
    formula = np.concatenate([r.formula_hits for r in results])
    diff = ranked - formula
    low, high = bootstrap_ci(diff, rounds)
    verdict = "tốt hơn" if low > 0 else "kém hơn" if high < 0 else "chưa phân biệt được"
    worse = sum(_mean(r.ranker_hits) < _mean(r.formula_hits) for r in results)
    return (
        f"| {label} | {len(results)} ({worse} lần kém hơn) "
        f"| {_decimal(statistics.mean(r.positives for r in results), 0)} "
        f"| {_decimal(len(diff), 0)} | {_percent(_mean(ranked))} | {_percent(_mean(formula))} "
        f"| {_signed(_mean(diff))} | {_signed(low)} – {_signed(high)} | {verdict} |"
    )


def evaluate_data_size(shop: Shop, ages: dict[int, int], per_mille: int, rounds: int) -> str:
    """Cần bao nhiêu dữ liệu để mô hình xếp hạng hơn công thức (căn cứ của MIN_POSITIVES): chia
    mẫu thành các phần rời nhau theo `customer_bucket`, gộp kết quả theo cỡ phần."""
    if not shop.orders:
        return "Không có giao dịch nào của khách trong mẫu."
    started = time.perf_counter()
    end = max(o.created_at for o in shop.orders) + timedelta(days=1)
    test_cutoff = end - timedelta(days=REC_HOLDOUT_DAYS)
    cutoffs = ranker.weekly_cutoffs(test_cutoff, ranker.TRAINING_WEEKS + VALIDATION_WEEKS)
    validation = set(cutoffs[-VALIDATION_WEEKS:])
    vectors = ItemVectors(shop.items)
    buckets: dict[int, set[int]] = defaultdict(set)
    for customer, account in shop.accounts.items():
        buckets[customer_bucket(customer)].add(account)
    first = min(validation)
    lines = [
        "# Cần bao nhiêu dữ liệu để mô hình xếp hạng hơn công thức (H&M)",
        "",
        (
            f"Mẫu {_decimal(per_mille / 10)}% khách ({_decimal(len(shop.accounts), 0)} khách) "
            "chia thành các phần rời nhau theo mã khách băm. Mỗi phần như 1 cửa hàng riêng: chỉ "
            f"thấy đơn của khách trong phần, mô hình xếp hạng học trên {ranker.TRAINING_WEEKS} tuần "
            "trước mốc như website (bỏ ngưỡng số lượt mua trúng) rồi so với công thức hybrid trên "
            f"chính khách của phần, ở {VALIDATION_WEEKS} tuần kiểm định từ {first:%d/%m} đến "
            f"{test_cutoff - timedelta(days=1):%d/%m/%Y} (không dùng tuần kiểm tra). Trúng = ít "
            f"nhất 1 trong {K} sản phẩm gợi ý được mua trong 7 ngày sau mốc."
        ),
        "",
        (
            "| Mỗi phần | Lần học | Lượt mua trúng ứng viên khi học (TB) | Khách đánh giá "
            "| Mô hình xếp hạng | Công thức hybrid | Chênh lệch (điểm %) | Khoảng tin cậy 95% "
            "| Kết luận |"
        ),
        "|---|---:|---:|---:|---:|---:|---:|---|---|",
    ]
    for size in (s for s in PART_SIZES if s <= per_mille):
        customers, results = [], []
        for start in range(0, per_mille - size + 1, size):
            accounts = set().union(*(buckets[b] for b in range(start, start + size)))
            customers.append(len(accounts))
            results += evaluate_part(shop, ages, vectors, accounts, cutoffs, validation)
        _progress(f"Phần {_decimal(size / 10)}%: {len(results)} lần học")
        lines.append(_size_row(size, customers, results, rounds))
    lines += [
        "",
        (
            "Lượt mua trúng ứng viên: số lần khách mua 1 sản phẩm mới nằm trong danh sách ứng viên, "
            f"cộng trên {ranker.TRAINING_WEEKS} tuần học. Website chỉ dùng mô hình xếp hạng khi có "
            f"ít nhất {_decimal(ranker.MIN_POSITIVES, 0)} lượt (`ranker.MIN_POSITIVES`), ít hơn thì "
            "dùng công thức hybrid."
        ),
        "",
        f"Thời gian chạy (sau khi đọc tệp): {_decimal(time.perf_counter() - started, 0)} giây.",
    ]
    return "\n".join(lines)


# ---- phân nhóm ----


def rfm_at(orders: Iterable[Order], totals: Sequence[int], now: datetime) -> list[Rfm]:
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


def summary(shop: Shop, ages: dict[int, int], per_mille: int, scope: Scope = ALL_PRODUCTS) -> str:
    """Số liệu về mẫu dữ liệu, phần sản phẩm được giữ và các loại hàng mua nhiều nhất."""
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
    if scope.groups is not None:
        bought = shop.lines + shop.outside
        lines += [
            "",
            (
                f"Chỉ giữ nhóm hàng {', '.join(sorted(scope.groups))} của H&M ({scope.label}, "
                f"cùng loại với hàng của website): {_decimal(shop.lines, 0)} trên "
                f"{_decimal(bought, 0)} lượt mua của khách trong mẫu "
                f"({_percent(shop.lines / max(bought, 1))}). Đơn, khách, R/F/M và gợi ý chỉ tính "
                "trên phần này, như thể H&M chỉ bán những loại hàng đó (mô hình xếp hạng học trên "
                "chính phần này; cấu hình giữ nguyên)."
            ),
        ]
    if shop.skipped:
        lines.append("")
        lines += [f"- Bỏ {_decimal(n, 0)} dòng: {reason}." for reason, n in shop.skipped.items()]
    lines += [
        "",
        f"Loại hàng nhiều lượt mua nhất (product_type_name, {TOP_TYPES} loại đầu):",
        "",
        "| Loại hàng | Lượt mua | Tỷ lệ |",
        "|---|---:|---:|",
    ]
    lines += [
        f"| {name} | {_decimal(count, 0)} | {_percent(count / max(shop.lines, 1))} |"
        for name, count in shop.types.most_common(TOP_TYPES)
    ]
    return "\n".join(lines)


def run(
    shop: Shop,
    ages: dict[int, int],
    per_mille: int,
    rounds: int,
    stability_rounds: int,
    max_cases: int,
    scope: Scope = ALL_PRODUCTS,
) -> str:
    """Toàn bộ báo cáo dạng Markdown."""
    if not shop.orders:
        return "Không có giao dịch nào của khách trong mẫu."
    title = "# Đánh giá trên dữ liệu mua hàng thật: H&M (Kaggle)"
    if scope.groups is not None:
        title += f", chỉ {scope.label}"
    started = time.perf_counter()
    end = max(o.created_at for o in shop.orders) + timedelta(days=1)
    segment_cutoff = end - timedelta(days=SEGMENT_HOLDOUT_DAYS)
    parts = [
        title,
        "",
        summary(shop, ages, per_mille, scope),
        "",
        "## Gợi ý sản phẩm",
        "",
        (
            "Cắt theo thời gian như cách chấm của cuộc thi: mô hình chỉ thấy dữ liệu trước mốc, đoán "
            f"sản phẩm mới (chưa từng mua) khách mua trong {REC_HOLDOUT_DAYS} ngày sau mốc; trúng = "
            f"ít nhất 1 trong {K} sản phẩm gợi ý được mua. Mô hình xếp hạng học trên "
            f"{ranker.TRAINING_WEEKS} tuần ngay trước mốc, mỗi tuần học chỉ dùng dữ liệu trước tuần "
            "đó (như website học lại mỗi đêm)."
        ),
        "",
        *evaluate_recommendations(shop, ages, rounds, max_cases),
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
        "--max-cases", type=int, default=MAX_CASES, help="số khách tối đa mỗi tuần đánh giá"
    )
    parser.add_argument(
        "--products",
        choices=sorted(SCOPES),
        default="all",
        help="phần sản phẩm: all (mọi sản phẩm, ghi ket-qua.md) hoặc shoes-bags-accessories "
        "(chỉ nhóm hàng Shoes, Bags, Accessories như website, ghi ket-qua-giay-tui-phu-kien.md)",
    )
    parser.add_argument(
        "--data-size",
        action="store_true",
        help="chỉ đo cần bao nhiêu dữ liệu để mô hình xếp hạng hơn công thức (chia mẫu thành phần "
        f"nhỏ, chỉ tuần kiểm định), ghi {DATA_SIZE_RESULT}",
    )
    args = parser.parse_args(argv)
    # Chọn bằng phép so sánh, không tra dict bằng chuỗi dòng lệnh: tên tệp kết quả và nhãn trong
    # báo cáo luôn là hằng số của SCOPES (SonarQube theo dõi giá trị dòng lệnh, issue #142).
    scope = next(value for name, value in SCOPES.items() if name == args.products)
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
    if args.data_size and scope is not ALL_PRODUCTS:
        parser.error("--data-size chỉ dùng với mọi sản phẩm")
    try:
        _progress("Đọc articles.csv")
        with open_csv(RAW_DIR, "articles") as rows:
            articles = load_articles(rows)
        _progress(f"Đọc transactions_train.csv ({len(articles)} sản phẩm-màu)")
        with open_csv(RAW_DIR, "transactions_train") as rows:
            shop = build_shop(rows, articles, sample, scope.groups)
        _progress(f"Đọc customers.csv ({len(shop.accounts)} khách trong mẫu)")
        with open_csv(RAW_DIR, "customers") as rows:
            ages = load_ages(rows, shop.accounts)
    except FileNotFoundError as error:
        print(error, file=sys.stderr)
        return 1
    # 1 luồng cho numpy, scikit-learn: hàng chục nghìn phép tính nhỏ mỗi tuần, nhiều luồng chỉ tốn
    # công đồng bộ (chậm gấp đôi, xem Dockerfile của ml-service).
    with threadpool_limits(limits=1):
        if args.data_size:
            report = evaluate_data_size(shop, ages, sample, rounds)
        else:
            report = run(shop, ages, sample, rounds, stability_rounds, max_cases, scope)
    result_file = RESULT_DIR / (DATA_SIZE_RESULT if args.data_size else scope.result_name)
    result_file.parent.mkdir(parents=True, exist_ok=True)
    with result_file.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write(report + "\n")
    _progress(f"Đã ghi {result_file}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
