"""Sinh hành vi của khách mô phỏng trong bộ nhớ (chưa ghi CSDL), tất định theo `seed`.

Mô hình đơn giản nhưng có cấu trúc để học máy học được:
- Mỗi khách có giới tính, 1-2 dịp sử dụng (đi học, đi làm, du lịch...) quyết định các danh mục
  quan tâm, một vị trí giá ưa thích và một phong cách mua sắm (tiết kiệm, thương hiệu, cầu toàn,
  xu hướng) giống câu hỏi khảo sát.
- Khả năng chọn 1 sản phẩm = độ "hot" chung x độ hợp giá x (chất lượng nếu cầu toàn) x (thương
  hiệu yêu thích nếu chuộng thương hiệu). Nhờ vậy có sản phẩm bán chạy, có khách giống nhau.
- Một đơn thường gồm món chính và món ở danh mục khác cùng dịp (giày thể thao + vớ), tạo dữ liệu
  "hay mua cùng". Trước khi mua, khách xem vài sản phẩm cùng danh mục và thêm vào giỏ.
- Kiểu khách (persona) quyết định số đơn, thời điểm mua, giá trị đơn, tức là R, F, M.
"""

import math
import random
import uuid
from collections.abc import Callable
from dataclasses import dataclass, field, replace
from datetime import date, datetime, timedelta

from app.simulation.catalog import Catalog, Product, Variant
from app.simulation.personas import (
    DEMO_ACCOUNTS,
    PERSONAS,
    DemoAccount,
    Persona,
    assign_personas,
    customer_email,
    random_address,
    random_name,
    random_phone,
)
from app.simulation.survey import SurveyOutcome

SHIPPING_FEE = 30_000  # OrderService.SHIPPING_FEE (BR-35)
FREE_SHIPPING_THRESHOLD = 500_000
DELIVERY_DAYS = 4  # đơn quá số ngày này đã giao xong hoặc đã hủy
CANCEL_RATE = 0.12
ONLINE_RATE = 0.3
SURVEY_COMPLETED_RATE = 0.6
SURVEY_SKIPPED_RATE = 0.15
WISHLIST_RATE = 0.25
OPEN_CART_RATE = 0.25
EXPLORE_RATE = 0.12  # tỉ lệ lần xem ở danh mục ngoài sở thích
HISTORY_DAYS = 400  # không có hoạt động nào xa hơn
RANDOM_SURVEY = "RANDOM"  # để Generator tự chọn trạng thái khảo sát theo tỉ lệ

# Giờ trong ngày khách hay mua sắm (tối nhiều nhất).
HOUR_WEIGHTS = [0.2, 0.1, 0.05, 0.05, 0.05, 0.1, 0.3, 0.6, 0.9, 1.0, 1.1, 1.3,
                1.5, 1.2, 1.0, 1.0, 1.1, 1.3, 1.6, 2.0, 2.4, 2.6, 2.0, 1.0]  # fmt: skip

OCCASION_WEIGHTS = {
    "young": {"SCHOOL": 3.0, "SPORT": 2.0, "OUTING": 3.0, "TRAVEL": 1.0, "WORK": 0.5},
    "adult": {"SCHOOL": 0.3, "SPORT": 1.5, "OUTING": 2.0, "TRAVEL": 2.0, "WORK": 3.0},
}

REVIEW_COMMENTS = {
    5: ("Sản phẩm rất đẹp, đúng mô tả, giao hàng nhanh.", "Chất lượng tuyệt vời, sẽ ủng hộ tiếp.",
        "Mua lần thứ hai rồi, rất hài lòng.", "Đóng gói cẩn thận, hàng y hình."),
    4: ("Hàng ổn so với giá tiền.", "Chất lượng tốt, giao hơi chậm một chút.",
        "Đẹp, nhưng màu hơi khác ảnh một chút.", "Dùng ổn, sẽ cân nhắc mua thêm."),
    3: ("Tạm được, không quá nổi bật.", "Chất lượng trung bình so với giá.",
        "Hàng dùng được, đường may chưa kỹ lắm."),
    2: ("Chất liệu không như mong đợi.", "Kích cỡ không chuẩn, phải đổi lại.",
        "Dùng vài lần đã có dấu hiệu hỏng."),
    1: ("Hàng kém chất lượng, không giống mô tả.", "Rất thất vọng, không muốn mua lại."),
}  # fmt: skip


@dataclass
class SimOrderItem:
    """Một dòng đơn."""

    product: Product
    variant: Variant
    quantity: int

    @property
    def line_total(self) -> int:
        """Thành tiền của dòng."""
        return self.variant.price * self.quantity


@dataclass
class SimReview:
    """Đánh giá cho dòng đơn thứ `item_index` của đơn."""

    item_index: int
    rating: int
    comment: str | None
    created_at: datetime


@dataclass
class SimOrder:
    """Một đơn hàng và các đánh giá sau khi nhận hàng."""

    created_at: datetime
    updated_at: datetime
    status: str
    payment_method: str
    paid_at: datetime | None
    transaction_no: str | None
    idempotency_key: str
    items: list[SimOrderItem]
    reviews: list[SimReview] = field(default_factory=list)

    @property
    def subtotal(self) -> int:
        """Tổng tiền hàng."""
        return sum(item.line_total for item in self.items)

    @property
    def shipping_fee(self) -> int:
        """Phí vận chuyển theo BR-35 (giống OrderService.shippingFeeFor)."""
        return 0 if self.subtotal >= FREE_SHIPPING_THRESHOLD else SHIPPING_FEE


@dataclass
class SimSurvey:
    """Kết quả khảo sát: COMPLETED kèm hồ sơ, hoặc SKIPPED."""

    status: str
    at: datetime
    outcome: SurveyOutcome | None = None


@dataclass
class SimCustomer:
    """Một khách mô phỏng và toàn bộ hoạt động của họ."""

    email: str
    full_name: str
    gender: str
    birthdate: date
    created_at: datetime
    persona: str
    style: str
    demo: bool
    occasions: list[str]
    interest: dict[int, float]  # mã danh mục -> trọng số quan tâm
    favourite_brands: set[int]
    price_target: float
    recipient_name: str
    phone: str
    address: str
    views: list[tuple[int, datetime]] = field(default_factory=list)
    cart_events: list[tuple[int, datetime]] = field(default_factory=list)
    cart: list[tuple[Variant, int, datetime]] = field(default_factory=list)
    wishlist: list[tuple[int, datetime]] = field(default_factory=list)
    orders: list[SimOrder] = field(default_factory=list)
    survey: SimSurvey | None = None


SurveyRunner = Callable[[SimCustomer, random.Random], SurveyOutcome]
"""Hàm đi khảo sát cho 1 khách (cần CSDL để lấy cây câu hỏi và thương hiệu được đề nghị)."""


class Generator:
    """Sinh khách và hành vi của họ trên danh mục `catalog`, tính thời gian lùi từ `now`."""

    def __init__(
        self,
        catalog: Catalog,
        occasions: dict[str, list[str]],
        now: datetime,
        seed: int,
        run_survey: SurveyRunner | None = None,
    ) -> None:
        """Nhận danh mục, các dịp sử dụng (mã dịp -> tên danh mục), thời điểm chạy và seed."""
        if not catalog.products:
            raise ValueError("Chưa có sản phẩm nào: cần nạp danh mục mẫu trước khi mô phỏng")
        self.catalog = catalog
        self.occasions = {
            code: catalog.category_ids_named(set(names)) for code, names in occasions.items()
        }
        self.now = now.replace(microsecond=0)
        self.seed = seed
        self.run_survey = run_survey
        self._weights: dict[tuple[int, int], list[float]] = {}

    # ---- khách ----

    def generate(self, customers: int, with_demo: bool = True) -> list[SimCustomer]:
        """Sinh `customers` khách thường và (nếu `with_demo`) các tài khoản demo."""
        result = []
        for index, persona in enumerate(assign_personas(customers, self.seed), start=1):
            rng = random.Random(f"{self.seed}-customer-{index}")
            result.append(self._customer(rng, customer_email(index), PERSONAS[persona]))
        if with_demo:
            for demo in DEMO_ACCOUNTS:
                rng = random.Random(f"{self.seed}-demo-{demo.local_part}")
                result.append(self._demo(rng, demo))
        return result

    def _customer(
        self,
        rng: random.Random,
        email: str,
        persona: Persona,
        gender: str | None = None,
        style: str | None = None,
        full_name: str | None = None,
        demo: bool = False,
        survey: str | None = RANDOM_SURVEY,
    ) -> SimCustomer:
        gender = gender or rng.choices(("MALE", "FEMALE", "OTHER"), (0.47, 0.5, 0.03))[0]
        age = min(18 + int(rng.expovariate(1 / 9)), 55)
        birthdate = (self.now - timedelta(days=age * 365 + rng.randint(0, 364))).date()
        style = style or rng.choices(*zip(*persona.styles, strict=True))[0]
        occasions = self._occasions(rng, age)
        interest = self._interest(rng, gender, occasions)
        name = full_name or random_name(rng, gender)
        customer = SimCustomer(
            email=email,
            full_name=name,
            gender=gender,
            birthdate=birthdate,
            created_at=self.now,
            persona=persona.code,
            style=style,
            demo=demo,
            occasions=occasions,
            interest=interest,
            favourite_brands=self._favourite_brands(rng, interest, style),
            price_target=self._price_target(rng, persona, style),
            recipient_name=name,
            phone=random_phone(rng),
            address=random_address(rng),
        )
        self._activity(rng, customer, persona)
        status = self._survey_status(rng) if survey == RANDOM_SURVEY else survey
        customer.survey = self._survey(rng, customer, status)
        return customer

    def _demo(self, rng: random.Random, demo: DemoAccount) -> SimCustomer:
        persona = PERSONAS[demo.persona]
        if not demo.active:
            persona = replace(persona, orders=(0, 0), browse_sessions=(0, 0))
        customer = self._customer(
            rng, demo.email, persona, demo.gender, demo.style, demo.full_name, True, None
        )
        if not demo.active:
            customer.created_at = self.now - timedelta(hours=rng.uniform(20, 30))
        customer.survey = self._survey(rng, customer, demo.survey)
        return customer

    def _occasions(self, rng: random.Random, age: int) -> list[str]:
        weights = OCCASION_WEIGHTS["young" if age < 25 else "adult"]
        codes = [c for c in self.occasions if self.occasions[c]]
        if not codes:
            return []
        picked = [rng.choices(codes, [weights.get(c, 1.0) for c in codes])[0]]
        if len(codes) > 1 and rng.random() < 0.5:
            rest = [c for c in codes if c != picked[0]]
            picked.append(rng.choices(rest, [weights.get(c, 1.0) for c in rest])[0])
        return picked

    def _interest(self, rng: random.Random, gender: str, occasions: list[str]) -> dict[int, float]:
        allowed = set(self.catalog.category_ids_for(None if gender == "OTHER" else gender))
        ids = sorted({cid for o in occasions for cid in self.occasions[o]} & allowed)
        if not ids:
            pool = sorted(allowed)
            ids = rng.sample(pool, min(3, len(pool)))
        return {cid: rng.uniform(0.3, 3.0) for cid in ids}

    def _favourite_brands(
        self, rng: random.Random, interest: dict[int, float], style: str
    ) -> set[int]:
        brands = [
            p.brand_id
            for cid in interest
            for p in self.catalog.by_category[cid]
            if p.brand_id is not None
        ]
        if not brands:
            return set()
        if style == "BRAND":
            return {rng.choice(brands) for _ in range(rng.randint(1, 2))}
        return {rng.choice(brands)} if rng.random() < 0.3 else set()

    @staticmethod
    def _price_target(rng: random.Random, persona: Persona, style: str) -> float:
        target = persona.price_target + rng.gauss(0, 0.12)
        if style == "SAVER":
            target = min(target, 0.25)
        if style in ("BRAND", "QUALITY"):
            target = max(target, 0.55)
        return min(max(target, 0.0), 1.0)

    # ---- hoạt động ----

    def _activity(self, rng: random.Random, customer: SimCustomer, persona: Persona) -> None:
        """Sinh đơn, lượt xem, giỏ hàng, yêu thích và khảo sát; đặt ngày đăng ký phù hợp."""
        far, near = persona.active_days
        order_times = [self._time_ago(rng, far, near) for _ in range(rng.randint(*persona.orders))]
        if order_times and persona.recent_order_days:
            order_times[0] = self._time_ago(rng, *persona.recent_order_days)
        order_times.sort()
        first = order_times[0] if order_times else self._time_ago(rng, far, near)
        customer.created_at = max(
            first - timedelta(days=rng.uniform(0.05, 20)),
            self.now - timedelta(days=HISTORY_DAYS),
        )
        browse_end = (
            self.now
            if persona.code not in ("CHURNED", "AT_RISK")
            else (order_times[-1] + timedelta(days=rng.uniform(5, 30)) if order_times else self.now)
        )
        browse_end = min(browse_end, self.now)

        for at in order_times:
            self._purchase(rng, customer, persona, max(at, customer.created_at))
        for _ in range(rng.randint(*persona.browse_sessions)):
            span = (browse_end - customer.created_at).total_seconds()
            at = customer.created_at + timedelta(seconds=rng.uniform(0, max(span, 60)))
            self._browse(rng, customer, min(at, self.now))
        self._wishlist_and_cart(rng, customer, persona)
        customer.views.sort(key=lambda v: v[1])
        customer.cart_events.sort(key=lambda v: v[1])
        customer.orders.sort(key=lambda o: o.created_at)
        reviewed: set[int] = set()
        for order in customer.orders:
            if order.status == "DELIVERED":
                self._reviews(rng, order, persona, reviewed)

    def _purchase(
        self, rng: random.Random, customer: SimCustomer, persona: Persona, at: datetime
    ) -> None:
        count = rng.randint(*persona.items_per_order)
        products: list[Product] = []
        main = self._pick_category(rng, customer)
        for i in range(count):
            # Món thêm thường ở danh mục khác (giày + vớ), đôi khi cùng danh mục.
            cid = main if i == 0 or rng.random() < 0.4 else self._pick_category(rng, customer)
            product = self._pick_product(rng, customer, cid, exclude=products)
            if product is not None:
                products.append(product)
        # Xem vài sản phẩm cùng danh mục trước khi mua, rồi thêm vào giỏ ngay trước khi đặt.
        for product in products:
            start = max(at - timedelta(hours=rng.uniform(0.2, 48)), customer.created_at)
            for _ in range(rng.randint(2, 6)):
                other = self._pick_product(rng, customer, product.category_id)
                customer.views.append((other.id, self._between(rng, start, at)))
            for _ in range(rng.randint(1, 2)):
                customer.views.append((product.id, self._between(rng, start, at)))
            customer.cart_events.append((product.id, at - timedelta(minutes=rng.uniform(1, 30))))
        items = [
            SimOrderItem(p, rng.choice(p.variants_in_stock()), self._quantity(rng))
            for p in products
        ]
        customer.orders.append(self._order(rng, at, items, persona))

    def _order(
        self, rng: random.Random, at: datetime, items: list[SimOrderItem], persona: Persona
    ) -> SimOrder:
        age_days = (self.now - at).total_seconds() / 86400
        online = rng.random() < (ONLINE_RATE / 2 if persona.code == "SAVER" else ONLINE_RATE)
        if age_days > DELIVERY_DAYS:
            status = "CANCELLED" if rng.random() < CANCEL_RATE else "DELIVERED"
        else:
            status = ("PENDING", "PROCESSING", "SHIPPING", "SHIPPING")[min(int(age_days), 3)]
        if status == "CANCELLED":
            online = False  # đơn trả trước bị hủy cần hoàn tiền: không mô phỏng
            updated = at + timedelta(hours=rng.uniform(1, 30))
        elif status == "DELIVERED":
            updated = at + timedelta(days=rng.uniform(2, DELIVERY_DAYS))
        else:
            updated = at + timedelta(hours=rng.uniform(0, age_days * 24))
        paid_at = at + timedelta(minutes=rng.uniform(1, 5)) if online else None
        order = SimOrder(
            created_at=at,
            updated_at=min(updated, self.now),
            status=status,
            payment_method="ONLINE" if online else "COD",
            paid_at=min(paid_at, self.now) if paid_at else None,
            transaction_no=str(rng.randint(10_000_000, 99_999_999)) if online else None,
            idempotency_key=f"sim-{uuid.UUID(int=rng.getrandbits(128), version=4)}",
            items=items,
        )
        return order

    def _reviews(
        self, rng: random.Random, order: SimOrder, persona: Persona, reviewed: set[int]
    ) -> None:
        """Đánh giá các món của đơn đã giao; mỗi sản phẩm khách chỉ đánh giá 1 lần."""
        for index, item in enumerate(order.items):
            if item.product.id in reviewed or rng.random() >= persona.review_rate:
                continue
            at = order.updated_at + timedelta(days=rng.uniform(0.2, 7))
            if at > self.now:
                continue
            rating = min(5, max(1, round(item.product.quality + rng.gauss(0, 0.8))))
            comment = rng.choice(REVIEW_COMMENTS[rating]) if rng.random() < 0.7 else None
            order.reviews.append(SimReview(index, rating, comment, at))
            reviewed.add(item.product.id)

    def _browse(self, rng: random.Random, customer: SimCustomer, at: datetime) -> None:
        if rng.random() < EXPLORE_RATE:
            gender = None if customer.gender == "OTHER" else customer.gender
            cid = rng.choice(self.catalog.category_ids_for(gender))
        else:
            cid = self._pick_category(rng, customer)
        when = at
        for _ in range(rng.randint(2, 7)):
            product = self._pick_product(rng, customer, cid)
            customer.views.append((product.id, min(when, self.now)))
            if rng.random() < 0.06:
                customer.cart_events.append((product.id, min(when, self.now)))
            when += timedelta(seconds=rng.uniform(20, 300))

    def _wishlist_and_cart(
        self, rng: random.Random, customer: SimCustomer, persona: Persona
    ) -> None:
        bought = {item.product.id for o in customer.orders for item in o.items}
        viewed: dict[int, datetime] = {}
        for pid, at in customer.views:
            viewed[pid] = max(at, viewed.get(pid, at))
        candidates = sorted(p for p in viewed if p not in bought)
        if candidates and rng.random() < WISHLIST_RATE:
            for pid in rng.sample(candidates, min(len(candidates), rng.randint(1, 4))):
                customer.wishlist.append((pid, viewed[pid] + timedelta(seconds=30)))
        # Giỏ đang bỏ dở: chỉ khách còn hoạt động, sản phẩm xem trong 30 ngày, còn hàng.
        recent = [p for p in candidates if self.now - viewed[p] < timedelta(days=30)]
        # Tài khoản demo "chỉ xem" luôn có giỏ bỏ dở để demo gợi ý theo giỏ hàng.
        chance = 1.0 if customer.demo and persona.code == "BROWSER" else OPEN_CART_RATE
        if persona.code in ("CHURNED", "AT_RISK") or not recent or rng.random() >= chance:
            return
        for pid in rng.sample(recent, min(len(recent), rng.randint(1, 2))):
            product = self.catalog.products[pid]
            in_stock = [v for v in product.variants if v.stock > 0]
            if not in_stock:
                continue
            at = min(viewed[pid] + timedelta(minutes=1), self.now)
            customer.cart.append((rng.choice(in_stock), 1, at))
            customer.cart_events.append((pid, at))

    def _survey_status(self, rng: random.Random) -> str | None:
        roll = rng.random()
        if roll < SURVEY_COMPLETED_RATE:
            return "COMPLETED"
        if roll < SURVEY_COMPLETED_RATE + SURVEY_SKIPPED_RATE:
            return "SKIPPED"
        return None

    def _survey(
        self, rng: random.Random, customer: SimCustomer, status: str | None
    ) -> SimSurvey | None:
        if status is None:
            return None
        at = min(customer.created_at + timedelta(minutes=rng.uniform(1, 30)), self.now)
        if status == "SKIPPED" or self.run_survey is None:
            return SimSurvey("SKIPPED", at)
        return SimSurvey("COMPLETED", at, self.run_survey(customer, rng))

    # ---- chọn danh mục, sản phẩm ----

    @staticmethod
    def _pick_category(rng: random.Random, customer: SimCustomer) -> int:
        ids = list(customer.interest)
        return rng.choices(ids, [customer.interest[c] for c in ids])[0]

    def _pick_product(
        self,
        rng: random.Random,
        customer: SimCustomer,
        category_id: int,
        exclude: list[Product] | None = None,
    ) -> Product | None:
        products = self.catalog.by_category[category_id]
        weights = self.product_weights(customer, category_id)
        if exclude:
            skip = {p.id for p in exclude}
            weights = [0.0 if p.id in skip else w for p, w in zip(products, weights, strict=True)]
            if not any(weights):
                return None
        return rng.choices(products, weights)[0]

    def product_weights(self, customer: SimCustomer, category_id: int) -> list[float]:
        """Khả năng khách chọn từng sản phẩm của danh mục (cùng thứ tự `by_category`)."""
        key = (id(customer), category_id)
        cached = self._weights.get(key)
        if cached is None:
            cached = [self._weight(customer, p) for p in self.catalog.by_category[category_id]]
            self._weights[key] = cached
        return cached

    @staticmethod
    def _weight(customer: SimCustomer, product: Product) -> float:
        weight = product.popularity ** (1.5 if customer.style == "TREND" else 1.0)
        weight *= math.exp(-((product.price_rank - customer.price_target) ** 2) / (2 * 0.2**2))
        weight *= (product.quality / 4) ** (4 if customer.style == "QUALITY" else 1)
        if product.brand_id in customer.favourite_brands:
            weight *= 6 if customer.style == "BRAND" else 2
        return weight

    # ---- thời gian ----

    def _time_ago(self, rng: random.Random, far_days: float, near_days: float) -> datetime:
        """Một thời điểm trong khoảng (cách đây far_days, cách đây near_days), giờ theo thói quen."""
        at = self.now - timedelta(days=rng.uniform(near_days, far_days))
        hour = rng.choices(range(24), HOUR_WEIGHTS)[0]
        moved = at.replace(hour=hour, minute=rng.randint(0, 59), second=rng.randint(0, 59))
        return moved if moved <= self.now else at

    @staticmethod
    def _between(rng: random.Random, start: datetime, end: datetime) -> datetime:
        return start + timedelta(seconds=rng.uniform(0, (end - start).total_seconds()))

    @staticmethod
    def _quantity(rng: random.Random) -> int:
        return rng.choices((1, 2, 3), (0.85, 0.12, 0.03))[0]
