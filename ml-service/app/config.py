"""Cấu hình ml-service, đọc từ biến môi trường (cùng tên biến với backend)."""

import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool) -> bool:
    """Đọc biến môi trường kiểu đúng/sai: true/1/yes/on là bật, false/0/no/off là tắt."""
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        return default
    return value.strip().lower() in {"true", "1", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    """Các giá trị cấu hình. Mặc định khớp lúc chạy trên máy dev (MySQL ở localhost:3306)."""

    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = "fashionshop"
    db_user: str = "root"
    db_password: str = "changeme_local_dev"
    worker_enabled: bool = True
    poll_interval_seconds: float = 2.0
    batch_size: int = 50

    @classmethod
    def from_env(cls) -> "Settings":
        """Đọc cấu hình từ biến môi trường; biến nào trống thì dùng giá trị mặc định."""
        defaults = cls()
        return cls(
            db_host=os.environ.get("DB_HOST") or defaults.db_host,
            db_port=int(os.environ.get("DB_PORT") or defaults.db_port),
            db_name=os.environ.get("DB_NAME") or defaults.db_name,
            db_user=os.environ.get("DB_USERNAME") or defaults.db_user,
            db_password=os.environ.get("MYSQL_ROOT_PASSWORD") or defaults.db_password,
            worker_enabled=_env_bool("RECOMPUTE_WORKER_ENABLED", defaults.worker_enabled),
            poll_interval_seconds=float(
                os.environ.get("RECOMPUTE_POLL_SECONDS") or defaults.poll_interval_seconds
            ),
            batch_size=int(os.environ.get("RECOMPUTE_BATCH_SIZE") or defaults.batch_size),
        )
