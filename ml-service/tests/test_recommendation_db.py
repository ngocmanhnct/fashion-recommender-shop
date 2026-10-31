"""Test gợi ý cần CSDL: đọc dữ liệu, ghi bảng recommendation, yêu cầu hàng loạt, API."""

from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.main import app, nightly_job, task_handlers
from app.recommendation import evaluate
from app.recommendation.cache import ModelCache
from app.recommendation.data import load_signals
from app.recommendation.store import enqueue_all, make_handler, replace_recommendations
from app.recompute.queue import RECOMMENDATION, RecomputeRequest
from app.recompute.worker import RecomputeWorker
from app.simulation import writer
from tests.db_helpers import TEST_DB, create_account, request_recompute
from tests.test_recommendation_model import BRANDS, ITEMS, SHOES, SOCKS

NOW = datetime(2026, 10, 26, 10, 0, tzinfo=UTC).replace(tzinfo=None)

CATEGORY_NAMES = {1: "Giày thể thao nam", 2: "Túi xách nữ", 3: "Vớ nam"}


def _wipe(engine):
    with engine.begin() as conn:
        for table in writer.RESET_TABLES:
            conn.execute(text(f"DELETE FROM {table}"))
        for table in ("product_variant", "product", "brand", "category"):
            conn.execute(text(f"DELETE FROM {table}"))


@pytest.fixture
def rec_db(db_engine):
    """CSDL test có 8 sản phẩm của test mô hình (sản phẩm 8 hết hàng), xóa sạch sau test."""
    _wipe(db_engine)
    with db_engine.begin() as conn:
        for cid, name in CATEGORY_NAMES.items():
            conn.execute(
                text("INSERT INTO category (id, name) VALUES (:i, :n)"), {"i": cid, "n": name}
            )
        for bid, name in BRANDS.items():
            conn.execute(
                text("INSERT INTO brand (id, name) VALUES (:i, :n)"), {"i": bid, "n": name}
            )
        for item in ITEMS:
            conn.execute(
                text(
                    "INSERT INTO product (id, category_id, brand_id, name, description, "
                    "created_at, updated_at) VALUES (:id, :c, :b, :n, :d, NOW(), NOW())"
                ),
                {
                    "id": item.id,
                    "c": item.category_id,
                    "b": item.brand_id,
                    "n": item.name,
                    "d": item.description,
                },
            )
            conn.execute(
                text(
                    "INSERT INTO product_variant (id, product_id, price, stock, created_at, "
                    "updated_at) VALUES (:id, :id, :price, :stock, NOW(), NOW())"
                ),
                {"id": item.id, "price": item.price, "stock": 0 if item.id == 8 else 10},
            )
    yield db_engine
    _wipe(db_engine)


def add_order(conn, account_id, product_ids, status="DELIVERED", at=NOW - timedelta(days=10)):
    order_id = conn.execute(
        text(
            "INSERT INTO orders (account_id, status, payment_method, idempotency_key, "
            "recipient_name_snapshot, phone_snapshot, address_detail_snapshot, discount_amount, "
            "subtotal_amount, shipping_fee, total_amount, created_at, updated_at) "
            "VALUES (:a, :s, 'COD', UUID(), 'Khách', '0900000000', 'Địa chỉ', 0, 100000, 0, "
            "100000, :at, :at)"
        ),
        {"a": account_id, "s": status, "at": at},
    ).lastrowid
    for pid in product_ids:
        conn.execute(
            text(
                "INSERT INTO order_item (order_id, variant_id, product_name_snapshot, "
                "unit_price_snapshot, quantity, line_total) VALUES (:o, :v, 'x', 1000, 1, 1000)"
            ),
            {"o": order_id, "v": pid},
        )
    return order_id


def add_views(conn, account_id, product_ids, at=NOW - timedelta(days=1)):
    for pid in product_ids:
        conn.execute(
            text(
                "INSERT INTO product_view_log (account_id, product_id, viewed_at) "
                "VALUES (:a, :p, :at)"
            ),
            {"a": account_id, "p": pid, "at": at},
        )


def add_survey(conn, account_id, category_ids, style, brand_id=None):
    conn.execute(
        text(
            "INSERT INTO survey_result (account_id, status, style, created_at, updated_at) "
            "VALUES (:a, 'COMPLETED', :s, NOW(), NOW())"
        ),
        {"a": account_id, "s": style},
    )
    for cid in category_ids:
        conn.execute(
            text(
                "INSERT INTO user_preference (account_id, category_id, brand_id, price_min, "
                "price_max, created_at, updated_at) VALUES (:a, :c, :b, 0, NULL, NOW(), NOW())"
            ),
            {"a": account_id, "c": cid, "b": brand_id},
        )


def stored(engine, account_id):
    with engine.connect() as conn:
        return conn.execute(
            text(
                "SELECT product_id, rank_position, reason, generated_at FROM recommendation "
                "WHERE account_id = :a ORDER BY rank_position"
            ),
            {"a": account_id},
        ).all()


def handle(engine, account_id, cache=None):
    handler = make_handler(cache or ModelCache(), clock=lambda: NOW)
    with engine.begin() as conn:
        handler(conn, RecomputeRequest(account_id, RECOMMENDATION, 1, 0, NOW))


# ---- đọc dữ liệu ----


def test_load_signals_reads_each_kind_of_interaction(rec_db):
    with rec_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        add_order(conn, a, [1])
        add_order(conn, a, [3], status="CANCELLED")
        add_order(conn, a, [5], status="PENDING")
        add_views(conn, a, [2, 4])
        add_views(conn, a, [7], at=NOW - timedelta(days=31))
        conn.execute(
            text(
                "INSERT INTO cart_item (account_id, variant_id, quantity, updated_at) "
                "VALUES (:a, 6, 1, NOW())"
            ),
            {"a": a},
        )
        conn.execute(
            text(
                "INSERT INTO wishlist_item (account_id, product_id, created_at) "
                "VALUES (:a, 2, NOW())"
            ),
            {"a": a},
        )
        add_survey(conn, a, [SHOES, SOCKS], "BRAND", brand_id=10)
        signals = load_signals(conn, a, NOW)
    assert signals.purchased == {1, 5}
    assert signals.delivered_orders == 1
    assert signals.viewed == {2, 4}
    assert signals.cart == {6} and signals.wishlist == {2}
    # Thời điểm tương tác gần nhất của mọi sản phẩm còn tính (không có đơn đã hủy, lượt xem cũ).
    assert set(signals.last_at) == {1, 2, 4, 5, 6}
    assert signals.now == NOW
    assert signals.survey.category_ids == (SHOES, SOCKS)
    assert signals.survey.brand_id == 10 and signals.survey.style == "BRAND"


def test_load_signals_only_for_customers(rec_db):
    with rec_db.begin() as conn:
        admin = create_account(conn, "admin@test.vn")
        conn.execute(text("UPDATE account SET role = 'ADMIN' WHERE id = :a"), {"a": admin})
        skipped = create_account(conn, "b@test.vn")
        conn.execute(
            text(
                "INSERT INTO survey_result (account_id, status, created_at, updated_at) "
                "VALUES (:a, 'SKIPPED', NOW(), NOW())"
            ),
            {"a": skipped},
        )
        assert load_signals(conn, admin, NOW) is None
        assert load_signals(conn, skipped, NOW).survey is None


# ---- ghi bảng recommendation ----


def test_handler_overwrites_list_by_behaviour_survey_or_nothing(rec_db):
    with rec_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        for i in range(3):
            other = create_account(conn, f"k{i}@test.vn")
            add_order(conn, other, [1, 6])
        add_order(conn, a, [1])

    handle(rec_db, a)
    rows = stored(rec_db, a)
    ids = [r.product_id for r in rows]
    assert ids and 1 not in ids and 8 not in ids
    assert [r.rank_position for r in rows] == list(range(1, len(rows) + 1))
    assert rows[0].generated_at == NOW
    assert any(r.reason.endswith("cũng hay mua sản phẩm này") for r in rows)

    # Đơn bị hủy: chưa đủ dữ liệu, chưa làm khảo sát → danh sách bị xóa (trang chủ hiện bán chạy).
    with rec_db.begin() as conn:
        conn.execute(text("UPDATE orders SET status = 'CANCELLED' WHERE account_id = :a"), {"a": a})
    handle(rec_db, a)
    assert stored(rec_db, a) == []

    with rec_db.begin() as conn:
        add_survey(conn, a, [SOCKS], "TREND")
    handle(rec_db, a)
    assert [r.product_id for r in stored(rec_db, a)][:2] == [6, 7]


def test_handler_for_admin_or_empty_catalog_writes_nothing(rec_db):
    with rec_db.begin() as conn:
        admin = create_account(conn, "admin@test.vn")
        conn.execute(text("UPDATE account SET role = 'ADMIN' WHERE id = :a"), {"a": admin})
        a = create_account(conn, "a@test.vn")
        add_survey(conn, a, [SHOES], "SAVER")
        replace_recommendations(conn, a, [], NOW)
    handle(rec_db, admin)
    assert stored(rec_db, admin) == []

    with rec_db.begin() as conn:
        conn.execute(text("DELETE FROM product_variant"))
        conn.execute(text("DELETE FROM product"))
    cache = ModelCache()
    with rec_db.connect() as conn:
        assert cache.get(conn) is None
    handle(rec_db, a, cache)
    assert stored(rec_db, a) == []


def test_worker_computes_requests_end_to_end(rec_db):
    with rec_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        add_views(conn, a, [1, 2, 3, 4, 5])
        request_recompute(conn, a, RECOMMENDATION)
    worker = RecomputeWorker(rec_db, task_handlers(ModelCache()))
    try:
        assert worker.poll_once() == 1
    finally:
        worker.stop()
    assert len(stored(rec_db, a)) == 7  # chưa mua gì: mọi sản phẩm còn hàng (trừ sản phẩm 8)
    with rec_db.connect() as conn:
        assert conn.execute(text("SELECT COUNT(*) FROM recompute_request")).scalar() == 0


def test_model_cache_rebuilds_when_data_changes(rec_db):
    cache = ModelCache(check_interval_seconds=0)
    with rec_db.connect() as conn:
        first = cache.get(conn)
        assert cache.get(conn) is first
    with rec_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        add_order(conn, a, [2])
    with rec_db.connect() as conn:
        second = cache.get(conn)
    assert second is not first and second.vectors is first.vectors
    assert second.popularity[second.vectors.index[2]] == 1.0

    with rec_db.begin() as conn:
        conn.execute(
            text(
                "UPDATE product_variant SET price = 900000, "
                "updated_at = NOW() + INTERVAL 1 MINUTE WHERE id = 5"
            )
        )
    with rec_db.connect() as conn:
        third = cache.get(conn)
    assert third.vectors is not second.vectors
    cache.invalidate()
    with rec_db.connect() as conn:
        assert cache.get(conn) is not third

    slow = ModelCache(check_interval_seconds=60)
    with rec_db.connect() as conn:
        model = slow.get(conn)
        conn.execute(text("UPDATE product SET name = 'Đổi tên' WHERE id = 1"))
        assert slow.get(conn) is model


# ---- yêu cầu tính lại hàng loạt ----


def test_enqueue_all_requests_customers_with_data_and_merges(rec_db):
    with rec_db.begin() as conn:
        buyer = create_account(conn, "buyer@test.vn")
        add_order(conn, buyer, [1])
        request_recompute(conn, buyer, RECOMMENDATION, delay_ms=300_000)
        viewer = create_account(conn, "viewer@test.vn")
        add_views(conn, viewer, [2])
        old_viewer = create_account(conn, "old@test.vn")
        add_views(conn, old_viewer, [2], at=NOW - timedelta(days=40))
        surveyed = create_account(conn, "survey@test.vn")
        add_survey(conn, surveyed, [SHOES], "SAVER")
        listed = create_account(conn, "listed@test.vn")
        conn.execute(
            text(
                "INSERT INTO recommendation (account_id, product_id, rank_position, score, "
                "reason, generated_at) VALUES (:a, 1, 1, 0.5, 'x', NOW())"
            ),
            {"a": listed},
        )
        create_account(conn, "nothing@test.vn")
        admin = create_account(conn, "admin@test.vn")
        conn.execute(text("UPDATE account SET role = 'ADMIN' WHERE id = :a"), {"a": admin})
        add_views(conn, admin, [3])

    with rec_db.begin() as conn:
        assert enqueue_all(conn, NOW - timedelta(days=30)) > 0
    with rec_db.connect() as conn:
        rows = conn.execute(
            text(
                "SELECT account_id, version, due_at <= UTC_TIMESTAMP(3) AS due "
                "FROM recompute_request ORDER BY account_id"
            )
        ).all()
    assert {r.account_id for r in rows} == {buyer, viewer, surveyed, listed}
    merged = next(r for r in rows if r.account_id == buyer)
    assert merged.version == 2 and merged.due == 1


def test_nightly_job_rebuilds_model_and_enqueues(rec_db):
    with rec_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        add_order(conn, a, [1])
    cache = ModelCache(check_interval_seconds=60)
    with rec_db.connect() as conn:
        model = cache.get(conn)
    with rec_db.begin() as conn:
        nightly_job(cache)(conn)
        assert cache.get(conn) is not model
        assert conn.execute(text("SELECT account_id FROM recompute_request")).scalars().all() == [a]


# ---- API ----


@pytest.fixture
def client(rec_db, monkeypatch):
    monkeypatch.setenv("DB_NAME", TEST_DB)
    monkeypatch.setenv("RECOMPUTE_WORKER_ENABLED", "false")
    with TestClient(app) as test_client:
        yield test_client


def test_related_api(client):
    response = client.get("/recommendations/related/1")
    assert response.status_code == 200
    body = response.json()
    assert len(body) == 4 and set(body[0]) == {"productId", "score", "reason", "source"}
    assert 1 not in [s["productId"] for s in body]
    assert len(client.get("/recommendations/related/1?limit=2").json()) == 2
    assert client.get("/recommendations/related/999").status_code == 404
    assert client.get("/recommendations/related/1?limit=0").status_code == 422


def test_survey_api(client):
    body = {"categoryIds": [SOCKS], "style": "TREND", "excludeProductIds": [6], "limit": 3}
    response = client.post("/recommendations/survey", json=body)
    assert response.status_code == 200
    ids = [s["productId"] for s in response.json()]
    assert ids[0] == 7 and 6 not in ids and len(ids) == 3
    assert response.json()[0]["source"] == "SURVEY"
    bad = client.post(
        "/recommendations/survey", json={"categoryIds": [1], "priceMin": 5, "priceMax": 1}
    )
    assert bad.status_code == 422
    assert client.post("/recommendations/survey", json={"categoryIds": []}).status_code == 422
    assert (
        client.post("/recommendations/survey", json={"categoryIds": [1], "style": "X"}).status_code
        == 422
    )


def test_api_without_products_is_unavailable(client, rec_db):
    with rec_db.begin() as conn:
        conn.execute(text("DELETE FROM product_variant"))
        conn.execute(text("DELETE FROM product"))
    assert client.get("/recommendations/related/1").status_code == 503


# ---- đánh giá ngoại tuyến ----


def test_evaluate_prints_scores(rec_db, capsys):
    with rec_db.begin() as conn:
        for i in range(4):
            a = create_account(conn, f"k{i}@test.vn")
            add_order(conn, a, [1], at=NOW - timedelta(days=20))
            add_order(conn, a, [6], at=NOW - timedelta(days=5))
        b = create_account(conn, "b@test.vn")
        add_survey(conn, b, [SOCKS], "TREND")
        add_order(conn, b, [7])
    assert evaluate.main(["--grid"], engine=rec_db) == 0
    out = capsys.readouterr().out
    assert "Khách có hành vi: 4 ca; khởi động lạnh: 1 ca" in out
    assert "| Hybrid (mặc định) | 4 | 1.000 |" in out
    assert "| Nội dung | Mua cùng |" in out
    assert "| Hybrid, không ưu tiên gần đây | 4 |" in out
    assert "| β | Bán rã (ngày) |" in out


def test_split_and_scores():
    first = evaluate.Order(1, 10, NOW - timedelta(days=9), "DELIVERED", {1: 1})
    last = evaluate.Order(2, 10, NOW, "DELIVERED", {1: 1, 2: 2})
    single = evaluate.Order(3, 11, NOW, "DELIVERED", {3: 1})
    dataset = evaluate.split([first, last, single], {10: [(5, NOW - timedelta(days=1))]}, {})
    (case,) = dataset.behaviour
    assert case.targets == {2} and case.signals.purchased == {1}
    assert case.signals.viewed == {5}
    assert case.signals.now == NOW
    assert case.signals.last_at == {1: NOW - timedelta(days=9), 5: NOW - timedelta(days=1)}
    assert dataset.train_sold == {1: 1, 3: 1}
    assert dataset.cold_start == []

    scores = evaluate.score_lists([case], lambda c: [7, 2], catalog_size=4, k=2)
    assert scores.hit_rate == 1 and scores.recall == 1 and scores.precision == 0.5
    assert scores.ndcg == pytest.approx(1 / 1.585, rel=1e-3)
    assert scores.coverage == 0.5
