"""Test phân nhóm khách hàng cần CSDL: đọc R, F, M, ghi mô hình, gán nhóm, huấn luyện lại."""

from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import text

from app.main import nightly_job, task_handlers
from app.recommendation.cache import ModelCache
from app.recompute.queue import SEGMENT_ASSIGN
from app.recompute.worker import RecomputeWorker
from app.segmentation import evaluate, store
from app.segmentation.rfm import load_rfm
from app.simulation.personas import DEMO_ACCOUNTS, assign_personas, customer_email
from tests.db_helpers import create_account, request_recompute
from tests.test_segmentation_model import GROUPS, make_customers

NOW = datetime(2026, 10, 28, 9, 0, tzinfo=UTC).replace(tzinfo=None)


def _wipe(engine):
    with engine.begin() as conn:
        for table in (
            "recompute_request",
            "account_segment",
            "customer_segment",
            "segment_model",
            "orders",
            "account",
        ):
            conn.execute(text(f"DELETE FROM {table}"))


@pytest.fixture
def seg_db(db_engine):
    """CSDL test không có khách nào, xóa sạch sau test."""
    _wipe(db_engine)
    yield db_engine
    _wipe(db_engine)


def add_order(conn, account_id, total, at, status="DELIVERED"):
    conn.execute(
        text(
            "INSERT INTO orders (account_id, status, payment_method, idempotency_key, "
            "recipient_name_snapshot, phone_snapshot, address_detail_snapshot, discount_amount, "
            "subtotal_amount, shipping_fee, total_amount, created_at, updated_at) "
            "VALUES (:a, :s, 'COD', UUID(), 'Khách', '0900000000', 'Địa chỉ', 0, :t, 0, :t, "
            ":at, :at)"
        ),
        {"a": account_id, "s": status, "t": total, "at": at},
    )


def add_customer(conn, email, recency, frequency, monetary):
    """Khách có `frequency` đơn Đã giao, tổng `monetary`, đơn gần nhất cách NOW `recency` ngày."""
    account_id = create_account(conn, email)
    for i in range(frequency):
        add_order(conn, account_id, monetary // frequency, NOW - timedelta(days=recency + 3 * i))
    return account_id


def add_groups(conn, per_group=8):
    """Khách của 4 nhóm rõ rệt (như test không cần CSDL); trả về {mã tài khoản: kiểu khách}."""
    rows, kinds = make_customers(per_group)
    return {
        add_customer(
            conn, f"kh{row.account_id}@test.vn", row.recency, row.frequency, row.monetary
        ): kinds[row.account_id]
        for row in rows
    }


def segment_of(engine, account_id):
    with engine.connect() as conn:
        return conn.execute(
            text(
                "SELECT s.code FROM account_segment a JOIN customer_segment s "
                "ON s.id = a.segment_id WHERE a.account_id = :id"
            ),
            {"id": account_id},
        ).scalar()


def test_load_rfm_counts_only_delivered_orders_of_customers(seg_db):
    with seg_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        add_order(conn, a, 200_000, NOW - timedelta(days=30))
        add_order(conn, a, 300_000, NOW - timedelta(days=5, hours=2))
        add_order(conn, a, 900_000, NOW - timedelta(days=1), status="CANCELLED")
        add_order(conn, a, 900_000, NOW - timedelta(days=1), status="SHIPPING")
        b = create_account(conn, "b@test.vn")
        add_order(conn, b, 500_000, NOW - timedelta(days=1), status="PENDING")
        admin = create_account(conn, "admin@test.vn")
        conn.execute(text("UPDATE account SET role = 'ADMIN' WHERE id = :id"), {"id": admin})
        add_order(conn, admin, 100_000, NOW)
        rows = load_rfm(conn, NOW)
        assert [(r.account_id, r.recency, r.frequency, r.monetary) for r in rows] == [
            (a, 5, 2, 500_000)
        ]
        assert load_rfm(conn, NOW, [b]) == []
        assert load_rfm(conn, NOW, []) == []


def test_retrain_saves_model_segments_and_assignments(seg_db):
    with seg_db.begin() as conn:
        truth = add_groups(conn)
        assert store.retrain(conn, NOW) is not None
    with seg_db.connect() as conn:
        model, segment_ids, trained_at = store.load_model(conn)
        assert trained_at == NOW
        assert len(model.segments) == len(segment_ids) == 4
        stored = conn.execute(
            text("SELECT cluster_count, customer_count, silhouette FROM segment_model")
        ).one()
        assert stored.cluster_count == 4 and stored.customer_count == 32
        assert stored.silhouette >= 0.3
        assert conn.execute(text("SELECT COUNT(*) FROM account_segment")).scalar() == 32
    for account_id, code in truth.items():
        assert segment_of(seg_db, account_id) == code


def test_retrain_without_enough_customers_keeps_nothing(seg_db):
    with seg_db.begin() as conn:
        add_customer(conn, "a@test.vn", 5, 2, 400_000)
        assert store.retrain(conn, NOW) is None
        assert store.load_model(conn) is None


def test_assign_uses_current_model_without_retraining(seg_db):
    with seg_db.begin() as conn:
        add_groups(conn)
        store.retrain(conn, NOW)
        newcomer = add_customer(conn, "moi@test.vn", 2, 1, 250_000)
        before = conn.execute(text("SELECT COUNT(*) FROM segment_model")).scalar()
        store.assign_account(conn, newcomer, NOW)
        assert conn.execute(text("SELECT COUNT(*) FROM segment_model")).scalar() == before
        row = conn.execute(
            text(
                "SELECT rfm_recency, rfm_frequency, rfm_monetary FROM account_segment "
                "WHERE account_id = :id"
            ),
            {"id": newcomer},
        ).one()
        assert tuple(row) == (2, 1, 250_000)
    assert segment_of(seg_db, newcomer) == "NEW"


def test_assign_without_model_trains_first_and_without_orders_removes_row(seg_db):
    with seg_db.begin() as conn:
        truth = add_groups(conn)
        vip = next(a for a, c in truth.items() if c == "VIP")
        store.assign_account(conn, vip, NOW)
        assert store.load_model(conn) is not None
        assert conn.execute(text("SELECT COUNT(*) FROM account_segment")).scalar() == 32
        # BR-24: không còn đơn Đã giao (vd dữ liệu bị sửa tay) thì không còn dòng.
        conn.execute(
            text("UPDATE orders SET status = 'CANCELLED' WHERE account_id = :id"), {"id": vip}
        )
        store.assign_account(conn, vip, NOW)
    assert segment_of(seg_db, vip) is None


def test_retrain_if_due_once_a_week(seg_db):
    with seg_db.begin() as conn:
        add_groups(conn)
        assert store.retrain_if_due(conn, NOW) is True
        assert store.retrain_if_due(conn, NOW + timedelta(days=6)) is False
        assert store.retrain_if_due(conn, NOW + timedelta(days=7) - timedelta(minutes=5)) is True
        assert conn.execute(text("SELECT COUNT(*) FROM segment_model")).scalar() == 2
        assert conn.execute(text("SELECT COUNT(*) FROM customer_segment")).scalar() == 4


def test_worker_handles_segment_assign_and_nightly_job_trains(seg_db):
    with seg_db.begin() as conn:
        truth = add_groups(conn)
        lost = next(a for a, c in truth.items() if c == "LOST")
        request_recompute(conn, lost, SEGMENT_ASSIGN)
    worker = RecomputeWorker(seg_db, task_handlers(ModelCache()))
    try:
        assert worker.tick() == 1
    finally:
        worker.stop()
    assert segment_of(seg_db, lost) == "LOST"
    # Việc mỗi đêm: mô hình vừa huấn luyện chưa đủ 1 tuần nên không huấn luyện lại.
    with seg_db.begin() as conn:
        nightly_job(ModelCache())(conn)
        assert conn.execute(text("SELECT COUNT(*) FROM segment_model")).scalar() == 1


def test_nightly_job_survives_segmentation_error(seg_db, monkeypatch):
    def broken(conn, now):
        raise RuntimeError("hỏng")

    monkeypatch.setattr(store, "retrain_if_due", broken)
    with seg_db.begin() as conn:
        a = add_customer(conn, "a@test.vn", 5, 1, 100_000)
        nightly_job(ModelCache())(conn)
        assert conn.execute(text("SELECT account_id FROM recompute_request")).scalars().all() == [a]


def test_evaluate_compares_with_simulated_personas(seg_db, capsys):
    labels = assign_personas(30, 7)
    with seg_db.begin() as conn:
        for index, persona in enumerate(labels, start=1):
            r, f, m = {
                "VIP": GROUPS["VIP"],
                "LOYAL": GROUPS["LOYAL"],
                "SAVER": GROUPS["LOYAL"],
                "NEW": GROUPS["NEW"],
            }.get(persona, GROUPS["LOST"])
            account_id = create_account(conn, customer_email(index))
            if persona != "BROWSER":
                for i in range(f):
                    add_order(conn, account_id, m // f, NOW - timedelta(days=r + index % 5 + i))
        demo = create_account(conn, DEMO_ACCOUNTS[0].email)
        add_order(conn, demo, 9_000_000, NOW - timedelta(days=3))
    assert evaluate.main(["--seed", "7"], engine=seg_db) == 0
    out = capsys.readouterr().out
    assert "Điểm gãy (Elbow)" in out and "ARI = " in out and "| Khách VIP |" in out
    with seg_db.connect() as conn:
        assert store.load_model(conn) is None  # không --save thì không ghi
        assert evaluate.true_personas(conn, 7)[demo] == DEMO_ACCOUNTS[0].persona
    assert "Đã ghi mô hình" in evaluate.run(seg_db, 7, save=True, now=NOW)
    with seg_db.connect() as conn:
        assert store.load_model(conn) is not None


def test_evaluate_without_data(seg_db):
    assert evaluate.run(seg_db, 7, save=False, now=NOW).startswith("Chưa đủ dữ liệu")
