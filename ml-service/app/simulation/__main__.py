"""Chạy mô phỏng: `uv run python -m app.simulation` (trong ml-service/).

Xóa dữ liệu mô phỏng cũ rồi sinh lại, trong 1 giao dịch: lỗi giữa chừng thì CSDL giữ nguyên như
trước khi chạy. Mật khẩu của các tài khoản demo lấy từ biến môi trường SIM_DEMO_PASSWORD (không
ghi vào code hay git); không đặt thì tài khoản demo không đăng nhập được.
"""

import argparse
import os
import sys
from collections import Counter
from datetime import UTC, datetime

from sqlalchemy import Engine
from sqlalchemy.exc import SQLAlchemyError

from app.config import Settings
from app.db import create_db_engine
from app.simulation import writer
from app.simulation.catalog import load_catalog
from app.simulation.generator import Generator
from app.simulation.personas import DEMO_ACCOUNTS, PERSONAS, assign_personas
from app.simulation.survey import load_tree, make_runner, occasion_categories

DEFAULT_CUSTOMERS = 300
DEFAULT_SEED = 2026
MIN_DEMO_PASSWORD = 8


def local_now() -> datetime:
    """Giờ địa phương của máy chạy, không kèm múi giờ: backend cũng lưu LocalDateTime.now()."""
    return datetime.now(UTC).astimezone().replace(tzinfo=None)


def parse_args(argv: list[str] | None) -> argparse.Namespace:
    """Đọc tham số dòng lệnh."""
    parser = argparse.ArgumentParser(
        prog="python -m app.simulation",
        description="Sinh dữ liệu hành vi mô phỏng (tài khoản @mophong.test) vào CSDL của shop.",
    )
    parser.add_argument(
        "--customers", type=int, default=DEFAULT_CUSTOMERS, help="số khách thường (mặc định 300)"
    )
    parser.add_argument("--seed", type=int, default=DEFAULT_SEED, help="seed (mặc định 2026)")
    parser.add_argument(
        "--reset-only", action="store_true", help="chỉ xóa dữ liệu mô phỏng, không sinh lại"
    )
    parser.add_argument("--no-demo", action="store_true", help="không tạo tài khoản demo")
    args = parser.parse_args(argv)
    if args.customers < 0:
        parser.error("--customers phải >= 0")
    return args


def run(engine: Engine, args: argparse.Namespace, now: datetime | None = None) -> Counter:
    """Xóa dữ liệu mô phỏng cũ rồi (nếu không --reset-only) sinh và ghi lại. Trả về số dòng."""
    demo_password = os.environ.get("SIM_DEMO_PASSWORD") or None
    if demo_password is not None and len(demo_password) < MIN_DEMO_PASSWORD:
        raise ValueError(f"SIM_DEMO_PASSWORD cần ít nhất {MIN_DEMO_PASSWORD} ký tự")
    with engine.begin() as conn:
        removed = writer.reset(conn)
        print(f"Đã xóa {removed} tài khoản mô phỏng cũ.")
        if args.reset_only:
            return Counter()
        catalog = load_catalog(conn, args.seed)
        tree = load_tree(conn)
        generator = Generator(
            catalog,
            occasion_categories(tree),
            now or local_now(),
            args.seed,
            make_runner(conn, tree, catalog) if tree.by_code else None,
        )
        customers = generator.generate(args.customers, with_demo=not args.no_demo)
        return writer.write(conn, customers, demo_password)


def print_summary(args: argparse.Namespace, counts: Counter) -> None:
    """In số dòng đã ghi, số khách theo kiểu và danh sách tài khoản demo."""
    print("Đã ghi:", ", ".join(f"{table} {n}" for table, n in sorted(counts.items())))
    personas = Counter(assign_personas(args.customers, args.seed))
    print("Kiểu khách:", ", ".join(f"{code} {personas[code]}" for code in PERSONAS))
    if args.no_demo:
        return
    status = (
        "mật khẩu SIM_DEMO_PASSWORD"
        if os.environ.get("SIM_DEMO_PASSWORD")
        else "chưa đặt SIM_DEMO_PASSWORD nên không đăng nhập được"
    )
    print(f"Tài khoản demo ({status}):")
    for demo in DEMO_ACCOUNTS:
        print(f"  {demo.email:28} {demo.note}")


def main(argv: list[str] | None = None, engine: Engine | None = None) -> int:
    """Điểm vào dòng lệnh. Mã thoát: 0 thành công, 1 lỗi CSDL, 2 dữ liệu vào không hợp lệ."""
    args = parse_args(argv)
    settings = Settings.from_env()
    own_engine = engine is None
    engine = engine or create_db_engine(settings)
    print(f"CSDL: {settings.db_name} trên {settings.db_host}:{settings.db_port}")
    exit_code = 0
    try:
        counts = run(engine, args)
        if not args.reset_only:
            print_summary(args, counts)
    except ValueError as exc:
        print(f"Lỗi: {exc}", file=sys.stderr)
        exit_code = 2
    except SQLAlchemyError as exc:
        print(f"Lỗi CSDL (không ghi gì): {str(exc.orig or exc).splitlines()[0]}", file=sys.stderr)
        exit_code = 1
    finally:
        if own_engine:
            engine.dispose()
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
