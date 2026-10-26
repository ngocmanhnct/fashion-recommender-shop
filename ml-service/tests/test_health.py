from fastapi.testclient import TestClient

from app.main import app
from app.recompute.queue import RECOMMENDATION
from tests.db_helpers import TEST_DB, create_account, request_recompute


def test_health_returns_up():
    response = TestClient(app).get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_health_does_not_need_database(monkeypatch):
    # MySQL không chạy (cổng 1 luôn từ chối): /health vẫn UP, trạng thái hàng đợi báo lỗi.
    monkeypatch.setenv("DB_HOST", "127.0.0.1")
    monkeypatch.setenv("DB_PORT", "1")
    monkeypatch.setenv("RECOMPUTE_WORKER_ENABLED", "false")
    with TestClient(app) as client:
        assert client.get("/health").json() == {"status": "UP"}
        status = client.get("/recompute/status").json()
    assert status["running"] is False
    assert status["pending"] is None
    assert status["pendingError"].startswith("OperationalError")


def test_recompute_status_counts_pending_requests(clean_db, monkeypatch):
    with clean_db.begin() as conn:
        a = create_account(conn, "a@test.vn")
        request_recompute(conn, a, RECOMMENDATION)
    monkeypatch.setenv("DB_NAME", TEST_DB)
    monkeypatch.setenv("RECOMPUTE_WORKER_ENABLED", "false")
    with TestClient(app) as client:
        status = client.get("/recompute/status").json()
    assert status["pending"] == {RECOMMENDATION: {"total": 1, "due": 1}}
    assert status["taskTypes"] == []
