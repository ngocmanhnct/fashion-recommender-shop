"""Test so các bộ đặc trưng phân nhóm (R, F, M và đặc trưng thêm)."""

import random
from dataclasses import replace
from datetime import timedelta

from app.segmentation import evaluate
from app.segmentation.features import RFM, CustomerFeatures, compare, load_features, report
from app.simulation.personas import assign_personas, customer_email
from tests.db_helpers import create_account
from tests.test_recommendation_db import NOW, add_order, add_views, rec_db  # noqa: F401

PRICE_SETS = (RFM, (*RFM, "avg_item_price"))


def customer(account_id, r, f, m, price, rng):
    """Khách quanh (R, F, M, giá TB/món) đã cho, lệch ngẫu nhiên ±10%."""

    def jitter(value):
        return value * rng.uniform(0.9, 1.1)

    f = max(1, round(jitter(f)))
    m = jitter(m)
    return CustomerFeatures(
        account_id=account_id,
        recency=round(jitter(r)),
        frequency=f,
        monetary=m,
        avg_order_value=m / f,
        avg_item_price=jitter(price),
        tenure=0.0,
        purchase_gap=0.0,
        viewed_products=0.0,
    )


def test_extra_feature_separates_types_that_rfm_cannot():
    # SAVER và LOYAL giống nhau ở R, F, M, chỉ khác giá mỗi món.
    groups = {
        "VIP": (5, 15, 12_000_000, 300_000),
        "LOST": (300, 2, 500_000, 150_000),
        "LOYAL": (20, 6, 2_500_000, 400_000),
        "SAVER": (20, 6, 2_500_000, 40_000),
    }
    rng = random.Random(3)
    rows, truth = [], {}
    for code, (r, f, m, price) in groups.items():
        for _ in range(15):
            rows.append(customer(len(rows) + 1, r, f, m, price, rng))
            truth[len(rows)] = code
    rfm, with_price = compare(rows, truth, PRICE_SETS)
    assert rfm.label == "R, F, M" and with_price.label == "R, F, M, giá TB/món"
    assert with_price.ari > rfm.ari + 0.2
    assert with_price.k == 4 and with_price.silhouette > 0.3

    table = report([rfm, with_price])
    assert "| R, F, M |" in table and f"| {with_price.ari - rfm.ari:+.3f} |" in table


def test_compare_skips_unlabelled_customers_and_sets_without_enough_points():
    rng = random.Random(1)
    rows = [customer(i, 10, 2, 300_000, 100_000, rng) for i in range(1, 9)]
    same = [replace(r, viewed_products=0.0) for r in rows]
    # Mọi khách cùng lượt xem: bộ chỉ có lượt xem không chia được nhóm nên bị bỏ qua.
    assert compare(same, {r.account_id: "NEW" for r in same}, [("viewed_products",)]) == []
    assert compare(rows, {}, [RFM]) == []
    assert report([]).count("\n") == 1  # chỉ có tiêu đề bảng


def test_load_features_reads_delivered_orders_items_and_recent_views(rec_db):  # noqa: F811
    with rec_db.begin() as conn:
        a = create_account(conn, "dactrung@example.com")
        add_order(conn, a, [1, 2], at=NOW - timedelta(days=10))
        add_order(conn, a, [3], at=NOW - timedelta(days=40))
        add_order(conn, a, [4, 5], status="CANCELLED", at=NOW - timedelta(days=2))
        add_views(conn, a, [1, 2, 1])
        add_views(conn, a, [4], at=NOW - timedelta(days=100))
        no_delivered = create_account(conn, "chuagiao@example.com")
        add_order(conn, no_delivered, [1], status="PENDING")
        rows = load_features(conn, NOW)
    assert [r.account_id for r in rows] == [a]
    row = rows[0]
    assert (row.recency, row.frequency, row.monetary) == (10, 2, 200_000)
    assert row.avg_order_value == 100_000
    assert row.avg_item_price == 1000  # 3 món, mỗi món 1.000đ
    assert row.tenure == 30 and row.purchase_gap == 30
    assert row.viewed_products == 2  # lượt xem 100 ngày trước nằm ngoài 90 ngày


def test_evaluate_features_prints_table(rec_db, capsys):  # noqa: F811
    labels = assign_personas(24, 5)
    rng = random.Random(5)
    with rec_db.begin() as conn:
        for index, persona in enumerate(labels, start=1):
            account_id = create_account(conn, customer_email(index))
            orders = {"VIP": 6, "LOYAL": 4, "SAVER": 3}.get(persona, 1)
            days = 200 if persona in ("AT_RISK", "CHURNED") else 5
            if persona == "BROWSER":
                continue
            for i in range(orders):
                at = NOW - timedelta(days=days + 20 * i + rng.randint(0, 4))
                add_order(conn, account_id, [1 + i % 3], at=at)
    assert evaluate.main(["--features", "--seed", "5"], engine=rec_db) == 0
    out = capsys.readouterr().out
    assert "So các bộ đặc trưng trên" in out
    assert "| R, F, M |" in out and "| L, R, F, M |" in out


def test_evaluate_features_without_data(rec_db):  # noqa: F811
    assert evaluate.run_features(rec_db, 5, now=NOW).startswith("Chưa đủ dữ liệu để so")
