"""Hàm tiện ích cho test cần CSDL: thêm tài khoản, ghi yêu cầu tính lại như backend."""

from sqlalchemy import Connection, text

TEST_DB = "fashionshop_mltest"
EMPTY_DB = "fashionshop_mltest_empty"


def create_account(conn: Connection, email: str) -> int:
    """Thêm 1 tài khoản khách tối thiểu (recompute_request có khóa ngoại tới account)."""
    result = conn.execute(
        text(
            "INSERT INTO account (email, password_hash, full_name, role, created_at, updated_at) "
            "VALUES (:email, 'x', 'Khách test', 'CUSTOMER', NOW(), NOW())"
        ),
        {"email": email},
    )
    return int(result.lastrowid)


def request_recompute(conn: Connection, account_id: int, task_type: str, delay_ms: int = 0) -> None:
    """Ghi yêu cầu bằng đúng câu lệnh của backend (RecomputeRequestService.UPSERT_SQL)."""
    conn.execute(
        text(
            """
            INSERT INTO recompute_request
                (account_id, task_type, due_at, version, attempts, created_at)
            VALUES (:account_id, :task_type,
                    TIMESTAMPADD(MICROSECOND, :delay_micros, UTC_TIMESTAMP(3)),
                    1, 0, UTC_TIMESTAMP(3))
            AS incoming
            ON DUPLICATE KEY UPDATE due_at = LEAST(recompute_request.due_at, incoming.due_at),
                                    version = recompute_request.version + 1
            """
        ),
        {"account_id": account_id, "task_type": task_type, "delay_micros": delay_ms * 1000},
    )
