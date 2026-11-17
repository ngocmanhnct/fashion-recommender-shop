"""Đọc dữ liệu cho mô hình gợi ý từ MySQL (chỉ đọc, trừ bảng `recommendation` ở `store.py`)."""

from dataclasses import dataclass, field
from datetime import date, datetime, timedelta

from sqlalchemy import Connection, text

from app.recommendation.features import CatalogItem
from app.recommendation.gender import product_gender

VIEW_WINDOW_DAYS = 30
"""Lượt xem trong 30 ngày gần nhất mới được tính (BR-19)."""

MIN_VIEWED_PRODUCTS = 5
"""Ngưỡng BR-19: xem ít nhất 5 sản phẩm khác nhau trong 30 ngày."""


@dataclass(frozen=True)
class ProductStats:
    """Thông tin thay đổi thường xuyên của 1 sản phẩm đang bán."""

    price: int
    in_stock: bool
    sold: int
    rating: float | None
    rating_count: int = 0


@dataclass(frozen=True)
class SurveyProfile:
    """Sở thích từ khảo sát (bảng `user_preference`, `survey_result`) hoặc từ backend gửi sang."""

    category_ids: tuple[int, ...]
    brand_id: int | None
    price_min: int
    price_max: int | None
    style: str | None
    min_rating: float | None = None
    gender: str | None = None
    """MALE / FEMALE: chỉ gợi ý đồ của giới tính này và đồ dùng chung; None = không lọc."""
    keywords: str | None = None
    """Từ khóa gu và dịp sử dụng đã chọn, cách nhau bằng dấu cách."""


@dataclass
class UserSignals:
    """Tương tác của 1 tài khoản, mỗi sản phẩm lấy loại tương tác mạnh nhất."""

    account_id: int
    purchased: set[int] = field(default_factory=set)
    cart: set[int] = field(default_factory=set)
    wishlist: set[int] = field(default_factory=set)
    viewed: set[int] = field(default_factory=set)
    delivered_orders: int = 0
    survey: SurveyProfile | None = None
    last_at: dict[int, datetime] = field(default_factory=dict)
    """Mã sản phẩm → lần tương tác gần nhất (mua, thêm giỏ, thích, xem), để ưu tiên việc vừa làm."""
    now: datetime | None = None
    """Thời điểm tính gợi ý, cùng giờ với `last_at`; None thì không ưu tiên theo thời gian."""
    purchased_at: dict[int, datetime] = field(default_factory=dict)
    """Mã sản phẩm đã mua → lần mua gần nhất (đặc trưng của mô hình xếp hạng)."""
    age: int | None = None
    """Tuổi (theo ngày sinh khách khai), None nếu không rõ."""

    def touch(self, product_id: int, at: datetime | None) -> None:
        """Ghi nhận 1 lần tương tác với sản phẩm, giữ thời điểm muộn nhất."""
        if at is not None and (product_id not in self.last_at or at > self.last_at[product_id]):
            self.last_at[product_id] = at

    def buy(self, product_id: int, at: datetime | None) -> None:
        """Ghi nhận 1 lần mua sản phẩm (đơn khác CANCELLED), giữ lần mua muộn nhất."""
        self.purchased.add(product_id)
        self.touch(product_id, at)
        if at is not None and (
            product_id not in self.purchased_at or at > self.purchased_at[product_id]
        ):
            self.purchased_at[product_id] = at

    @property
    def has_enough_behaviour(self) -> bool:
        """Ngưỡng BR-19: có ít nhất 1 đơn Đã giao, hoặc đã xem ít nhất 5 sản phẩm trong 30 ngày."""
        return self.delivered_orders >= 1 or len(self.viewed) >= MIN_VIEWED_PRODUCTS


@dataclass(slots=True)
class Order:
    """1 đơn khác CANCELLED. `slots` bớt khoảng 40 byte mỗi đơn: toàn bộ dữ liệu H&M có hơn 9 triệu
    đơn."""

    id: int
    account_id: int
    created_at: datetime
    status: str
    products: dict[int, int] = field(default_factory=dict)
    """Mã sản phẩm → số lượng."""


def load_orders(conn: Connection) -> list[Order]:
    """Mọi đơn khác CANCELLED kèm sản phẩm, theo thời gian đặt."""
    orders: dict[int, Order] = {}
    for oid, account_id, created_at, status, product_id, quantity in conn.execute(
        text(
            "SELECT o.id, o.account_id, o.created_at, o.status, v.product_id, oi.quantity "
            "FROM orders o "
            "JOIN order_item oi ON oi.order_id = o.id "
            "JOIN product_variant v ON v.id = oi.variant_id "
            "WHERE o.status <> 'CANCELLED' ORDER BY o.created_at, o.id"
        )
    ):
        order = orders.setdefault(oid, Order(oid, account_id, created_at, status))
        order.products[product_id] = order.products.get(product_id, 0) + quantity
    return list(orders.values())


def age_on(birthdate: date | None, today: date) -> int | None:
    """Số tuổi tròn vào ngày `today`; None nếu không có ngày sinh."""
    if birthdate is None:
        return None
    return (
        today.year - birthdate.year - ((today.month, today.day) < (birthdate.month, birthdate.day))
    )


def load_ages(conn: Connection, now: datetime) -> dict[int, int]:
    """Tuổi của các tài khoản Khách hàng có khai ngày sinh."""
    rows = conn.execute(
        text("SELECT id, birthdate FROM account WHERE role = 'CUSTOMER' AND birthdate IS NOT NULL")
    )
    ages = {int(account_id): age_on(birthdate, now.date()) for account_id, birthdate in rows}
    return {account_id: age for account_id, age in ages.items() if age is not None and age > 0}


def load_catalog(conn: Connection) -> list[CatalogItem]:
    """Mọi sản phẩm chưa xóa mềm, kèm giá thấp nhất của các biến thể."""
    rows = conn.execute(
        text(
            "SELECT p.id, p.category_id, p.brand_id, p.name, COALESCE(p.description, ''), "
            "       MIN(v.price), c.name "
            "FROM product p JOIN product_variant v ON v.product_id = p.id "
            "JOIN category c ON c.id = p.category_id "
            "WHERE p.deleted_at IS NULL "
            "GROUP BY p.id, p.category_id, p.brand_id, p.name, p.description, c.name "
            "ORDER BY p.id"
        )
    )
    return [
        CatalogItem(
            int(pid),
            int(cid),
            None if bid is None else int(bid),
            name,
            desc,
            int(price),
            product_gender(category, name),
        )
        for pid, cid, bid, name, desc, price, category in rows
    ]


def load_stats(conn: Connection) -> dict[int, ProductStats]:
    """Giá, còn hàng, số đã bán (đơn khác CANCELLED) và điểm đánh giá của sản phẩm đang bán."""
    sold = dict(
        conn.execute(
            text(
                "SELECT v.product_id, SUM(oi.quantity) "
                "FROM order_item oi "
                "JOIN orders o ON o.id = oi.order_id AND o.status <> 'CANCELLED' "
                "JOIN product_variant v ON v.id = oi.variant_id "
                "GROUP BY v.product_id"
            )
        ).all()
    )
    ratings = {
        pid: (avg, count)
        for pid, avg, count in conn.execute(
            text("SELECT product_id, AVG(rating), COUNT(*) FROM review GROUP BY product_id")
        )
    }
    rows = conn.execute(
        text(
            "SELECT p.id, MIN(v.price), MAX(v.stock) "
            "FROM product p JOIN product_variant v ON v.product_id = p.id "
            "WHERE p.deleted_at IS NULL GROUP BY p.id"
        )
    )
    return {
        int(pid): ProductStats(
            price=int(price),
            in_stock=int(stock) > 0,
            sold=int(sold.get(pid, 0)),
            rating=None if pid not in ratings else round(float(ratings[pid][0]), 1),
            rating_count=int(ratings[pid][1]) if pid in ratings else 0,
        )
        for pid, price, stock in rows
    }


def load_purchase_pairs(conn: Connection) -> list[tuple[int, int]]:
    """Các cặp (tài khoản, sản phẩm) đã mua, trên đơn khác CANCELLED: dữ liệu cho "hay mua cùng"."""
    rows = conn.execute(
        text(
            "SELECT DISTINCT o.account_id, v.product_id "
            "FROM orders o "
            "JOIN order_item oi ON oi.order_id = o.id "
            "JOIN product_variant v ON v.id = oi.variant_id "
            "WHERE o.status <> 'CANCELLED'"
        )
    )
    return [(int(a), int(p)) for a, p in rows]


def load_sales(conn: Connection) -> list[tuple[int, datetime, int, int]]:
    """Lượt bán trên đơn khác CANCELLED: (sản phẩm, lúc đặt, số lượng, tài khoản người mua), cho
    điểm "đang bán chạy" (tính tại thời điểm gợi ý, chỉ dùng TRENDING_WINDOW_DAYS ngày trước đó)
    và số liệu bán hàng của mô hình xếp hạng."""
    rows = conn.execute(
        text(
            "SELECT v.product_id, o.created_at, oi.quantity, o.account_id "
            "FROM orders o "
            "JOIN order_item oi ON oi.order_id = o.id "
            "JOIN product_variant v ON v.id = oi.variant_id "
            "WHERE o.status <> 'CANCELLED'"
        )
    )
    return [(int(p), at, int(q), int(a)) for p, at, q, a in rows]


def catalog_signature(conn: Connection) -> tuple:
    """Dấu hiệu danh mục sản phẩm đã đổi (thêm, sửa, xóa sản phẩm hay biến thể)."""
    return tuple(
        conn.execute(
            text(
                "SELECT (SELECT COUNT(*) FROM product), (SELECT MAX(updated_at) FROM product), "
                "       (SELECT MAX(deleted_at) FROM product), (SELECT COUNT(*) FROM product_variant)"
            )
        ).one()
    )


def activity_signature(conn: Connection) -> tuple:
    """Dấu hiệu tồn kho, giá, đơn hàng hay đánh giá đã đổi."""
    return tuple(
        conn.execute(
            text(
                "SELECT (SELECT MAX(updated_at) FROM product_variant), "
                "       (SELECT COUNT(*) FROM orders), (SELECT MAX(updated_at) FROM orders), "
                "       (SELECT COUNT(*) FROM review), (SELECT MAX(updated_at) FROM review)"
            )
        ).one()
    )


def load_signals(conn: Connection, account_id: int, now: datetime) -> UserSignals | None:
    """Tương tác của 1 tài khoản Khách hàng; None nếu không có tài khoản Khách hàng này.

    `now` là giờ địa phương không kèm múi giờ, cùng kiểu với `viewed_at` (giờ JVM của backend).
    """
    account = conn.execute(
        text("SELECT role, birthdate FROM account WHERE id = :id"), {"id": account_id}
    ).one_or_none()
    if account is None or account[0] != "CUSTOMER":
        return None
    params = {"id": account_id}
    age = age_on(account[1], now.date())
    signals = UserSignals(account_id, now=now, age=age if age is not None and age > 0 else None)
    for product_id, at in conn.execute(
        text(
            "SELECT v.product_id, MAX(o.created_at) "
            "FROM orders o "
            "JOIN order_item oi ON oi.order_id = o.id "
            "JOIN product_variant v ON v.id = oi.variant_id "
            "WHERE o.account_id = :id AND o.status <> 'CANCELLED' "
            "GROUP BY v.product_id"
        ),
        params,
    ):
        signals.buy(int(product_id), at)
    signals.delivered_orders = int(
        conn.execute(
            text("SELECT COUNT(*) FROM orders WHERE account_id = :id AND status = 'DELIVERED'"),
            params,
        ).scalar()
    )
    for product_id, at in conn.execute(
        text(
            "SELECT v.product_id, MAX(c.updated_at) FROM cart_item c "
            "JOIN product_variant v ON v.id = c.variant_id WHERE c.account_id = :id "
            "GROUP BY v.product_id"
        ),
        params,
    ):
        signals.cart.add(int(product_id))
        signals.touch(int(product_id), at)
    for product_id, at in conn.execute(
        text("SELECT product_id, created_at FROM wishlist_item WHERE account_id = :id"), params
    ):
        signals.wishlist.add(int(product_id))
        signals.touch(int(product_id), at)
    for product_id, at in conn.execute(
        text(
            "SELECT product_id, MAX(viewed_at) FROM product_view_log "
            "WHERE account_id = :id AND viewed_at >= :since GROUP BY product_id"
        ),
        {"id": account_id, "since": now - timedelta(days=VIEW_WINDOW_DAYS)},
    ):
        signals.viewed.add(int(product_id))
        signals.touch(int(product_id), at)
    signals.survey = load_survey(conn, account_id)
    return signals


def load_survey(conn: Connection, account_id: int) -> SurveyProfile | None:
    """Sở thích của lần làm khảo sát gần nhất; None nếu chưa làm hoặc đã bỏ qua."""
    result = conn.execute(
        text(
            "SELECT style, min_rating, gender, keywords FROM survey_result "
            "WHERE account_id = :id AND status = 'COMPLETED'"
        ),
        {"id": account_id},
    ).one_or_none()
    if result is None:
        return None
    rows = conn.execute(
        text(
            "SELECT category_id, brand_id, price_min, price_max FROM user_preference "
            "WHERE account_id = :id ORDER BY category_id"
        ),
        {"id": account_id},
    ).all()
    if not rows:
        return None
    _, brand_id, price_min, price_max = rows[0]
    style, min_rating, gender, keywords = result
    return SurveyProfile(
        category_ids=tuple(int(r[0]) for r in rows),
        brand_id=None if brand_id is None else int(brand_id),
        price_min=int(price_min),
        price_max=None if price_max is None else int(price_max),
        style=style,
        min_rating=None if min_rating is None else float(min_rating),
        gender=gender,
        keywords=keywords,
    )


def load_brand_names(conn: Connection) -> dict[int, str]:
    """Tên thương hiệu theo mã, để ghi lý do gợi ý."""
    return {int(bid): name for bid, name in conn.execute(text("SELECT id, name FROM brand"))}
