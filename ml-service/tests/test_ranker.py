"""Test mô hình xếp hạng (ranker.py) và số liệu bán hàng tại 1 thời điểm (SalesHistory.snapshot).

Cửa hàng giả: khách trẻ (22 tuổi) và khách lớn tuổi (52 tuổi) mỗi tuần đổ xô mua 1 sản phẩm "đang
hot" của riêng nhóm tuổi mình. Mô hình xếp hạng phải học được: sản phẩm đang bán chạy trong nhóm
tuổi của khách thì khách hay mua tiếp.
"""

import random
from datetime import date, datetime, timedelta

import numpy as np
import pytest

from app.recommendation import ranker
from app.recommendation.copurchase import build_copurchase
from app.recommendation.data import Order, ProductStats, UserSignals, age_on
from app.recommendation.features import CatalogItem, ItemVectors
from app.recommendation.model import SOURCE_BEHAVIOUR, Recommender
from app.recommendation.trending import AGE_BANDS, NEVER_SOLD_DAYS, SalesHistory, age_band
from tests.test_recommendation_model import ITEMS as SMALL_ITEMS
from tests.test_recommendation_model import STATS as SMALL_STATS

NOW = datetime(2026, 10, 30, 14, 0)
DAY = timedelta(days=1)

# ---- nhóm tuổi, ngày sinh, lần mua ----


@pytest.mark.parametrize(
    ("age", "band"),
    [(None, -1), (0, -1), (-3, -1), (19, 0), (20, 1), (24, 1), (25, 2), (59, 8), (60, 9), (90, 9)],
)
def test_age_band(age, band):
    assert age_band(age) == band
    assert AGE_BANDS == 10


def test_age_on_counts_whole_years():
    assert age_on(date(2000, 11, 20), date(2026, 11, 19)) == 25
    assert age_on(date(2000, 11, 20), date(2026, 11, 20)) == 26
    assert age_on(None, date(2026, 11, 20)) is None


def test_age_bands_skip_unknown_ages():
    assert ranker.age_bands({1: 22, 2: 52, 3: 0}) == {1: 1, 2: 7}


def test_buy_keeps_the_latest_purchase_time():
    signals = UserSignals(1)
    signals.buy(5, NOW - 2 * DAY)
    signals.buy(5, NOW - 5 * DAY)
    signals.buy(6, None)
    assert signals.purchased == {5, 6}
    assert signals.purchased_at == {5: NOW - 2 * DAY}
    assert signals.last_at == {5: NOW - 2 * DAY}
    signals.buy(5, NOW)
    assert signals.purchased_at[5] == NOW


# ---- số liệu bán hàng tại 1 thời điểm ----


def test_snapshot_counts_only_sales_before_now():
    young, old = age_band(22), age_band(52)
    sales = SalesHistory(
        [
            (1, NOW - 1 * DAY, 2, 10),  # khách 10: trẻ
            (1, NOW - 2 * DAY, 1, 11),  # khách 11: lớn tuổi
            (1, NOW - 2 * DAY, 1, 10),  # khách 10 mua lại: vẫn là 1 người mua
            (2, NOW - 10 * DAY, 3, 11),  # tuần trước
            (2, NOW - 40 * DAY, 5, 12),  # ngoài 30 ngày: chỉ tính vào tổng đã bán
            (3, NOW + timedelta(hours=1), 4, 10),  # sau thời điểm tính: bỏ
            (9, NOW - 1 * DAY, 1, 10),  # sản phẩm không còn bán
        ],
        {1: 0, 2: 1, 3: 2, 4: 3},
        4,
        {10: young, 11: old},
    )
    snapshot = sales.snapshot(NOW, np.array([0, 0, 1, 1]), 2)

    def decay(days, half_life=7.0):
        return 0.5 ** (days / half_life)

    weekly = [2 * decay(1) + 2 * decay(2), 3 * decay(10), 0, 0]
    assert snapshot.decayed[7.0] == pytest.approx(weekly)
    assert snapshot.decayed[1.0][0] == pytest.approx(2 * 0.5 + 2 * 0.25)
    assert snapshot.sold_7d.tolist() == [4, 0, 0, 0]
    assert snapshot.sold_prev_7d.tolist() == [0, 3, 0, 0]
    assert snapshot.sold_all.tolist() == [4, 8, 0, 0]
    assert snapshot.buyers_7d.tolist() == [2, 0, 0, 0]
    assert snapshot.days_since_first_sale.tolist() == pytest.approx([2, 40, 999, 999])
    assert snapshot.days_since_last_sale.tolist() == pytest.approx([1, 10, 999, 999])
    assert NEVER_SOLD_DAYS == 999

    # Theo nhóm tuổi: khách 12 không rõ tuổi (và mua ngoài 30 ngày) không vào nhóm nào.
    assert snapshot.age_band_trend[young] == pytest.approx([2 * decay(1) + decay(2), 0, 0, 0])
    assert snapshot.age_band_trend[old] == pytest.approx([decay(2), 3 * decay(10), 0, 0])
    assert snapshot.by_age_band[young].tolist() == [0]
    assert snapshot.by_age_band[old].tolist() == [1, 0]  # 3 lượt tuần trước > 1 lượt 2 ngày trước
    assert snapshot.by_age_band[0].tolist() == []

    assert snapshot.by_trend.tolist() == [0, 1]
    assert snapshot.rank_overall.tolist() == [0, 1, 2, 3]
    assert snapshot.category_trend == pytest.approx([sum(weekly)] * 2 + [0, 0])
    assert snapshot.rank_in_category.tolist() == [0, 0.5, 0, 0.5]
    assert [rows.tolist() for rows in snapshot.by_category] == [[0, 1], []]

    # Chưa có lượt bán nào trước thời điểm tính.
    empty = sales.snapshot(NOW - 60 * DAY, np.array([0, 0, 1, 1]), 2)
    assert not empty.sold_all.any() and empty.by_trend.size == 0
    assert empty.days_since_first_sale.tolist() == [NEVER_SOLD_DAYS] * 4


# ---- cửa hàng giả ----

START = datetime(2026, 8, 3)
END = START + timedelta(days=56)
"""Mốc gợi ý của các test: học trên 5 tuần trước, đoán 7 ngày sau."""
DAYS = 63
ACCOUNTS = range(1, 161)
WORDS = ["cotton", "denim", "len", "da", "lụa", "nỉ"]
CATEGORY_NAMES = {1: "Áo thun", 2: "Giày", 3: "Túi xách"}
ITEMS = [
    CatalogItem(
        i,
        1 + (i - 1) // 8,
        None,
        f"{CATEGORY_NAMES[1 + (i - 1) // 8]} {WORDS[i % 6]} mẫu {i}",
        f"Chất {WORDS[(i + 2) % 6]} mềm, dễ phối đồ",
        100_000 + 25_000 * (i % 8),
    )
    for i in range(1, 25)
]


def age_of(account: int) -> int | None:
    """Khách lẻ 22 tuổi, khách chẵn 52 tuổi; cứ 10 khách thì 1 khách không khai tuổi."""
    if account % 10 == 0:
        return None
    return 22 if account % 2 else 52


def hot(day: int, account: int) -> int | None:
    """Sản phẩm nhóm tuổi của khách đang đổ xô mua: đổi mỗi 7 ngày, lệch mốc tuần 3 ngày."""
    period = (day - 3) // 7
    if period < 0:
        return None
    return 1 + 2 * period + (0 if account % 2 else 1)


def make_world() -> tuple[ItemVectors, list[Order], dict[int, int]]:
    """Vector sản phẩm, các đơn (63 ngày từ START) và tuổi khách của cửa hàng giả."""
    rng = random.Random(7)
    orders = []
    for day in range(DAYS):
        for account in ACCOUNTS:
            if rng.random() >= 0.2:
                continue
            product = hot(day, account)
            if product is None or rng.random() >= 0.8:
                product = rng.randint(1, len(ITEMS))
            at = START + timedelta(days=day, hours=rng.randint(8, 20))
            orders.append(Order(len(orders) + 1, account, at, "DELIVERED", {product: 1}))
    ages = {a: age for a in ACCOUNTS if (age := age_of(a)) is not None}
    return ItemVectors(ITEMS), orders, ages


@pytest.fixture(scope="module")
def world():
    return make_world()


@pytest.fixture(scope="module")
def learned(world):
    vectors, orders, ages = world
    result = ranker.train(vectors, ITEMS, orders, ages, END)
    assert result is not None
    return result


@pytest.fixture
def model(world, learned):
    vectors, orders, ages = world
    built = ranker.model_at(vectors, ITEMS, orders, END, ranker.age_bands(ages))
    built.ranker = learned
    return built


def parts_of(model, signals):
    return model.behaviour_parts(signals, model.interaction_weights(signals))


def test_cases_at_uses_only_the_past():
    cutoff = NOW
    orders = [
        Order(1, 1, cutoff - 10 * DAY, "DELIVERED", {1: 1, 2: 1}),
        Order(2, 1, cutoff - 5 * DAY, "PENDING", {3: 1}),
        Order(3, 1, cutoff + DAY, "DELIVERED", {1: 1, 4: 2}),  # mua lại 1 không tính
        Order(4, 1, cutoff + 8 * DAY, "DELIVERED", {5: 1}),  # sau 7 ngày
        Order(5, 2, cutoff + DAY, "DELIVERED", {6: 1}),  # chưa mua gì trước mốc
        Order(6, 3, cutoff - 3 * DAY, "DELIVERED", {1: 1}),
        Order(7, 3, cutoff + DAY, "DELIVERED", {1: 1}),  # chỉ mua lại
    ]
    (case,) = ranker.cases_at(orders, cutoff, cutoff + 7 * DAY, {1: 30})
    signals = case.signals
    assert case.targets == {4}
    assert signals.account_id == 1 and signals.now == cutoff and signals.age == 30
    assert signals.purchased == {1, 2, 3}
    assert signals.delivered_orders == 1  # đơn đang giao không tính
    assert signals.purchased_at == {1: cutoff - 10 * DAY, 2: cutoff - 10 * DAY, 3: cutoff - 5 * DAY}
    assert ranker.cases_at(orders, cutoff, cutoff + 7 * DAY)[0].signals.age is None


def test_sample_and_weekly_cutoffs():
    cases = [ranker.Case(UserSignals(i), {i}) for i in range(10)]
    assert ranker.sample(cases, 20, 0) is cases
    picked = ranker.sample(cases, 4, 0)
    assert len(picked) == 4 and picked == ranker.sample(cases, 4, 0)
    assert picked == sorted(picked, key=lambda c: c.signals.account_id)
    assert ranker.weekly_cutoffs(END, 3) == [END - 21 * DAY, END - 14 * DAY, END - 7 * DAY]


def test_model_at_sees_only_orders_before_the_cutoff(world):
    vectors, orders, _ = world
    cutoff = START + 10 * DAY
    built = ranker.model_at(vectors, ITEMS, orders, cutoff)
    sold = sum(sum(o.products.values()) for o in orders if o.created_at < cutoff)
    assert built.snapshot(cutoff).sold_all.sum() == sold
    assert built.snapshot(cutoff) is built.snapshot(cutoff)  # cùng thời điểm thì không tính lại
    assert built.available.all()  # tồn kho trong quá khứ không lưu: coi như còn hàng


def test_category_share_weights_recent_purchases_more():
    small = ItemVectors(SMALL_ITEMS)
    model = Recommender(small, SMALL_ITEMS, SMALL_STATS, build_copurchase([], small.index, 8), {})
    signals = UserSignals(1, now=NOW)
    signals.buy(1, NOW - 30 * DAY)  # giày, 30 ngày trước: nặng một nửa
    signals.buy(4, NOW)  # túi xách
    signals.buy(99, NOW)  # sản phẩm không còn bán
    assert ranker.category_share(model, signals) == pytest.approx([1 / 3, 2 / 3, 0])
    signals.now = None
    assert not ranker.category_share(model, signals).any()


def customer(age: int | None) -> UserSignals:
    """Khách tại mốc END: 1 đơn Đã giao 20 ngày trước, chỉ có sản phẩm 1 (áo thun)."""
    signals = UserSignals(999, delivered_orders=1, now=END, age=age)
    signals.buy(1, END - 20 * DAY)
    return signals


def test_candidates_are_allowed_and_come_from_each_pool(model, monkeypatch):
    young = customer(22)
    young.cart.add(9)
    parts = parts_of(model, young)
    snapshot = model.snapshot(END)
    rows = ranker.candidates(model, snapshot, young, parts)
    index = model.vectors.index
    assert rows.tolist() == sorted(set(rows.tolist()))
    assert parts.allowed[rows].all()
    assert index[1] not in rows and index[9] not in rows  # đã mua, đang trong giỏ

    for pool in ("POOL_TREND", "POOL_CONTENT", "POOL_COPURCHASE", "POOL_HYBRID", "POOL_CATEGORY"):
        monkeypatch.setattr(ranker, pool, 0)
    monkeypatch.setattr(ranker, "POOL_AGE", 2)
    # Chỉ còn nguồn theo nhóm tuổi: 2 sản phẩm hot gần đây nhất của chính nhóm tuổi của khách.
    assert ranker.candidates(model, snapshot, young, parts).tolist() == [index[13], index[15]]
    old = customer(52)
    rows = ranker.candidates(model, snapshot, old, parts_of(model, old))
    assert rows.tolist() == [index[14], index[16]]
    unknown = customer(None)
    assert ranker.candidates(model, snapshot, unknown, parts_of(model, unknown)).size == 0

    monkeypatch.setattr(ranker, "POOL_CATEGORY", 1)
    # Khách chỉ mua áo thun (danh mục 1): thêm sản phẩm áo thun bán chạy nhất còn được gợi ý.
    rows = ranker.candidates(model, snapshot, unknown, parts_of(model, unknown))
    assert len(rows) == 1 and model.vectors.category_ids[rows[0]] == 1


def test_feature_matrix_has_one_column_per_feature(model):
    assert len(ranker.FEATURES) == len(set(ranker.FEATURES)) == 28
    grouped = [name for members in ranker.FEATURE_GROUPS.values() for name in members]
    assert sorted(grouped) == sorted(ranker.FEATURES)

    def column(matrix, name):
        return matrix[:, ranker.FEATURES.index(name)]

    young = customer(22)
    snapshot = model.snapshot(END)
    parts = parts_of(model, young)
    rows = ranker.candidates(model, snapshot, young, parts)
    matrix = ranker.feature_matrix(model, snapshot, young, parts, rows)
    assert matrix.shape == (len(rows), 28) and matrix.dtype == np.float32
    assert (column(matrix, "customer_age") == 22).all()
    assert (column(matrix, "customer_orders") == 1).all()
    assert (column(matrix, "customer_products") == 1).all()
    assert column(matrix, "days_since_last_purchase") == pytest.approx(20)
    assert column(matrix, "log_price") == pytest.approx(model.log_prices[rows])
    assert column(matrix, "trend_7d") == pytest.approx(np.log1p(snapshot.decayed[7.0][rows]))
    hot_row = list(rows).index(model.vectors.index[15])
    assert column(matrix, "age_band_lift")[hot_row] > 0  # khách trẻ mua nhiều hơn mặt bằng chung

    unknown = UserSignals(998, viewed={2, 3, 4, 5, 6}, now=END)
    parts = parts_of(model, unknown)
    rows = ranker.candidates(model, snapshot, unknown, parts)
    matrix = ranker.feature_matrix(model, snapshot, unknown, parts, rows)
    assert (column(matrix, "customer_age") == -1).all()
    assert not column(matrix, "age_band_lift").any()
    assert column(matrix, "age_band_trend_7d") == pytest.approx(column(matrix, "trend_7d"))
    assert (column(matrix, "days_since_last_purchase") == NEVER_SOLD_DAYS).all()
    assert not column(matrix, "price_vs_customer").any()


def test_case_rows_needs_interactions_and_sales(world, model):
    vectors, _, _ = world
    assert ranker.case_rows(model, ranker.Case(UserSignals(1, now=END), {2})) is None
    no_sales = Recommender(
        vectors,
        ITEMS,
        {i.id: ProductStats(i.price, True, 0, None) for i in ITEMS},
        build_copurchase([], vectors.index, len(ITEMS)),
        {},
    )
    case = ranker.Case(customer(22), {15})
    assert ranker.case_rows(no_sales, case) is None
    rows, matrix, labels = ranker.case_rows(model, case)
    assert labels.tolist() == [int(r == model.vectors.index[15]) for r in rows]
    assert matrix.shape[0] == len(rows)

    no_interactions = ranker.Case(UserSignals(1, now=END), {2})
    empty = ranker.week_examples(model, [no_interactions], np.random.default_rng(0))
    assert empty.features.shape == (0, 28) and empty.cases == empty.positives == 0


def test_fit_needs_enough_positives_and_both_labels(monkeypatch):
    zeros = np.zeros((10, 28), dtype=np.float32)
    assert ranker.fit([ranker.WeekExamples(zeros, np.zeros(10, np.int8), 1, 0)]) is None
    monkeypatch.setattr(ranker, "MIN_POSITIVES", 5)
    assert ranker.fit([ranker.WeekExamples(zeros, np.ones(10, np.int8), 1, 10)]) is None


def test_train_without_orders_gives_no_ranker(world):
    vectors, _, _ = world
    assert ranker.train(vectors, ITEMS, [], {}, END) is None


def test_learned_ranker_follows_the_age_band_trend(world, learned, model):
    _, orders, ages = world
    assert learned.summary.weeks == ranker.TRAINING_WEEKS
    assert learned.summary.positives >= ranker.MIN_POSITIVES
    assert learned.summary.rows > learned.summary.positives

    cases = ranker.cases_at(orders, END, END + 7 * DAY, ages)
    picks = {22: [], 52: []}
    formula_picks = []
    for case in cases:
        signals = case.signals
        own_hot = 15 if signals.age == 22 else 16
        if signals.age is None or own_hot in signals.purchased:
            continue
        model.ranker = learned
        result = model.for_account(signals, 10)
        picks[signals.age].append(result[0].product_id == own_hot)
        assert all(s.source == SOURCE_BEHAVIOUR and s.reason for s in result)
        scores = [s.score for s in result]
        assert scores == sorted(scores, reverse=True) and 0 <= scores[-1] <= scores[0] <= 1
        assert not {s.product_id for s in result} & signals.purchased
        model.ranker = None
        formula_picks.append(model.for_account(signals, 1)[0].product_id == own_hot)
    # Sản phẩm đang hot của chính nhóm tuổi đứng đầu với gần như mọi khách trẻ và lớn tuổi; công
    # thức hybrid không biết nhóm tuổi nên hay đưa sản phẩm hot của nhóm kia lên đầu.
    assert len(picks[22]) >= 15 and len(picks[52]) >= 15
    assert np.mean(picks[22]) >= 0.9 and np.mean(picks[52]) >= 0.9
    assert np.mean(formula_picks) < np.mean(picks[22] + picks[52])


class _Unused:
    """Mô hình xếp hạng giả: test hỏng nếu bị gọi."""

    def rank(self, *args):
        raise AssertionError("không được gọi mô hình xếp hạng")


class _NoCandidates:
    """Mô hình xếp hạng giả không tìm được ứng viên nào."""

    def rank(self, *args):
        return None


def test_formula_is_used_without_time_or_candidates(model):
    signals = customer(22)
    formula = model.ranker
    model.ranker = None
    expected = [s.product_id for s in model.for_account(signals, 5)]

    model.ranker = _NoCandidates()
    assert [s.product_id for s in model.for_account(signals, 5)] == expected
    model.ranker = _Unused()
    signals.now = None
    assert model.for_account(signals, 5)
    model.ranker = formula


def test_rank_without_candidates_or_sales(world, learned, model):
    vectors, _, _ = world
    everything = UserSignals(1, delivered_orders=1, now=END)
    for item in ITEMS:
        everything.buy(item.id, END - DAY)
    assert learned.rank(model, everything, parts_of(model, everything), 10) is None
    assert ranker.case_rows(model, ranker.Case(everything, {1})) is None
    assert model.for_account(everything, 10) == []

    no_sales = Recommender(
        vectors,
        ITEMS,
        {i.id: ProductStats(i.price, True, 0, None) for i in ITEMS},
        build_copurchase([], vectors.index, len(ITEMS)),
        {},
    )
    signals = customer(22)
    assert learned.rank(no_sales, signals, parts_of(no_sales, signals), 10) is None
