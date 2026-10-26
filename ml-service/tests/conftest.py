"""Fixture dùng chung: CSDL MySQL riêng cho test của ml-service.

Test tạo lại CSDL `fashionshop_mltest` mỗi lần chạy và chạy đúng các file migration Flyway của
backend, nên schema giống hệt CSDL thật mà không đụng dữ liệu máy dev. Kết nối theo cùng biến môi
trường với backend (DB_HOST, MYSQL_ROOT_PASSWORD...). Không có MySQL thì các test cần CSDL được bỏ
qua; CI đặt ML_TEST_REQUIRE_DB=1 để thiếu MySQL là báo lỗi thay vì bỏ qua.
"""

import os
import re
from collections.abc import Iterator
from pathlib import Path

import pymysql
import pytest
from sqlalchemy import Engine, text

from app.config import Settings
from app.db import create_db_engine
from tests.db_helpers import EMPTY_DB, TEST_DB

MIGRATIONS = Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration"


def _migration_files() -> list[Path]:
    """Các file V<n>__*.sql theo đúng thứ tự Flyway chạy (số phiên bản tăng dần)."""
    files = MIGRATIONS.glob("V*__*.sql")
    return sorted(files, key=lambda f: int(re.match(r"V(\d+)__", f.name).group(1)))


def _admin_connection(settings: Settings) -> pymysql.Connection:
    """Kết nối quản trị (chưa chọn CSDL), cho phép chạy nhiều câu lệnh trong 1 lần gửi."""
    return pymysql.connect(
        host=settings.db_host,
        port=settings.db_port,
        user=settings.db_user,
        password=settings.db_password,
        charset="utf8mb4",
        client_flag=pymysql.constants.CLIENT.MULTI_STATEMENTS,
        connect_timeout=5,
    )


def _for_test_db(sql: str) -> str:
    """Đổi `ALTER DATABASE fashionshop` (V1) sang CSDL test.

    Flyway của backend luôn chạy trên CSDL tên fashionshop nên V1 ghi thẳng tên đó. Chạy nguyên
    văn ở đây thì sửa CSDL dev trên máy, còn ở CI (không có CSDL fashionshop) thì báo lỗi 3503.
    """
    return re.sub(r"ALTER DATABASE\s+fashionshop\b", f"ALTER DATABASE {TEST_DB}", sql)


def _run_script(cursor: pymysql.cursors.Cursor, sql: str) -> None:
    """Chạy cả file SQL; đọc hết kết quả để lỗi ở câu lệnh sau cũng được báo."""
    cursor.execute(_for_test_db(sql))
    while cursor.nextset():
        pass


@pytest.fixture(scope="session")
def settings() -> Settings:
    """Cấu hình đọc từ biến môi trường như lúc ml-service chạy thật."""
    return Settings.from_env()


@pytest.fixture(scope="session")
def db_engine(settings: Settings) -> Iterator[Engine]:
    """Engine tới CSDL test đã chạy đủ migration của backend."""
    try:
        admin = _admin_connection(settings)
    except pymysql.err.OperationalError as exc:
        if os.environ.get("ML_TEST_REQUIRE_DB") == "1":
            pytest.fail(f"Không kết nối được MySQL: {exc}")
        pytest.skip(f"Bỏ qua test cần MySQL (không kết nối được: {exc})")
    with admin, admin.cursor() as cursor:
        for name in (TEST_DB, EMPTY_DB):
            cursor.execute(f"DROP DATABASE IF EXISTS {name}")
            cursor.execute(
                f"CREATE DATABASE {name} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
            )
        cursor.execute(f"USE {TEST_DB}")
        for migration in _migration_files():
            _run_script(cursor, migration.read_text(encoding="utf-8"))
    engine = create_db_engine(settings, database=TEST_DB)
    yield engine
    engine.dispose()


@pytest.fixture(scope="session")
def empty_db_engine(settings: Settings, db_engine: Engine) -> Iterator[Engine]:
    """Engine tới 1 CSDL chưa có bảng nào (giống lúc backend chưa chạy Flyway)."""
    engine = create_db_engine(settings, database=EMPTY_DB)
    yield engine
    engine.dispose()


@pytest.fixture
def clean_db(db_engine: Engine) -> Iterator[Engine]:
    """Xóa dữ liệu các bảng test dùng, trước và sau mỗi test."""

    def wipe() -> None:
        with db_engine.begin() as conn:
            conn.execute(text("DELETE FROM recompute_request"))
            conn.execute(text("DELETE FROM account"))

    wipe()
    yield db_engine
    wipe()
