from collections import Counter
from datetime import UTC, datetime, timedelta

import bcrypt
import pytest
from sqlalchemy import text

from app.simulation import writer
from app.simulation.__main__ import main, parse_args
from app.simulation.catalog import Product, Variant, build_catalog, category_gender, variant_label
from app.simulation.generator import FREE_SHIPPING_THRESHOLD, SHIPPING_FEE, Generator
from app.simulation.personas import (
    DEMO_ACCOUNTS,
    PERSONAS,
    assign_personas,
    random_phone,
)
from tests.db_helpers import create_account

NOW = datetime(2026, 10, 26, 12, tzinfo=UTC).replace(tzinfo=None)
DEMO_PASSWORD = "demo-test-123"

# Danh mục nhỏ cho test: tên trùng danh mục mẫu để khớp cây khảo sát (V5).
CATEGORIES = {
    1: "Giày thể thao nam",
    2: "Giày thể thao nữ",
    3: "Vớ nam",
    4: "Vớ nữ",
    5: "Balo laptop",
    6: "Vali",
    7: "Túi xách nữ",
    8: "Kính mát",
}
OCCASIONS = {
    "SCHOOL": ["Balo laptop", "Giày thể thao nam", "Giày thể thao nữ", "Vớ nam", "Vớ nữ"],
    "SPORT": ["Giày thể thao nam", "Giày thể thao nữ", "Vớ nam", "Vớ nữ"],
    "TRAVEL": ["Vali", "Kính mát"],
    "WORK": ["Túi xách nữ"],
    "OUTING": ["Kính mát"],
}


def make_products() -> list[Product]:
    products = []
    variant_id = 1
    for cid in CATEGORIES:
        for k in range(6):
            pid = cid * 100 + k
            variants = []
            for size in ("39", "40") if k % 2 == 0 else (None,):
                stock = 0 if (k == 5 and size in ("40", None)) else 10
                variants.append(Variant(variant_id, 50_000 * (k + 1) * cid, stock, size))
                variant_id += 1
            products.append(Product(pid, f"Sản phẩm {pid}", cid, 1 + k % 3, variants))
    return products


@pytest.fixture
def catalog():
    return build_catalog(dict(CATEGORIES), make_products(), seed=7)


@pytest.fixture
def customers(catalog):
    return Generator(catalog, OCCASIONS, NOW, seed=7).generate(60)


# ---------------------------------------------------------------------------------------------
# Hàm nhỏ, kiểu khách
# ---------------------------------------------------------------------------------------------


def test_category_gender_and_variant_label():
    assert category_gender("Giày thể thao nam") == "MALE"
    assert category_gender("Sandal nữ") == "FEMALE"
    # Tên tách dấu (NFD) vẫn nhận ra là đồ nữ.
    assert category_gender("Sandal nu\u031b\u0303") == "FEMALE"
    assert category_gender("Vali") is None
    assert variant_label(None, None) is None
    assert variant_label("Đen", None) == "Đen"
    assert variant_label("Đen", "42") == "Đen – 42"


def test_personas_shares_and_assignment():
    assert sum(p.share for p in PERSONAS.values()) == pytest.approx(1.0)
    labels = assign_personas(300, seed=1)
    assert len(labels) == 300
    assert labels == assign_personas(300, seed=1)
    assert labels != assign_personas(300, seed=2)
    counts = Counter(labels)
    for code, persona in PERSONAS.items():
        assert abs(counts[code] - persona.share * 300) < 1
    assert assign_personas(0, seed=1) == []
    assert len(assign_personas(7, seed=1)) == 7


def test_random_phone_matches_backend_pattern():
    import random
    import re

    rng = random.Random(1)
    assert all(re.fullmatch(r"0\d{9,10}", random_phone(rng)) for _ in range(50))


def test_demo_accounts_have_unique_emails():
    emails = [d.email for d in DEMO_ACCOUNTS]
    assert len(set(emails)) == len(emails)
    assert all(e.endswith("@mophong.test") for e in emails)


# ---------------------------------------------------------------------------------------------
# Sinh dữ liệu trong bộ nhớ
# ---------------------------------------------------------------------------------------------


def test_generation_is_deterministic(catalog):
    def snapshot(seed):
        result = Generator(catalog, OCCASIONS, NOW, seed=seed).generate(20)
        return [(c.email, c.persona, len(c.orders), len(c.views)) for c in result]

    first, again, other = snapshot(3), snapshot(3), snapshot(4)
    assert first == again
    assert first != other


def test_empty_catalog_is_rejected():
    with pytest.raises(ValueError, match="Chưa có sản phẩm"):
        Generator(build_catalog({}, [], 1), OCCASIONS, NOW, seed=1)


def test_activity_happens_after_sign_up_and_before_now(customers):
    for c in customers:
        times = [t for _, t in c.views] + [t for _, t in c.cart_events]
        times += [o.created_at for o in c.orders] + [o.updated_at for o in c.orders]
        times += [t for _, t in c.wishlist] + [t for *_, t in c.cart]
        times += [r.created_at for o in c.orders for r in o.reviews]
        assert all(c.created_at <= t <= NOW for t in times), c.email
        assert c.created_at >= NOW - timedelta(days=401)


def test_orders_follow_shop_rules(customers):
    orders = [o for c in customers for o in c.orders]
    assert orders
    for order in orders:
        expected_fee = 0 if order.subtotal >= FREE_SHIPPING_THRESHOLD else SHIPPING_FEE
        assert order.shipping_fee == expected_fee
        assert order.items and len({i.product.id for i in order.items}) == len(order.items)
        age = NOW - order.created_at
        if age > timedelta(days=4):
            assert order.status in ("DELIVERED", "CANCELLED")
        else:
            assert order.status in ("PENDING", "PROCESSING", "SHIPPING")
        if order.status == "CANCELLED":
            assert order.payment_method == "COD"
        if order.payment_method == "ONLINE":
            assert order.paid_at is not None and order.transaction_no
        else:
            assert order.paid_at is None
        if order.reviews:
            assert order.status == "DELIVERED"
    assert len({o.idempotency_key for o in orders}) == len(orders)


def test_each_product_is_reviewed_once_and_cart_is_in_stock(customers):
    for c in customers:
        reviewed = [o.items[r.item_index].product.id for o in c.orders for r in o.reviews]
        assert len(reviewed) == len(set(reviewed))
        assert all(v.stock > 0 for v, _, _ in c.cart)
        assert len({pid for pid, _ in c.wishlist}) == len(c.wishlist)
        assert all(1 <= r.rating <= 5 for o in c.orders for r in o.reviews)


def test_customers_buy_from_categories_of_their_gender(customers, catalog):
    for c in customers:
        for order in c.orders:
            for item in order.items:
                gender = category_gender(catalog.categories[item.product.category_id])
                assert gender in (None, c.gender) or c.gender == "OTHER"


def test_personas_differ_in_recency_frequency_and_money(customers):
    def stats(code):
        group = [c for c in customers if c.persona == code and not c.demo]
        orders = [len(c.orders) for c in group]
        money = [sum(o.subtotal for o in c.orders) for c in group]
        return sum(orders) / len(group), sum(money) / len(group)

    assert stats("VIP")[0] > stats("LOYAL")[0] > stats("CHURNED")[0]
    assert stats("VIP")[1] > stats("SAVER")[1]
    for c in customers:
        if c.persona == "CHURNED" and c.orders:
            assert NOW - c.orders[-1].created_at >= timedelta(days=239)
        if c.persona == "BROWSER":
            assert not c.orders and c.views


def test_demo_accounts_match_their_story(customers):
    demo = {c.email.split("@")[0]: c for c in customers if c.demo}
    assert set(demo) == {d.local_part for d in DEMO_ACCOUNTS}
    assert len(demo["demo.vip"].orders) >= 10
    assert demo["demo.chixem"].views and demo["demo.chixem"].cart and not demo["demo.chixem"].orders
    for name in ("demo.moi", "demo.khaosat"):
        assert not demo[name].views and not demo[name].orders and not demo[name].cart
        assert NOW - demo[name].created_at < timedelta(days=2)
    # Không có hàm đi khảo sát (không CSDL) thì "đã làm" thành "bỏ qua".
    assert demo["demo.vip"].survey.status == "SKIPPED"
    assert demo["demo.moi"].survey is None


# ---------------------------------------------------------------------------------------------
# Ghi vào CSDL
# ---------------------------------------------------------------------------------------------


def _wipe(engine):
    with engine.begin() as conn:
        writer.reset(conn)
        conn.execute(text("DELETE FROM recompute_request"))
        conn.execute(text("DELETE FROM account"))
        for table in ("product_variant", "product", "brand", "category"):
            conn.execute(text(f"DELETE FROM {table}"))


@pytest.fixture
def sim_db(db_engine, monkeypatch):
    """CSDL test có danh mục nhỏ (cùng tên danh mục với dữ liệu mẫu), xóa sạch sau test."""
    monkeypatch.setenv("SIM_DEMO_PASSWORD", DEMO_PASSWORD)
    _wipe(db_engine)
    with db_engine.begin() as conn:
        for cid, name in CATEGORIES.items():
            conn.execute(
                text("INSERT INTO category (id, name) VALUES (:id, :name)"),
                {"id": cid, "name": name},
            )
        for bid in (1, 2, 3):
            conn.execute(
                text("INSERT INTO brand (id, name) VALUES (:id, :name)"),
                {"id": bid, "name": f"Hiệu {bid}"},
            )
        for p in make_products():
            conn.execute(
                text(
                    "INSERT INTO product (id, category_id, brand_id, name, variation_name1, "
                    "created_at, updated_at) VALUES (:id, :c, :b, :n, :v1, NOW(), NOW())"
                ),
                {
                    "id": p.id,
                    "c": p.category_id,
                    "b": p.brand_id,
                    "n": p.name,
                    "v1": "Size" if p.variants[0].label else None,
                },
            )
            for v in p.variants:
                conn.execute(
                    text(
                        "INSERT INTO product_variant (id, product_id, option1_value, price, stock, "
                        "created_at, updated_at) VALUES (:id, :p, :o, :price, :stock, NOW(), NOW())"
                    ),
                    {"id": v.id, "p": p.id, "o": v.label, "price": v.price, "stock": v.stock},
                )
        # Sản phẩm đã xóa mềm: không được xuất hiện trong dữ liệu mô phỏng.
        conn.execute(
            text(
                "INSERT INTO product (id, category_id, name, deleted_at, created_at, updated_at) "
                "VALUES (9999, 1, 'Đã xóa', NOW(), NOW(), NOW())"
            )
        )
        conn.execute(
            text(
                "INSERT INTO product_variant (product_id, price, stock, created_at, updated_at) "
                "VALUES (9999, 1000, 5, NOW(), NOW())"
            )
        )
    yield db_engine
    _wipe(db_engine)


def counts(engine):
    with engine.connect() as conn:
        return writer.summary(conn)


def test_main_writes_data_and_rerun_replaces_it(sim_db, capsys):
    with sim_db.begin() as conn:
        real = create_account(conn, "that@test.vn")

    assert main(["--customers", "40", "--seed", "5"], engine=sim_db) == 0
    first = counts(sim_db)
    assert first["account"] == 40 + len(DEMO_ACCOUNTS)
    for table in ("orders", "product_view_log", "product_event_log", "review", "survey_result"):
        assert first[table] > 0, table
    out = capsys.readouterr().out
    assert "mật khẩu SIM_DEMO_PASSWORD" in out and "demo.vip@mophong.test" in out

    assert main(["--customers", "40", "--seed", "5"], engine=sim_db) == 0
    assert counts(sim_db)["account"] == first["account"]
    assert "Đã xóa 46 tài khoản" in capsys.readouterr().out

    assert main(["--reset-only"], engine=sim_db) == 0
    assert all(n == 0 for n in counts(sim_db).values())
    with sim_db.connect() as conn:
        assert conn.execute(text("SELECT id FROM account")).scalars().all() == [real]


def test_written_rows_are_consistent(sim_db):
    assert main(["--customers", "40", "--seed", "9"], engine=sim_db) == 0
    with sim_db.connect() as conn:

        def q(sql):
            return conn.execute(text(sql)).all()

        # Tổng tiền đơn = tổng dòng + phí ship; không dùng sản phẩm đã xóa.
        assert not q(
            "SELECT o.id FROM orders o JOIN order_item i ON i.order_id = o.id GROUP BY o.id "
            "HAVING SUM(i.line_total) <> MAX(o.subtotal_amount) "
            "OR MAX(o.total_amount) <> MAX(o.subtotal_amount) + MAX(o.shipping_fee)"
        )
        assert not q("SELECT id FROM product_view_log WHERE product_id = 9999")
        # Đơn ONLINE có mã tham chiếu bắt đầu bằng mã đơn; đơn COD không có trạng thái thanh toán.
        assert not q(
            "SELECT id FROM orders WHERE payment_method = 'ONLINE' "
            "AND (payment_status <> 'PAID' OR payment_txn_ref NOT LIKE CONCAT(id, '%'))"
        )
        assert not q(
            "SELECT id FROM orders WHERE payment_method = 'COD' AND payment_status IS NOT NULL"
        )
        # Đánh giá trỏ đúng dòng đơn đã giao của chính khách đó.
        assert not q(
            "SELECT r.id FROM review r JOIN order_item i ON i.id = r.order_item_id "
            "JOIN orders o ON o.id = i.order_id JOIN product_variant v ON v.id = i.variant_id "
            "WHERE o.account_id <> r.account_id OR v.product_id <> r.product_id "
            "OR o.status <> 'DELIVERED'"
        )
        # Yêu cầu tính lại: gợi ý cho mọi khách, phân nhóm cho khách có đơn đã giao.
        tasks = dict(q("SELECT task_type, COUNT(*) FROM recompute_request GROUP BY task_type"))
        assert tasks["RECOMMENDATION"] == 40 + len(DEMO_ACCOUNTS)
        delivered = q("SELECT COUNT(DISTINCT account_id) FROM orders WHERE status = 'DELIVERED'")
        assert tasks["SEGMENT_ASSIGN"] == delivered[0][0]


def test_survey_results_follow_the_question_tree(sim_db):
    assert main(["--customers", "40", "--seed", "11"], engine=sim_db) == 0
    with sim_db.connect() as conn:
        results = conn.execute(
            text(
                "SELECT a.gender, s.style, s.answers, a.id FROM survey_result s "
                "JOIN account a ON a.id = s.account_id WHERE s.status = 'COMPLETED'"
            )
        ).all()
        assert results
        for gender, style, answers, account_id in results:
            import json

            path = [a["questionCode"] for a in json.loads(answers)]
            assert path[:3] == ["Q1", "Q2", "Q3"]
            assert path[-1] in ("P1", "S2")
            categories = conn.execute(
                text(
                    "SELECT c.name FROM user_preference u JOIN category c ON c.id = u.category_id "
                    "WHERE u.account_id = :a"
                ),
                {"a": account_id},
            ).scalars()
            other = {"MALE": "FEMALE", "FEMALE": "MALE"}.get(gender)
            assert all(category_gender(name) != other for name in categories)
            assert style in ("SAVER", "BRAND", "QUALITY", "TREND")


def test_demo_password_is_a_spring_bcrypt_hash(sim_db):
    assert main(["--customers", "3"], engine=sim_db) == 0
    with sim_db.connect() as conn:
        hashes = dict(conn.execute(text("SELECT email, password_hash FROM account")).all())
    demo = hashes["demo.vip@mophong.test"]
    assert demo.startswith("$2b$12$")
    assert bcrypt.checkpw(DEMO_PASSWORD.encode(), demo.encode())
    # Khách thường không đăng nhập được bằng mật khẩu demo.
    assert not bcrypt.checkpw(DEMO_PASSWORD.encode(), hashes["kh0001@mophong.test"].encode())


def test_without_demo_password_demo_accounts_cannot_log_in(sim_db, monkeypatch, capsys):
    monkeypatch.delenv("SIM_DEMO_PASSWORD")
    assert main(["--customers", "2"], engine=sim_db) == 0
    assert "chưa đặt SIM_DEMO_PASSWORD" in capsys.readouterr().out
    assert main(["--customers", "2", "--no-demo"], engine=sim_db) == 0
    assert counts(sim_db)["account"] == 2


def test_reset_covers_every_table_that_references_account(db_engine):
    with db_engine.connect() as conn:
        referencing = conn.execute(
            text(
                "SELECT DISTINCT table_name FROM information_schema.key_column_usage "
                "WHERE table_schema = DATABASE() AND referenced_table_name IN ('account', 'orders')"
            )
        ).scalars()
        assert set(referencing) <= set(writer.RESET_TABLES)


def test_main_reports_errors(sim_db, empty_db_engine, monkeypatch, capsys):
    monkeypatch.setenv("SIM_DEMO_PASSWORD", "short")
    assert main([], engine=sim_db) == 2
    assert "ít nhất 8 ký tự" in capsys.readouterr().err

    monkeypatch.delenv("SIM_DEMO_PASSWORD")
    assert main([], engine=empty_db_engine) == 1
    assert "Lỗi CSDL" in capsys.readouterr().err

    with sim_db.begin() as conn:
        conn.execute(text("DELETE FROM product_variant"))
        conn.execute(text("DELETE FROM product"))
    assert main([], engine=sim_db) == 2
    assert "Chưa có sản phẩm" in capsys.readouterr().err


def test_parse_args_rejects_negative_customers():
    assert parse_args([]).customers == 300
    with pytest.raises(SystemExit):
        parse_args(["--customers", "-1"])
