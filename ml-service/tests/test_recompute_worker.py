import time
from datetime import datetime

import pytest
from sqlalchemy import text

from app.recompute.queue import RECOMMENDATION, SEGMENT_ASSIGN
from app.recompute.worker import VIETNAM, RecomputeWorker
from tests.db_helpers import create_account, request_recompute


def rows(engine):
    with engine.connect() as conn:
        return conn.execute(
            text(
                "SELECT account_id, task_type, version, attempts, last_error "
                "FROM recompute_request ORDER BY account_id, task_type"
            )
        ).all()


@pytest.fixture
def make_worker():
    workers = []

    def make(engine, handlers, **kwargs):
        kwargs.setdefault("poll_interval_seconds", 0.05)
        worker = RecomputeWorker(engine, handlers, **kwargs)
        workers.append(worker)
        return worker

    yield make
    for worker in workers:
        worker.stop()


def test_processes_due_requests_and_leaves_other_task_types(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
        request_recompute(conn, a, SEGMENT_ASSIGN)
    seen = []
    worker = make_worker(clean_db, {RECOMMENDATION: lambda conn, req: seen.append(req.account_id)})

    assert worker.tick() == 1
    assert seen == [a]
    assert [(r.task_type, r.version) for r in rows(clean_db)] == [(SEGMENT_ASSIGN, 1)]
    assert worker.processed == 1 and worker.failed == 0
    assert worker.tick() == 0


def test_handler_writes_commit_together_with_delete(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)

    def handler(conn, req):
        conn.execute(
            text("UPDATE account SET full_name = 'Đã tính' WHERE id = :id"), {"id": req.account_id}
        )

    make_worker(clean_db, {RECOMMENDATION: handler}).tick()
    with clean_db.connect() as conn:
        name = conn.execute(text("SELECT full_name FROM account WHERE id = :id"), {"id": a})
        assert name.scalar() == "Đã tính"
    assert rows(clean_db) == []


def test_failed_handler_rolls_back_and_schedules_retry(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)

    def handler(conn, req):
        conn.execute(text("UPDATE account SET full_name = 'Dở dang' WHERE id = :id"), {"id": a})
        raise ValueError("thiếu dữ liệu")

    worker = make_worker(clean_db, {RECOMMENDATION: handler})
    assert worker.tick() == 1
    ((_, _, version, attempts, error),) = rows(clean_db)
    assert (version, attempts) == (1, 1)
    assert error == "ValueError: thiếu dữ liệu"
    assert worker.failed == 1
    with clean_db.connect() as conn:
        name = conn.execute(text("SELECT full_name FROM account WHERE id = :id"), {"id": a})
        assert name.scalar() == "Khách test"
    assert worker.tick() == 0  # chưa đến hạn thử lại (10 giây)


def test_request_arriving_during_computation_is_computed_once_more(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
    calls = []

    def handler(conn, req):
        calls.append(req.version)
        if len(calls) == 1:
            with clean_db.begin() as other:  # khách thêm giỏ trong lúc đang tính
                request_recompute(other, a, RECOMMENDATION)

    worker = make_worker(clean_db, {RECOMMENDATION: handler})
    worker.tick()
    assert [(r.version, r.attempts) for r in rows(clean_db)] == [(2, 0)]
    worker.tick()
    worker.tick()
    assert calls == [1, 2]
    assert rows(clean_db) == []


def test_only_one_worker_holds_the_lock(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
    first = make_worker(clean_db, {RECOMMENDATION: lambda conn, req: None})
    second = make_worker(clean_db, {RECOMMENDATION: lambda conn, req: None}, lock_retry_seconds=0)

    assert first.tick() == 1 and first.holds_lock
    with clean_db.begin() as conn:
        request_recompute(conn, a, RECOMMENDATION)
    assert second.tick() == 0
    assert not second.holds_lock
    assert "khác đang lấy việc" in second.last_error

    first.stop()
    assert second.tick() == 1 and second.holds_lock
    assert second.last_error is None


def test_lock_is_per_database(clean_db, make_worker):
    # Khóa của MySQL tính trên cả máy chủ: ml-service của CSDL khác không được chặn worker này.
    other = clean_db.connect()
    other.execute(text("SELECT GET_LOCK('recompute_worker:fashionshop', 0)"))
    worker = make_worker(clean_db, {})
    try:
        worker.tick()
        assert worker.holds_lock
        assert worker.lock_name == f"recompute_worker:{clean_db.url.database}"
    finally:
        other.close()


def test_survives_missing_table_and_reports_it(empty_db_engine, make_worker):
    worker = make_worker(empty_db_engine, {RECOMMENDATION: lambda conn, req: None})
    assert worker.tick() == 0
    assert worker.tick() == 0
    assert "chưa có bảng recompute_request" in worker.last_error
    assert not worker.holds_lock


def test_background_thread_picks_up_new_requests(clean_db, make_worker):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
    done = []
    worker = make_worker(clean_db, {RECOMMENDATION: lambda conn, req: done.append(req.account_id)})
    worker.start()
    worker.start()  # gọi 2 lần không tạo 2 luồng
    with clean_db.begin() as conn:
        request_recompute(conn, a, RECOMMENDATION)

    deadline = time.monotonic() + 5
    while not done and time.monotonic() < deadline:
        time.sleep(0.02)
    status = worker.status()
    worker.stop()

    assert done == [a]
    assert status["running"] is True and status["holdsLock"] is True
    assert status["taskTypes"] == [RECOMMENDATION]
    assert status["processed"] == 1 and status["lastPollAt"].endswith("Z")
    assert worker.status()["running"] is False and not worker.holds_lock


# ---- việc mỗi đêm ----


def vn(day, hour, minute=0):
    return datetime(2026, 10, day, hour, minute, tzinfo=VIETNAM)


def test_maintenance_runs_at_start_then_after_each_2am(clean_db, make_worker):
    calls = []
    now = [vn(26, 10)]
    worker = make_worker(
        clean_db, {}, maintenance=lambda conn: calls.append(now[0]), clock=lambda: now[0]
    )
    assert worker.maintenance_due(now[0])
    assert worker.tick() == 0
    assert calls == [vn(26, 10)]
    assert worker.status()["lastMaintenanceAt"] == "2026-10-26T10:00:00+07:00"

    for moment, expected in (
        (vn(26, 23), False),
        (vn(27, 1, 59), False),
        (vn(27, 2), True),
    ):
        assert worker.maintenance_due(moment) is expected
    now[0] = vn(27, 2, 1)
    assert worker.run_maintenance_if_due()
    assert not worker.run_maintenance_if_due()
    assert len(calls) == 2


def test_maintenance_error_waits_for_next_night(clean_db, make_worker, caplog):
    def broken(conn):
        raise RuntimeError("hỏng")

    worker = make_worker(clean_db, {}, maintenance=broken, clock=lambda: vn(26, 10))
    assert worker.run_maintenance_if_due() is False
    assert "Lỗi khi chạy việc định kỳ" in caplog.text
    assert not worker.maintenance_due(vn(26, 11))


def test_maintenance_database_error_is_retried(clean_db, make_worker):
    def broken(conn):
        conn.execute(text("SELECT * FROM bang_khong_co"))

    worker = make_worker(clean_db, {}, maintenance=broken, clock=lambda: vn(26, 10))
    assert worker.tick() == 0
    assert worker.last_error is not None
    assert worker.last_maintenance_at is None


def test_no_maintenance_by_default(clean_db, make_worker):
    worker = make_worker(clean_db, {})
    assert not worker.maintenance_due(vn(26, 10))
    assert worker.run_maintenance_if_due() is False
