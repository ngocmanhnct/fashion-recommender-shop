from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import text

from app.recompute.queue import (
    RECOMMENDATION,
    SEGMENT_ASSIGN,
    delete_if_unchanged,
    fetch_due,
    pending_counts,
    record_failure,
    retry_delay_seconds,
)
from tests.db_helpers import create_account, request_recompute


def utcnow():
    return datetime.now(UTC).replace(tzinfo=None)


@pytest.mark.parametrize(
    ("attempts", "seconds"), [(0, 10), (1, 10), (2, 20), (3, 40), (6, 320), (7, 600), (99, 600)]
)
def test_retry_delay_doubles_from_10_seconds_up_to_10_minutes(attempts, seconds):
    assert retry_delay_seconds(attempts) == seconds


def test_fetch_due_returns_only_due_requests_of_given_types_oldest_first(clean_db):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        b = create_account(conn, "b@test.vn")
        c = create_account(conn, "c@test.vn")
        request_recompute(conn, b, RECOMMENDATION, delay_ms=-2000)
        request_recompute(conn, a, RECOMMENDATION, delay_ms=-1000)
        request_recompute(conn, c, RECOMMENDATION, delay_ms=60_000)  # chưa đến hạn
        request_recompute(conn, a, SEGMENT_ASSIGN, delay_ms=-3000)  # loại việc không lấy
    with clean_db.begin() as conn:
        due = fetch_due(conn, [RECOMMENDATION], limit=10)
        assert [(r.account_id, r.task_type) for r in due] == [
            (b, RECOMMENDATION),
            (a, RECOMMENDATION),
        ]
        assert due[0].version == 1 and due[0].attempts == 0
        assert len(fetch_due(conn, [RECOMMENDATION, SEGMENT_ASSIGN], limit=2)) == 2
        assert fetch_due(conn, [], limit=10) == []


def test_delete_only_when_no_new_request_arrived(clean_db):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        b = create_account(conn, "b@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
        request_recompute(conn, b, RECOMMENDATION)
    with clean_db.begin() as conn:
        first, second = fetch_due(conn, [RECOMMENDATION], limit=10)
    with clean_db.begin() as conn:
        request_recompute(conn, second.account_id, RECOMMENDATION)  # khách lại thao tác
    with clean_db.begin() as conn:
        assert delete_if_unchanged(conn, first) is True
        assert delete_if_unchanged(conn, second) is False
    with clean_db.begin() as conn:
        (left,) = fetch_due(conn, [RECOMMENDATION], limit=10)
        assert (left.account_id, left.version) == (second.account_id, 2)


def test_record_failure_counts_attempt_and_postpones(clean_db):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
    with clean_db.begin() as conn:
        (request,) = fetch_due(conn, [RECOMMENDATION], limit=10)
    before = utcnow()
    with clean_db.begin() as conn:
        assert record_failure(conn, request, "ValueError: " + "x" * 2000) == 10
    with clean_db.begin() as conn:
        attempts, error, due_at = conn.execute(
            text("SELECT attempts, last_error, due_at FROM recompute_request")
        ).one()
        assert fetch_due(conn, [RECOMMENDATION], limit=10) == []
    assert attempts == 1
    assert len(error) == 1000 and error.startswith("ValueError")
    assert before + timedelta(seconds=9) <= due_at <= utcnow() + timedelta(seconds=11)


def test_record_failure_keeps_due_time_of_newer_request(clean_db):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
    with clean_db.begin() as conn:
        (request,) = fetch_due(conn, [RECOMMENDATION], limit=10)
        request_recompute(conn, a, RECOMMENDATION)
    with clean_db.begin() as conn:
        assert record_failure(conn, request, "boom") is None
        (still_due,) = fetch_due(conn, [RECOMMENDATION], limit=10)
    assert (still_due.version, still_due.attempts) == (2, 0)


def test_pending_counts_by_task_type(clean_db):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        b = create_account(conn, "b@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
        request_recompute(conn, b, RECOMMENDATION, delay_ms=300_000)
        request_recompute(conn, a, SEGMENT_ASSIGN)
    with clean_db.connect() as conn:
        assert pending_counts(conn) == {
            RECOMMENDATION: {"total": 2, "due": 1},
            SEGMENT_ASSIGN: {"total": 1, "due": 1},
        }
