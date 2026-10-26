"""Đi cây câu hỏi khảo sát lần đầu (migration V5) như khách thật, để lưu kết quả mô phỏng.

Cây câu hỏi đọc từ CSDL; cách gộp thông tin từ đáp án và lọc danh mục theo giới tính làm giống
SurveyService của backend, nên trang /survey của khách mô phỏng hiện đúng hồ sơ đã lưu.
"""

import random
from collections.abc import Callable
from dataclasses import dataclass, field

from sqlalchemy import Connection, bindparam, text

from app.simulation.catalog import Catalog, category_gender

START_QUESTION = "Q1"
BRAND_CHOICE_LIMIT = 15  # SurveyService.BRAND_CHOICE_LIMIT


@dataclass
class Option:
    """Một đáp án và thông tin nó đóng góp vào hồ sơ khách."""

    code: str
    next_question_id: int | None
    purpose: str | None
    gender: str | None
    style: str | None
    price_min: int | None
    price_max: int | None
    price_flexible: bool | None
    brand_loyalty: str | None
    min_rating: float | None
    category_names: list[str] = field(default_factory=list)


@dataclass
class Question:
    """Một câu hỏi: SINGLE, MULTIPLE (tối đa `max_choices`) hoặc BRAND."""

    id: int
    code: str
    answer_type: str
    max_choices: int
    next_question_id: int | None
    options: dict[str, Option] = field(default_factory=dict)


@dataclass
class SurveyTree:
    """Cây câu hỏi theo mã và theo id."""

    by_code: dict[str, Question]
    by_id: dict[int, Question]


@dataclass
class SurveyOutcome:
    """Kết quả đi hết khảo sát: câu trả lời (lưu JSON) và hồ sơ ghi vào user_preference."""

    answers: list[dict]
    style: str | None
    brand_loyalty: str | None
    min_rating: float | None
    category_ids: list[int]
    brand_id: int | None
    price_min: int
    price_max: int | None


def load_tree(conn: Connection) -> SurveyTree:
    """Đọc toàn bộ câu hỏi, đáp án và danh mục của đáp án."""
    questions: dict[int, Question] = {}
    for qid, code, answer_type, max_choices, next_id in conn.execute(
        text(
            "SELECT id, code, answer_type, max_choices, next_question_id "
            "FROM survey_question ORDER BY id"
        )
    ):
        questions[qid] = Question(qid, code, answer_type, max_choices, next_id)
    options: dict[int, Option] = {}
    for row in conn.execute(
        text(
            "SELECT id, question_id, code, next_question_id, purpose, gender, style, price_min, "
            "price_max, price_flexible, brand_loyalty, min_rating "
            "FROM survey_option ORDER BY question_id, display_order"
        )
    ):
        oid, qid, code, next_id, purpose, gender, style, pmin, pmax, flexible, loyalty, rating = row
        option = Option(
            code,
            next_id,
            purpose,
            gender,
            style,
            None if pmin is None else int(pmin),
            None if pmax is None else int(pmax),
            None if flexible is None else bool(flexible),
            loyalty,
            None if rating is None else float(rating),
        )
        options[oid] = option
        questions[qid].options[code] = option
    for oid, name in conn.execute(
        text("SELECT option_id, category_name FROM survey_option_category ORDER BY category_name")
    ):
        options[oid].category_names.append(name)
    return SurveyTree({q.code: q for q in questions.values()}, questions)


def occasion_categories(tree: SurveyTree, question_code: str = "Q2") -> dict[str, list[str]]:
    """Dịp sử dụng (đáp án câu Q2) và tên các danh mục ứng với từng dịp."""
    question = tree.by_code.get(question_code)
    if question is None:
        return {}
    return {code: list(o.category_names) for code, o in question.options.items()}


Chooser = Callable[[Question, list[int]], list[str] | int | None]
"""Chọn đáp án cho 1 câu: danh sách mã đáp án, hoặc mã thương hiệu (câu BRAND, None = bỏ qua).

Tham số thứ 2 là các danh mục đã gộp tới lúc đó (để biết thương hiệu nào được đề nghị).
"""


_BRAND_CHOICES = text(
    "SELECT b.id FROM product p JOIN brand b ON b.id = p.brand_id "
    "WHERE p.category_id IN :ids AND p.deleted_at IS NULL "
    "GROUP BY b.id, b.name ORDER BY COUNT(p.id) DESC, b.name ASC LIMIT :limit"
).bindparams(bindparam("ids", expanding=True))


def brand_choices(conn: Connection, category_ids: list[int]) -> list[int]:
    """Thương hiệu backend đề nghị ở câu chọn thương hiệu (BrandRepository.findTopInCategories)."""
    if not category_ids:
        return []
    rows = conn.execute(_BRAND_CHOICES, {"ids": category_ids, "limit": BRAND_CHOICE_LIMIT})
    return [bid for (bid,) in rows]


def walk(tree: SurveyTree, catalog: Catalog, choose: Chooser) -> SurveyOutcome:
    """Trả lời từ câu đầu đến hết theo `choose`, gộp thông tin như SurveyService.walk.

    Câu MULTIPLE đi tiếp theo đáp án chọn đầu tiên; câu BRAND theo câu tiếp theo của câu hỏi.
    """
    answers: list[dict] = []
    state: dict = {"categories": []}
    current = tree.by_code[START_QUESTION]
    while current is not None:
        if current.answer_type == "BRAND":
            brand_id = choose(current, _category_ids(catalog, state))
            answers.append({"questionCode": current.code, "optionCodes": [], "brandId": brand_id})
            if brand_id is not None:
                state["brand_id"] = brand_id
            next_id = current.next_question_id
        else:
            codes = choose(current, _category_ids(catalog, state))
            limit = 1 if current.answer_type == "SINGLE" else current.max_choices
            if not codes or len(codes) > limit or len(set(codes)) != len(codes):
                raise ValueError(f"Chọn đáp án không hợp lệ ở câu {current.code}: {codes}")
            answers.append({"questionCode": current.code, "optionCodes": codes, "brandId": None})
            for code in codes:
                _apply(state, current.options[code])
            next_id = current.options[codes[0]].next_question_id
        current = None if next_id is None else tree.by_id[next_id]

    if state.get("purpose") == "GIFT":
        raise ValueError("Nhánh tìm quà không lưu kết quả (BR-38)")
    price_max = state.get("price_max")
    if price_max is not None and state.get("price_flexible"):
        price_max = price_max * 13 // 10
    return SurveyOutcome(
        answers=answers,
        style=state.get("style"),
        brand_loyalty=state.get("brand_loyalty"),
        min_rating=state.get("min_rating"),
        category_ids=_category_ids(catalog, state),
        brand_id=state.get("brand_id"),
        price_min=state.get("price_min") or 0,
        price_max=price_max,
    )


def _apply(state: dict, option: Option) -> None:
    """Gộp thông tin của 1 đáp án vào hồ sơ đang đi (SurveyService.Walk.apply)."""
    for key in ("purpose", "gender", "style", "brand_loyalty", "min_rating"):
        value = getattr(option, key)
        if value is not None:
            state[key] = value
    for name in option.category_names:
        if name not in state["categories"]:
            state["categories"].append(name)
    if option.price_min is not None or option.price_max is not None:
        state["price_min"] = option.price_min
        state["price_max"] = option.price_max
    if option.price_flexible is not None:
        state["price_flexible"] = option.price_flexible


def _category_ids(catalog: Catalog, state: dict) -> list[int]:
    """Danh mục của các dịp đã chọn, bỏ danh mục của giới tính còn lại (SurveyService)."""
    gender = state.get("gender")
    names = {
        name
        for name in state["categories"]
        if not (
            (gender == "MALE" and category_gender(name) == "FEMALE")
            or (gender == "FEMALE" and category_gender(name) == "MALE")
        )
    }
    return sorted(cid for cid, name in catalog.categories.items() if name in names)


def make_runner(conn: Connection, tree: SurveyTree, catalog: Catalog) -> Callable:
    """Hàm đi khảo sát cho 1 khách mô phỏng, chọn đáp án khớp với hồ sơ ẩn của khách.

    Chọn theo thông tin của đáp án (giới tính, phong cách, khoảng giá...) chứ không theo mã cố
    định, nên vẫn chạy nếu cây câu hỏi đổi chữ hay thêm đáp án.
    """

    def run(customer, rng: random.Random) -> SurveyOutcome:
        budget = _typical_price(catalog, customer)

        def choose(question: Question, category_ids: list[int]) -> list[str] | int | None:
            if question.answer_type == "BRAND":
                offered = brand_choices(conn, category_ids)
                favourites = [b for b in offered if b in customer.favourite_brands]
                if favourites:
                    return favourites[0]
                if offered and customer.style == "BRAND":
                    return offered[0]
                return None
            options = list(question.options.values())
            if question.answer_type == "MULTIPLE":
                picked = [o.code for o in options if o.code in customer.occasions]
                return picked[: question.max_choices] or [rng.choice(options).code]
            return [_choose_single(options, customer, budget, rng).code]

        return walk(tree, catalog, choose)

    return run


def _choose_single(options: list[Option], customer, budget: int, rng: random.Random) -> Option:
    """Đáp án của câu chọn 1, theo loại thông tin mà các đáp án của câu đó mang."""
    gender = customer.gender if customer.gender != "OTHER" else rng.choice(("MALE", "FEMALE"))
    rules = (
        (lambda o: o.purpose is not None, lambda o: o.purpose == "SELF" and o.gender == gender),
        (lambda o: o.style is not None, lambda o: o.style == customer.style),
        (
            lambda o: o.price_min is not None or o.price_max is not None,
            lambda o: (o.price_min or 0) <= budget < (o.price_max or 10**12),
        ),
        (
            lambda o: o.price_flexible is not None,
            lambda o: o.price_flexible == (customer.style != "SAVER"),
        ),
        (
            lambda o: o.brand_loyalty is not None,
            lambda o: o.brand_loyalty == ("STRONG" if customer.style == "BRAND" else "OPEN"),
        ),
        (
            lambda o: o.min_rating is not None,
            lambda o: o.min_rating == (4.5 if customer.style == "QUALITY" else 4.0),
        ),
    )
    for applies, matches in rules:
        if any(applies(o) for o in options):
            fitting = [o for o in options if matches(o)]
            return rng.choice(fitting) if fitting else rng.choice(options)
    return rng.choice(options)


def _typical_price(catalog: Catalog, customer) -> int:
    """Giá 1 món khách hay mua: giá ở vị trí ưa thích của khách, trung bình các danh mục."""
    prices = []
    for cid in customer.interest:
        sorted_prices = sorted(p.price for p in catalog.by_category[cid])
        prices.append(sorted_prices[round(customer.price_target * (len(sorted_prices) - 1))])
    return round(sum(prices) / len(prices)) if prices else 0
