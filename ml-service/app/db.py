"""Kết nối MySQL dùng chung cho ml-service (SQLAlchemy Core + PyMySQL)."""

from sqlalchemy import Engine, create_engine
from sqlalchemy.engine import URL

from app.config import Settings


def create_db_engine(settings: Settings, database: str | None = None) -> Engine:
    """Tạo engine tới CSDL của shop (hoặc CSDL `database` nếu truyền vào, dùng cho test).

    Schema do Flyway của backend quản lý: ml-service chỉ đọc/ghi dữ liệu, không bao giờ tạo
    hay sửa bảng. `pool_pre_ping` bỏ các kết nối MySQL đã đóng (vd sau khi MySQL khởi động lại).
    """
    url = URL.create(
        "mysql+pymysql",
        username=settings.db_user,
        password=settings.db_password,
        host=settings.db_host,
        port=settings.db_port,
        database=database or settings.db_name,
        query={"charset": "utf8mb4"},
    )
    return create_engine(url, pool_pre_ping=True, pool_recycle=3600)
