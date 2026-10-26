"""Các kiểu khách mô phỏng (persona), tài khoản demo và dữ liệu tên, địa chỉ để sinh khách."""

import hashlib
import random
from dataclasses import dataclass

EMAIL_DOMAIN = "mophong.test"


@dataclass(frozen=True)
class Persona:
    """Một kiểu khách: số đơn, thời gian hoạt động, cách mua và xem hàng.

    Thời gian tính bằng số ngày trước thời điểm chạy. Các kiểu được chọn để khác nhau rõ theo
    R (mua gần đây), F (số đơn), M (tổng tiền) cho bài toán phân nhóm khách hàng ở Tuần 9.
    """

    code: str
    label: str
    share: float
    orders: tuple[int, int]  # số đơn (nhỏ nhất, lớn nhất)
    active_days: tuple[float, float]  # đơn rơi vào khoảng (cách đây xa nhất, gần nhất) ngày
    recent_order_days: tuple[float, float] | None  # có 1 đơn gần đây trong khoảng này
    items_per_order: tuple[int, int]
    price_target: float  # vị trí giá ưa thích trong danh mục: 0 = rẻ nhất, 1 = đắt nhất
    browse_sessions: tuple[int, int]  # số lần chỉ xem, không mua
    review_rate: float  # xác suất đánh giá mỗi sản phẩm đã nhận
    styles: tuple[tuple[str, float], ...]  # phong cách mua sắm (câu Q3) và tỉ lệ


PERSONAS: dict[str, Persona] = {
    p.code: p
    for p in (
        Persona(
            "VIP",
            "Khách VIP: mua nhiều, thường xuyên, chi nhiều",
            0.07,
            (10, 18),
            (365, 1),
            (1, 20),
            (1, 4),
            0.75,
            (15, 30),
            0.5,
            (("BRAND", 0.5), ("QUALITY", 0.4), ("TREND", 0.1)),
        ),
        Persona(
            "LOYAL",
            "Khách quen: mua đều đặn",
            0.23,
            (5, 9),
            (365, 1),
            (1, 45),
            (1, 3),
            0.5,
            (8, 20),
            0.4,
            (("QUALITY", 0.35), ("TREND", 0.35), ("BRAND", 0.15), ("SAVER", 0.15)),
        ),
        Persona(
            "SAVER",
            "Khách tiết kiệm: mua khá thường xuyên, món rẻ",
            0.20,
            (4, 8),
            (300, 1),
            (1, 60),
            (1, 2),
            0.15,
            (10, 25),
            0.3,
            (("SAVER", 0.8), ("TREND", 0.2)),
        ),
        Persona(
            "NEW",
            "Khách mới: vừa đăng ký, 1-2 đơn",
            0.15,
            (1, 2),
            (40, 1),
            None,
            (1, 2),
            0.5,
            (2, 6),
            0.3,
            (("TREND", 0.5), ("SAVER", 0.3), ("QUALITY", 0.2)),
        ),
        Persona(
            "AT_RISK",
            "Khách sắp rời bỏ: từng mua khá nhiều, 4-10 tháng nay không mua",
            0.10,
            (3, 6),
            (300, 120),
            None,
            (1, 3),
            0.5,
            (3, 8),
            0.3,
            (("QUALITY", 0.3), ("TREND", 0.3), ("BRAND", 0.2), ("SAVER", 0.2)),
        ),
        Persona(
            "CHURNED",
            "Khách đã rời bỏ: mua ít, lần cuối 8-12 tháng trước",
            0.12,
            (1, 3),
            (365, 240),
            None,
            (1, 2),
            0.4,
            (1, 4),
            0.25,
            (("SAVER", 0.4), ("TREND", 0.4), ("QUALITY", 0.2)),
        ),
        Persona(
            "BROWSER",
            "Chỉ xem: chưa mua đơn nào",
            0.13,
            (0, 0),
            (60, 0),
            None,
            (1, 1),
            0.5,
            (4, 12),
            0.0,
            (("TREND", 0.4), ("SAVER", 0.3), ("QUALITY", 0.2), ("BRAND", 0.1)),
        ),
    )
}


@dataclass(frozen=True)
class DemoAccount:
    """Tài khoản demo có tên dễ nhớ, mỗi tài khoản cho thấy 1 trường hợp của gợi ý/phân nhóm."""

    local_part: str
    full_name: str
    persona: str
    gender: str
    style: str
    survey: str | None  # COMPLETED, SKIPPED hoặc None (chưa gặp khảo sát)
    active: bool = True  # False: không có lượt xem, giỏ hàng, đơn nào
    note: str = ""

    @property
    def email(self) -> str:
        """Địa chỉ email đăng nhập."""
        return f"{self.local_part}@{EMAIL_DOMAIN}"


DEMO_ACCOUNTS: tuple[DemoAccount, ...] = (
    DemoAccount(
        "demo.vip",
        "Trần Ngọc Vy",
        "VIP",
        "FEMALE",
        "BRAND",
        "COMPLETED",
        note="Mua nhiều, chuộng 1-2 thương hiệu: gợi ý theo lịch sử mua",
    ),
    DemoAccount(
        "demo.tietkiem",
        "Lê Minh Khang",
        "SAVER",
        "MALE",
        "SAVER",
        "COMPLETED",
        note="Mua thường xuyên món giá rẻ",
    ),
    DemoAccount(
        "demo.ngungmua",
        "Phạm Quốc Bảo",
        "CHURNED",
        "MALE",
        "TREND",
        "SKIPPED",
        note="Lần mua cuối 8-12 tháng trước, đã bỏ qua khảo sát",
    ),
    DemoAccount(
        "demo.chixem",
        "Nguyễn Thu Hà",
        "BROWSER",
        "FEMALE",
        "TREND",
        None,
        note="Chỉ xem và bỏ giỏ, chưa mua: gợi ý theo lượt xem",
    ),
    DemoAccount(
        "demo.khaosat",
        "Võ Hoàng Nam",
        "NEW",
        "MALE",
        "QUALITY",
        "COMPLETED",
        active=False,
        note="Mới đăng ký, chỉ làm khảo sát: gợi ý theo khảo sát",
    ),
    DemoAccount(
        "demo.moi",
        "Đặng Mai Anh",
        "NEW",
        "FEMALE",
        "TREND",
        None,
        active=False,
        note="Mới đăng ký, chưa làm gì: gợi ý bán chạy",
    ),
)


def assign_personas(count: int, seed: int) -> list[str]:
    """Kiểu khách của `count` khách thường theo tỉ lệ `share`, xáo trộn theo `seed`.

    Chỉ phụ thuộc `count` và `seed` (không phụ thuộc danh mục hay ngày chạy), nên notebook EDA
    và việc đánh giá phân nhóm sau này dựng lại được nhãn thật của từng khách.
    """
    codes = list(PERSONAS)
    quotas = [int(PERSONAS[c].share * count) for c in codes]
    # Phần dư chia cho các kiểu có phần lẻ lớn nhất, để tổng đúng bằng count.
    remainders = sorted(
        range(len(codes)), key=lambda i: PERSONAS[codes[i]].share * count - quotas[i], reverse=True
    )
    for i in remainders[: count - sum(quotas)]:
        quotas[i] += 1
    labels = [code for code, quota in zip(codes, quotas, strict=True) for _ in range(quota)]
    # Xáo trộn tất định: xếp theo mã băm của (seed, vị trí).
    order = sorted(
        range(count), key=lambda i: hashlib.sha256(f"{seed}-personas-{i}".encode()).digest()
    )
    return [labels[i] for i in order]


def customer_email(index: int) -> str:
    """Email của khách thường thứ `index` (đếm từ 1)."""
    return f"kh{index:04d}@{EMAIL_DOMAIN}"


# ---- tên, địa chỉ, số điện thoại ----

SURNAMES = (
    ("Nguyễn", 30),
    ("Trần", 11),
    ("Lê", 9),
    ("Phạm", 7),
    ("Hoàng", 5),
    ("Huỳnh", 5),
    ("Phan", 4),
    ("Vũ", 4),
    ("Võ", 4),
    ("Đặng", 3),
    ("Bùi", 3),
    ("Đỗ", 3),
    ("Hồ", 2),
    ("Ngô", 2),
    ("Dương", 2),
    ("Lý", 1),
)
MIDDLE_NAMES = {
    "MALE": ("Văn", "Minh", "Quốc", "Đức", "Hoàng", "Thành", "Gia", "Anh", "Hữu", "Tuấn"),
    "FEMALE": ("Thị", "Ngọc", "Thu", "Thanh", "Mai", "Bảo", "Phương", "Khánh", "Minh", "Hồng"),
}
GIVEN_NAMES = {
    "MALE": (
        "An",
        "Bảo",
        "Cường",
        "Dũng",
        "Duy",
        "Hải",
        "Hiếu",
        "Hùng",
        "Huy",
        "Khang",
        "Khoa",
        "Long",
        "Minh",
        "Nam",
        "Nghĩa",
        "Phát",
        "Phúc",
        "Quân",
        "Sơn",
        "Tài",
        "Thắng",
        "Thịnh",
        "Trí",
        "Trung",
        "Tuấn",
        "Việt",
        "Vinh",
    ),
    "FEMALE": (
        "Anh",
        "Châu",
        "Chi",
        "Diệp",
        "Dung",
        "Giang",
        "Hà",
        "Hạnh",
        "Hằng",
        "Hiền",
        "Hoa",
        "Hương",
        "Lan",
        "Linh",
        "Ly",
        "Mai",
        "My",
        "Ngân",
        "Nhi",
        "Nhung",
        "Oanh",
        "Phương",
        "Quỳnh",
        "Tâm",
        "Thảo",
        "Trang",
        "Trâm",
        "Uyên",
        "Vy",
        "Yến",
    ),
}
STREETS = (
    "Võ Văn Ngân",
    "Lê Văn Việt",
    "Kha Vạn Cân",
    "Nguyễn Thị Minh Khai",
    "Điện Biên Phủ",
    "Cách Mạng Tháng Tám",
    "Lý Thường Kiệt",
    "Phan Văn Trị",
    "Nguyễn Văn Linh",
    "Hoàng Diệu",
    "Trần Hưng Đạo",
    "Lê Lợi",
    "Hai Bà Trưng",
    "Nguyễn Trãi",
    "Quang Trung",
    "Phạm Văn Đồng",
)
AREAS = (
    ("Phường Linh Chiểu, TP. Thủ Đức", "TP. Hồ Chí Minh", 18),
    ("Phường Hiệp Phú, TP. Thủ Đức", "TP. Hồ Chí Minh", 8),
    ("Phường Bến Nghé, Quận 1", "TP. Hồ Chí Minh", 8),
    ("Phường 7, Quận 3", "TP. Hồ Chí Minh", 6),
    ("Phường 12, Quận Gò Vấp", "TP. Hồ Chí Minh", 8),
    ("Phường 15, Quận Tân Bình", "TP. Hồ Chí Minh", 7),
    ("Phường Dĩ An, TP. Dĩ An", "Bình Dương", 6),
    ("Phường Tân Mai, TP. Biên Hòa", "Đồng Nai", 4),
    ("Phường Dịch Vọng, Quận Cầu Giấy", "Hà Nội", 8),
    ("Phường Láng Hạ, Quận Đống Đa", "Hà Nội", 6),
    ("Phường Hải Châu 1, Quận Hải Châu", "Đà Nẵng", 5),
    ("Phường Xuân Khánh, Quận Ninh Kiều", "Cần Thơ", 4),
)


def random_name(rng: random.Random, gender: str) -> str:
    """Họ tên tiếng Việt ngẫu nhiên theo giới tính (OTHER lấy tên ngẫu nhiên của 1 giới)."""
    key = gender if gender in MIDDLE_NAMES else rng.choice(("MALE", "FEMALE"))
    surname = rng.choices([s for s, _ in SURNAMES], [w for _, w in SURNAMES])[0]
    return f"{surname} {rng.choice(MIDDLE_NAMES[key])} {rng.choice(GIVEN_NAMES[key])}"


def random_phone(rng: random.Random) -> str:
    """Số di động 10 chữ số, đúng mẫu backend kiểm tra (0 và 9-10 chữ số)."""
    return "0" + rng.choice("35789") + "".join(rng.choice("0123456789") for _ in range(8))


def random_address(rng: random.Random) -> str:
    """Địa chỉ giao hàng ngẫu nhiên."""
    area, city, _ = rng.choices(AREAS, [w for *_, w in AREAS])[0]
    return f"{rng.randint(1, 450)} {rng.choice(STREETS)}, {area}, {city}"
