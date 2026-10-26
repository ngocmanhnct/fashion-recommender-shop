"""Xóa và ghi dữ liệu mô phỏng vào CSDL. Chỉ đụng các tài khoản có email `@mophong.test`."""

import json
import secrets
from collections import Counter

import bcrypt
from sqlalchemy import Connection, text

from app.recompute.queue import RECOMMENDATION, SEGMENT_ASSIGN
from app.simulation.generator import SimCustomer
from app.simulation.personas import EMAIL_DOMAIN

SIM_ACCOUNTS = f"SELECT id FROM account WHERE email LIKE '%@{EMAIL_DOMAIN}'"
SIM_ORDERS = f"SELECT id FROM orders WHERE account_id IN ({SIM_ACCOUNTS})"

# Thứ tự xóa theo khóa ngoại: bảng con trước. Bảng nào mới có khóa ngoại tới account thì thêm ở
# đây (test kiểm tra không còn bảng nào trỏ tới account mà chưa có trong danh sách).
RESET_STATEMENTS = (
    f"DELETE FROM review WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM refund WHERE order_id IN ({SIM_ORDERS})",
    f"DELETE FROM order_item WHERE order_id IN ({SIM_ORDERS})",
    f"DELETE FROM orders WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM address WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM cart_item WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM wishlist_item WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM product_view_log WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM product_event_log WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM user_preference WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM survey_result WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM recommendation WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM account_segment WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM recompute_request WHERE account_id IN ({SIM_ACCOUNTS})",
    f"DELETE FROM account WHERE email LIKE '%@{EMAIL_DOMAIN}'",
)
RESET_TABLES = tuple(stmt.split()[2] for stmt in RESET_STATEMENTS)

BCRYPT_ROUNDS = 12  # Spring BCrypt đọc được mọi cost; 12 là mức tối thiểu SonarQube chấp nhận


def reset(conn: Connection) -> int:
    """Xóa mọi dữ liệu của tài khoản mô phỏng. Trả về số tài khoản đã xóa."""
    deleted = 0
    for statement in RESET_STATEMENTS:
        deleted = conn.execute(text(statement)).rowcount
    return deleted


def password_hash(password: str | None) -> str:
    """Mã băm BCrypt mà Spring Security đọc được; không có mật khẩu thì băm 1 chuỗi ngẫu nhiên
    không ai biết (tài khoản tồn tại nhưng không đăng nhập được)."""
    secret = password if password else secrets.token_urlsafe(32)
    return bcrypt.hashpw(secret.encode(), bcrypt.gensalt(BCRYPT_ROUNDS)).decode()


def write(
    conn: Connection, customers: list[SimCustomer], demo_password: str | None = None
) -> Counter:
    """Ghi khách và hoạt động của họ; trả về số dòng đã ghi theo bảng.

    Không trừ tồn kho (đơn mô phỏng không làm đổi danh mục, chạy lại nhiều lần vẫn như cũ). Cuối
    cùng ghi yêu cầu tính lại gợi ý (và phân nhóm, nếu có đơn đã giao) cho mọi khách, đến hạn
    ngay: loại việc nào ml-service chưa có hàm xử lý thì yêu cầu nằm chờ trong CSDL.
    """
    counts: Counter = Counter()
    hidden_hash = password_hash(None)
    demo_hash = password_hash(demo_password) if demo_password else hidden_hash
    for customer in customers:
        account_id = _insert_account(conn, customer, demo_hash if customer.demo else hidden_hash)
        counts["account"] += 1
        address_id = None
        if customer.orders or customer.cart:
            address_id = _insert(
                conn,
                "INSERT INTO address (account_id, recipient_name, phone, address_detail, "
                "is_default, created_at) VALUES (:a, :name, :phone, :detail, TRUE, :at)",
                a=account_id,
                name=customer.recipient_name,
                phone=customer.phone,
                detail=customer.address,
                at=customer.orders[0].created_at if customer.orders else customer.created_at,
            )
            counts["address"] += 1
        for order in customer.orders:
            counts += _insert_order(conn, account_id, address_id, customer, order)
        counts += _insert_logs(conn, account_id, customer)
        counts += _insert_survey(conn, account_id, customer)
        counts += _request_recompute(conn, account_id, customer)
    return counts


def _insert(conn: Connection, sql: str, **params) -> int:
    """Chạy 1 câu INSERT, trả về id vừa tạo."""
    return conn.execute(text(sql), params).lastrowid


def _insert_account(conn: Connection, customer: SimCustomer, hashed: str) -> int:
    return _insert(
        conn,
        "INSERT INTO account (email, password_hash, full_name, gender, birthdate, role, "
        "created_at, updated_at) VALUES (:email, :hash, :name, :gender, :birthdate, "
        "'CUSTOMER', :at, :at)",
        email=customer.email,
        hash=hashed,
        name=customer.full_name,
        gender=customer.gender,
        birthdate=customer.birthdate,
        at=customer.created_at,
    )


def _insert_order(
    conn: Connection, account_id: int, address_id: int | None, customer: SimCustomer, order
) -> Counter:
    subtotal = order.subtotal
    order_id = _insert(
        conn,
        "INSERT INTO orders (account_id, status, payment_method, payment_status, "
        "payment_transaction_no, paid_at, idempotency_key, address_id, recipient_name_snapshot, "
        "phone_snapshot, address_detail_snapshot, discount_amount, subtotal_amount, "
        "shipping_fee, total_amount, created_at, updated_at) VALUES (:a, :status, :method, "
        ":pay_status, :txn_no, :paid_at, :key, :address, :name, :phone, :detail, 0, :subtotal, "
        ":fee, :total, :created, :updated)",
        a=account_id,
        status=order.status,
        method=order.payment_method,
        pay_status="PAID" if order.payment_method == "ONLINE" else None,
        txn_no=order.transaction_no,
        paid_at=order.paid_at,
        key=order.idempotency_key,
        address=address_id,
        name=customer.recipient_name,
        phone=customer.phone,
        detail=customer.address,
        subtotal=subtotal,
        fee=order.shipping_fee,
        total=subtotal + order.shipping_fee,
        created=order.created_at,
        updated=order.updated_at,
    )
    if order.payment_method == "ONLINE":
        # Mã tham chiếu VNPay: mã đơn + thời điểm tạo link (PaymentService).
        conn.execute(
            text("UPDATE orders SET payment_txn_ref = :ref WHERE id = :id"),
            {"ref": f"{order_id}{order.created_at:%Y%m%d%H%M%S}", "id": order_id},
        )
    item_ids = [
        _insert(
            conn,
            "INSERT INTO order_item (order_id, variant_id, product_name_snapshot, "
            "variant_label_snapshot, unit_price_snapshot, quantity, line_total) "
            "VALUES (:o, :v, :name, :label, :price, :qty, :total)",
            o=order_id,
            v=item.variant.id,
            name=item.product.name,
            label=item.variant.label,
            price=item.variant.price,
            qty=item.quantity,
            total=item.line_total,
        )
        for item in order.items
    ]
    for review in order.reviews:
        item = order.items[review.item_index]
        conn.execute(
            text(
                "INSERT INTO review (account_id, product_id, order_item_id, rating, comment, "
                "created_at, updated_at) VALUES (:a, :p, :oi, :rating, :comment, :at, :at)"
            ),
            {
                "a": account_id,
                "p": item.product.id,
                "oi": item_ids[review.item_index],
                "rating": review.rating,
                "comment": review.comment,
                "at": review.created_at,
            },
        )
    return Counter(orders=1, order_item=len(item_ids), review=len(order.reviews))


def _insert_logs(conn: Connection, account_id: int, customer: SimCustomer) -> Counter:
    counts: Counter = Counter()
    batches = (
        (
            "product_view_log",
            "INSERT INTO product_view_log (account_id, product_id, viewed_at) VALUES (:a, :p, :at)",
            [{"a": account_id, "p": pid, "at": at} for pid, at in customer.views],
        ),
        (
            "product_event_log",
            (
                "INSERT INTO product_event_log (account_id, product_id, event_type, created_at) "
                "VALUES (:a, :p, 'ADD_TO_CART', :at)"
            ),
            [{"a": account_id, "p": pid, "at": at} for pid, at in customer.cart_events],
        ),
        (
            "cart_item",
            (
                "INSERT INTO cart_item (account_id, variant_id, quantity, updated_at) "
                "VALUES (:a, :v, :q, :at)"
            ),
            [{"a": account_id, "v": v.id, "q": q, "at": at} for v, q, at in customer.cart],
        ),
        (
            "wishlist_item",
            "INSERT INTO wishlist_item (account_id, product_id, created_at) VALUES (:a, :p, :at)",
            [{"a": account_id, "p": pid, "at": at} for pid, at in customer.wishlist],
        ),
    )
    for table, sql, rows in batches:
        if rows:
            conn.execute(text(sql), rows)
            counts[table] += len(rows)
    return counts


def _insert_survey(conn: Connection, account_id: int, customer: SimCustomer) -> Counter:
    survey = customer.survey
    if survey is None:
        return Counter()
    outcome = survey.outcome
    conn.execute(
        text(
            "INSERT INTO survey_result (account_id, status, style, brand_loyalty, min_rating, "
            "answers, created_at, updated_at) VALUES (:a, :status, :style, :loyalty, :rating, "
            ":answers, :at, :at)"
        ),
        {
            "a": account_id,
            "status": survey.status,
            "style": outcome.style if outcome else None,
            "loyalty": outcome.brand_loyalty if outcome else None,
            "rating": outcome.min_rating if outcome else None,
            "answers": json.dumps(outcome.answers, ensure_ascii=False) if outcome else None,
            "at": survey.at,
        },
    )
    rows = [
        {
            "a": account_id,
            "c": category_id,
            "b": outcome.brand_id,
            "min": outcome.price_min,
            "max": outcome.price_max,
            "at": survey.at,
        }
        for category_id in (outcome.category_ids if outcome else [])
    ]
    if rows:
        conn.execute(
            text(
                "INSERT INTO user_preference (account_id, category_id, brand_id, price_min, "
                "price_max, created_at, updated_at) VALUES (:a, :c, :b, :min, :max, :at, :at)"
            ),
            rows,
        )
    return Counter(survey_result=1, user_preference=len(rows))


def _request_recompute(conn: Connection, account_id: int, customer: SimCustomer) -> Counter:
    tasks = [RECOMMENDATION]
    if any(o.status == "DELIVERED" for o in customer.orders):
        tasks.append(SEGMENT_ASSIGN)
    conn.execute(
        text(
            "INSERT INTO recompute_request (account_id, task_type, due_at, created_at) "
            "VALUES (:a, :task, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"
        ),
        [{"a": account_id, "task": task} for task in tasks],
    )
    return Counter(recompute_request=len(tasks))


def summary(conn: Connection) -> dict[str, int]:
    """Số dòng của khách mô phỏng trong từng bảng (để in sau khi chạy và để test)."""
    result = {}
    for table in RESET_TABLES:
        if table in ("account", "refund", "order_item"):
            continue
        result[table] = conn.execute(
            text(f"SELECT COUNT(*) FROM {table} WHERE account_id IN ({SIM_ACCOUNTS})")
        ).scalar()
    result["order_item"] = conn.execute(
        text(f"SELECT COUNT(*) FROM order_item WHERE order_id IN ({SIM_ORDERS})")
    ).scalar()
    result["account"] = conn.execute(text(f"SELECT COUNT(*) FROM ({SIM_ACCOUNTS}) a")).scalar()
    return result
