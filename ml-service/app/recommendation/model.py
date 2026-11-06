"""Gợi ý hybrid: lọc theo nội dung + hay được mua cùng + khảo sát + bán chạy.

Trang chủ (US-13), nguồn chọn theo BR-38:
1. Đủ dữ liệu hành vi (BR-19): hồ sơ P(u) là trung bình có trọng số vector các sản phẩm khách đã
   tương tác, cộng vector khảo sát s(u) với trọng số thấp nhất (công thức (1.3)). Tương tác càng
   gần đây càng nặng (nhân hệ số 1 + β · 0,5^(tuổi / chu kỳ bán rã)), để việc khách vừa làm thấy
   ngay trên trang chủ. Điểm của sản phẩm j:

       score(j) = w_c · cos(P(u), v(j)) + w_cf · cf(j) + w_p · pop(j) + w_t · trend(j)
                  + λ · b(j)

   cf(j) là tổng có trọng số độ tương đồng "hay mua cùng" giữa j và các sản phẩm đã tương tác,
   chia cho giá trị lớn nhất; pop(j) là số đã bán (thang log) chia cho lớn nhất; trend(j) là số
   đã bán mấy ngày gần đây, lượt bán càng mới càng nặng (trending.py); λ · b(j) là điểm cộng theo
   kiểu người mua trong khảo sát (công thức (1.4)), bằng 0 nếu chưa làm khảo sát.
2. Chưa đủ dữ liệu nhưng đã làm khảo sát: score(j) = cos(s(u), v(j)) + w_s · pop(j) + λ · b(j)
   (công thức (1.4) thêm thành phần bán chạy).
3. Không có cả hai: không có danh sách riêng, trang chủ hiện sản phẩm bán chạy.

Chỉ gợi ý sản phẩm đang bán, còn hàng, khách chưa mua và chưa có trong giỏ. Danh sách được dàn
đều danh mục: mỗi sản phẩm cùng danh mục với một sản phẩm đã chọn bị trừ `Weights.diversity`.

Trang chi tiết (US-12): độ tương đồng nội dung với sản phẩm đang xem, cộng "hay mua cùng".
"""

from collections.abc import Collection, Sequence
from dataclasses import dataclass
from datetime import datetime

import numpy as np
from scipy import sparse

from app.recommendation.data import ProductStats, SurveyProfile, UserSignals
from app.recommendation.features import CatalogItem, ItemVectors
from app.recommendation.gender import FEMALE, MALE
from app.recommendation.trending import SalesHistory

# Trọng số theo loại tương tác mạnh nhất với sản phẩm (đã mua > trong giỏ > yêu thích > đã xem),
# vector khảo sát nhẹ nhất (BR-38). Chốt bằng đánh giá trên dữ liệu mô phỏng (evaluate.py).
INTERACTION_WEIGHTS = {"PURCHASE": 5.0, "CART": 3.0, "WISHLIST": 2.0, "VIEW": 1.0}
SURVEY_WEIGHT = 0.5

# Ưu tiên tương tác gần đây: hệ số 1 + β · 0,5^(tuổi / chu kỳ bán rã), để khách thấy gợi ý đổi theo
# việc vừa làm (thêm 1 món vào giỏ: trung bình 0,5 → 1,6 trên 8 món trang chủ đổi). Đánh giá ngoại
# tuyến (đã bỏ lượt xem trong 48 giờ trước đơn, evaluate.SESSION_GAP) cho tỷ lệ trúng gần như bằng
# β = 0 (chênh dưới 0,015 trên 3 bộ mô phỏng): hệ số này để khách thấy gợi ý đổi, không làm tăng độ
# chính xác. β lớn hơn làm danh sách chỉ xoay quanh vài món vừa xem, mất các sở thích lâu dài.
RECENT_BOOST = 3.0
RECENT_HALF_LIFE_DAYS = 1.0

RECENT_PHRASE_HOURS = 24
"""Tương tác trong chừng này giờ thì lý do gợi ý ghi "vừa xem", "vừa thêm vào giỏ"."""

TRENDING_REASON = "Đang bán chạy mấy ngày gần đây"
TRENDING_REASON_MIN = 0.5
"""Lý do ghi "đang bán chạy" khi trend(j) từ mức này và phần đóng góp lớn hơn nội dung, mua cùng."""


@dataclass(frozen=True)
class Weights:
    """Trọng số trộn các nguồn gợi ý.

    Mặc định chốt bằng đánh giá "giữ lại đơn cuối" trên dữ liệu mô phỏng (evaluate.py --grid), trừ
    w_t và chu kỳ bán rã của "đang bán chạy": dữ liệu mô phỏng không có xu hướng theo thời gian nên
    2 tham số này chọn trên dữ liệu mua hàng thật của H&M, giữ nguyên các trọng số khác. Thử w_t
    0; 0,5; 1; 2 với chu kỳ 3 và 7 ngày trên tuần kiểm định 09/09–15/09/2020 (mẫu 5% khách), lấy
    bộ có NDCG@10 cao nhất, rồi mới đo trên tuần kiểm tra 16/09–22/09/2020 (data/hm/ket-qua.md).
    """

    content: float = 0.7
    copurchase: float = 0.2
    popular: float = 0.1
    trending: float = 1.0
    """w_t: đang bán chạy (trending.py)."""
    trending_half_life_days: float = 7.0
    """Lượt bán chừng này ngày trước nặng bằng nửa lượt bán hôm nay."""
    style: float = 0.15
    """λ của công thức (1.4)."""
    diversity: float = 0.05
    """Điểm trừ cho mỗi sản phẩm cùng danh mục đã được chọn trước đó."""
    survey_popular: float = 0.2
    """Trọng số bán chạy khi gợi ý chỉ theo khảo sát (nhiều sản phẩm cùng danh mục, cùng tầm giá
    có cosine bằng nhau, nên bán chạy quyết định thứ tự)."""
    recent_boost: float = RECENT_BOOST
    """β: tương tác vừa xảy ra nặng gấp (1 + β) lần tương tác cùng loại đã lâu."""
    recent_half_life_days: float = RECENT_HALF_LIFE_DAYS
    """Sau chừng này ngày, phần ưu tiên còn một nửa."""


DEFAULT_WEIGHTS = Weights()

RATING_PRIOR_WEIGHT = 5.0
"""Số đánh giá "ảo" bằng trung bình chung cộng vào mỗi sản phẩm khi xét kiểu cầu toàn."""

RELATED_CONTENT = 0.75
RELATED_COPURCHASE = 0.25

TIE_BREAK = 1e-3
"""Điểm nội dung bằng nhau (cùng danh mục, cùng tầm giá) thì sản phẩm bán chạy hơn đứng trước."""

HOME_LIMIT = 20
"""Số gợi ý lưu cho mỗi tài khoản; trang chủ hiện 8, phần dư thay cho sản phẩm vừa hết hàng."""

RELATED_LIMIT = 4
"""BR-21."""

SOURCE_BEHAVIOUR = "BEHAVIOR"
SOURCE_SURVEY = "SURVEY"
SOURCE_RELATED = "RELATED"

MAX_NAME_LENGTH = 40

_SELF_PHRASES = {
    "WISHLIST": "Có trong danh sách yêu thích của bạn",
    "VIEW": "Bạn đã xem sản phẩm này gần đây",
}

_KIND_PHRASES = {
    "PURCHASE": "bạn đã mua",
    "CART": "trong giỏ hàng của bạn",
    "WISHLIST": "bạn đã thích",
    "VIEW": "bạn đã xem",
}

_RECENT_KIND_PHRASES = {
    "PURCHASE": "bạn vừa mua",
    "CART": "bạn vừa thêm vào giỏ",
    "WISHLIST": "bạn vừa thích",
    "VIEW": "bạn vừa xem",
}


@dataclass(frozen=True)
class Suggestion:
    """1 sản phẩm gợi ý: điểm, lý do ngắn cho người mua và nguồn gợi ý."""

    product_id: int
    score: float
    reason: str
    source: str


def short_name(name: str) -> str:
    """Rút gọn tên sản phẩm (tên từ Tiki thường rất dài) để đặt trong câu lý do."""
    name = " ".join(name.split())
    if len(name) <= MAX_NAME_LENGTH:
        return name
    cut = name[:MAX_NAME_LENGTH].rsplit(" ", 1)[0]
    return cut.rstrip(" ,.-–") + "…"


class Recommender:
    """Mô hình đã dựng sẵn: vector sản phẩm, ma trận "hay mua cùng" và số liệu bán hàng."""

    def __init__(
        self,
        vectors: ItemVectors,
        items: Sequence[CatalogItem],
        stats: dict[int, ProductStats],
        copurchase: sparse.csr_matrix,
        brand_names: dict[int, str],
        weights: Weights = DEFAULT_WEIGHTS,
        sales: SalesHistory | None = None,
    ) -> None:
        """Ghép các phần đã dựng; `items` theo đúng thứ tự hàng của `vectors`. Không có `sales`
        thì điểm đang bán chạy bằng 0."""
        self.vectors = vectors
        self.copurchase = copurchase
        self.brand_names = brand_names
        self.weights = weights
        self.sales = sales
        self._trend_memo: tuple[tuple[datetime, float], np.ndarray] | None = None
        self.names = [item.name for item in items]
        size = len(items)
        self.available = np.zeros(size, dtype=bool)
        self.prices = np.array([item.price for item in items], dtype=np.float64)
        sold = np.zeros(size)
        self.ratings = np.full(size, np.nan)
        rating_sums = np.zeros(size)
        rating_counts = np.zeros(size)
        for row, item in enumerate(items):
            stat = stats.get(item.id)
            if stat is None:
                continue
            self.available[row] = stat.in_stock
            self.prices[row] = stat.price
            sold[row] = stat.sold
            if stat.rating is not None:
                self.ratings[row] = stat.rating
                rating_counts[row] = stat.rating_count
                rating_sums[row] = stat.rating * stat.rating_count
        top = np.log1p(sold.max()) if sold.size else 0
        self.popularity = np.log1p(sold) / top if top > 0 else np.zeros(size)
        self.cheapness = self._cheapness_within_category()
        # Điểm đánh giá đã "co" về trung bình chung (trung bình Bayes): sản phẩm có 1 đánh giá 5
        # sao không vượt sản phẩm có 30 đánh giá trung bình 4,8.
        reviews = rating_counts.sum()
        prior = rating_sums.sum() / reviews if reviews > 0 else 0.0
        self.adjusted_ratings = (rating_sums + RATING_PRIOR_WEIGHT * prior) / (
            rating_counts + RATING_PRIOR_WEIGHT
        )
        self.adjusted_ratings[rating_counts == 0] = 0.0

    def _cheapness_within_category(self) -> np.ndarray:
        """1 với sản phẩm rẻ nhất danh mục, 0 với đắt nhất (theo thứ hạng giá)."""
        cheapness = np.zeros(len(self.prices))
        for category in np.unique(self.vectors.category_ids):
            rows = np.flatnonzero(self.vectors.category_ids == category)
            if len(rows) == 1:
                cheapness[rows] = 1.0
                continue
            order = rows[np.argsort(self.prices[rows], kind="stable")]
            cheapness[order] = 1.0 - np.arange(len(rows)) / (len(rows) - 1)
        return cheapness

    def trending(self, now: datetime | None) -> np.ndarray:
        """trend(j) tại thời điểm gợi ý `now`; 0 nếu không biết thời điểm hoặc không có lượt bán.

        Nhớ kết quả của lần gọi trước: các khách cùng thời điểm (đánh giá cắt theo thời gian) chỉ
        tính 1 lần. Đọc và thay bản nhớ bằng 1 phép gán, nên 2 luồng gọi cùng lúc vẫn nhận đúng
        mảng của thời điểm mình hỏi."""
        if self.sales is None or now is None:
            return np.zeros(len(self.prices))
        key = (now, self.weights.trending_half_life_days)
        memo = self._trend_memo
        if memo is None or memo[0] != key:
            memo = (key, self.sales.trending(now, key[1]))
            self._trend_memo = memo
        return memo[1]

    # ---- điểm cộng theo kiểu người mua, công thức (1.4) ----

    def style_bonus(self, survey: SurveyProfile | None) -> np.ndarray:
        """b(j) theo kiểu người mua: tiết kiệm (giá rẻ trong danh mục), thương hiệu (đúng thương
        hiệu yêu thích), cầu toàn (điểm đánh giá), xu hướng (bán chạy). Không có thì 0."""
        size = len(self.prices)
        if survey is None or survey.style is None:
            return np.zeros(size)
        if survey.style == "SAVER":
            return self.cheapness
        if survey.style == "BRAND":
            if survey.brand_id is None:
                return np.zeros(size)
            return (self.vectors.brand_ids == survey.brand_id).astype(np.float64)
        if survey.style == "QUALITY":
            return np.clip((self.adjusted_ratings - 3.0) / 2.0, 0.0, 1.0)
        if survey.style == "TREND":
            return self.popularity
        return np.zeros(size)

    def _allowed(self, excluded: Collection[int], survey: SurveyProfile | None) -> np.ndarray:
        """Sản phẩm được gợi ý: còn hàng, không nằm trong `excluded`, đạt điểm tối thiểu (nếu có)."""
        allowed = self.available.copy()
        for product_id in excluded:
            row = self.vectors.index.get(product_id)
            if row is not None:
                allowed[row] = False
        if survey is not None and survey.style == "QUALITY" and survey.min_rating is not None:
            allowed &= ~(self.ratings < survey.min_rating)  # chưa có đánh giá (NaN) vẫn được
        return allowed

    # ---- trang chủ ----

    def for_account(self, signals: UserSignals, limit: int = HOME_LIMIT) -> list[Suggestion]:
        """Danh sách gợi ý trang chủ của 1 tài khoản theo thứ tự nguồn BR-38 (rỗng = bán chạy)."""
        weights = self._interaction_weights(signals)
        if signals.has_enough_behaviour and weights:
            return self._behaviour(signals, weights, limit)
        if signals.survey is not None:
            return self.for_survey(signals.survey, limit, excluded=signals.purchased | signals.cart)
        return []

    def _interaction_weights(self, signals: UserSignals) -> dict[int, tuple[float, str]]:
        """Hàng ma trận → (trọng số, loại) của loại tương tác mạnh nhất với sản phẩm đó.

        Trọng số theo loại nhân hệ số gần đây của lần tương tác muộn nhất với sản phẩm.
        """
        weights: dict[int, tuple[float, str]] = {}
        for kind, products in (
            ("VIEW", signals.viewed),
            ("WISHLIST", signals.wishlist),
            ("CART", signals.cart),
            ("PURCHASE", signals.purchased),
        ):
            for product_id in products:
                row = self.vectors.index.get(product_id)
                if row is not None:
                    factor = self._recency(signals.now, signals.last_at.get(product_id))
                    weights[row] = (INTERACTION_WEIGHTS[kind] * factor, kind)
        return weights

    def _recency(self, now: datetime | None, at: datetime | None) -> float:
        """1 + β · 0,5^(tuổi / chu kỳ bán rã); thiếu thời điểm thì 1 (không ưu tiên)."""
        if now is None or at is None or self.weights.recent_boost <= 0:
            return 1.0
        age_days = max((now - at).total_seconds(), 0.0) / 86_400
        return 1.0 + self.weights.recent_boost * 0.5 ** (
            age_days / self.weights.recent_half_life_days
        )

    def _behaviour(
        self, signals: UserSignals, weights: dict[int, tuple[float, str]], limit: int
    ) -> list[Suggestion]:
        """Gợi ý theo hành vi: P(u), hay mua cùng, bán chạy, đang bán chạy và điểm cộng theo kiểu
        người mua."""
        rows = np.fromiter(weights, dtype=np.int64)
        w = np.array([weights[r][0] for r in rows])
        matrix = self.vectors.matrix
        profile = (sparse.csr_matrix(w) @ matrix[rows]).toarray().ravel()
        total = w.sum()
        survey = signals.survey
        survey_vector = (
            None
            if survey is None
            else self.vectors.survey_vector(
                survey.category_ids,
                survey.brand_id,
                survey.price_min,
                survey.price_max,
                survey.keywords,
            )
        )
        if survey_vector is not None:
            profile = profile + SURVEY_WEIGHT * survey_vector
            total += SURVEY_WEIGHT
        profile /= total
        norm = np.linalg.norm(profile)
        content = matrix @ (profile / norm) if norm > 0 else np.zeros(matrix.shape[0])

        bought_together = (sparse.csr_matrix(w) @ self.copurchase[rows]).toarray().ravel() / total
        top = bought_together.max()
        copurchase = bought_together / top if top > 0 else bought_together

        trend = self.trending(signals.now) if self.weights.trending > 0 else np.zeros(len(content))
        score = (
            self.weights.content * content
            + self.weights.copurchase * copurchase
            + self.weights.popular * self.popularity
            + self.weights.trending * trend
            + self.weights.style * self.style_bonus(survey)
        )
        allowed = self._allowed(signals.purchased | signals.cart, survey)
        if survey is not None and survey.gender is not None:
            # Khách đã chọn đồ nam/nữ ở khảo sát: chỉ gợi ý đồ giới tính kia khi khách đã mua, thêm
            # vào giỏ hoặc yêu thích đồ đó (vd mua quà). Lượt xem không tính: khách có thể chỉ bấm
            # thử một gợi ý.
            other = FEMALE if survey.gender == MALE else MALE
            chosen = [
                self.vectors.index[p]
                for p in signals.purchased | signals.cart | signals.wishlist
                if p in self.vectors.index
            ]
            if not np.any(self.vectors.genders[chosen] == other):
                allowed &= self._fits_gender(survey.gender)
        picked = self._pick(score, allowed, limit)

        result = []
        for row in picked:
            reason = self._behaviour_reason(
                row, rows, w, weights, (content[row], copurchase[row], trend[row]), signals
            )
            result.append(
                Suggestion(int(self.vectors.ids[row]), float(score[row]), reason, SOURCE_BEHAVIOUR)
            )
        return result

    def _behaviour_reason(
        self,
        row: int,
        anchors: np.ndarray,
        anchor_weights: np.ndarray,
        weights: dict[int, tuple[float, str]],
        parts: tuple[float, float, float],
        signals: UserSignals,
    ) -> str:
        """Lý do theo phần đóng góp nhiều nhất vào điểm của sản phẩm `row`: đang bán chạy, hoặc
        sản phẩm đã tương tác gần với nó nhất. `parts` là (nội dung, hay mua cùng, đang bán chạy)."""
        content, copurchase, trend = parts
        if row in weights:
            # Chính sản phẩm khách đã xem hoặc đã thích (đã mua, trong giỏ thì không gợi ý).
            return _SELF_PHRASES[weights[row][1]]
        if trend >= TRENDING_REASON_MIN and self.weights.trending * trend >= max(
            self.weights.content * content, self.weights.copurchase * copurchase
        ):
            return TRENDING_REASON
        if copurchase > 0.3 and (
            self.weights.copurchase * copurchase >= 0.5 * self.weights.content * content
        ):
            links = anchor_weights * self.copurchase[anchors, row].toarray().ravel()
            anchor = int(anchors[int(np.argmax(links))])
            return f"Khách mua {short_name(self.names[anchor])} cũng hay mua sản phẩm này"
        matrix = self.vectors.matrix
        similarity = (matrix[anchors] @ matrix[row].T).toarray().ravel()
        anchor = int(anchors[int(np.argmax(anchor_weights * similarity))])
        kind = weights[anchor][1]
        phrases = _RECENT_KIND_PHRASES if self._is_recent(signals, anchor) else _KIND_PHRASES
        return f"Giống {short_name(self.names[anchor])} {phrases[kind]}"

    def _is_recent(self, signals: UserSignals, row: int) -> bool:
        """Khách tương tác với sản phẩm ở hàng `row` trong RECENT_PHRASE_HOURS giờ qua."""
        at = signals.last_at.get(int(self.vectors.ids[row]))
        if signals.now is None or at is None:
            return False
        return (signals.now - at).total_seconds() <= RECENT_PHRASE_HOURS * 3600

    # ---- theo khảo sát ----

    def for_survey(
        self,
        survey: SurveyProfile,
        limit: int = HOME_LIMIT,
        excluded: Collection[int] = (),
    ) -> list[Suggestion]:
        """Gợi ý theo sở thích khảo sát: cos(s(u), v(j)) + w_s · pop(j) + λ · b(j).

        Chỉ gợi ý đồ đúng giới tính khách chọn (và đồ dùng chung). Thứ tự theo tầng, trong mỗi tầng
        theo điểm: trong danh mục quan tâm và trong khoảng giá, trong danh mục nhưng ngoài khoảng
        giá, rồi mới tới danh mục khác (lấp khi danh mục quan tâm không đủ sản phẩm).
        """
        vector = self.vectors.survey_vector(
            survey.category_ids,
            survey.brand_id,
            survey.price_min,
            survey.price_max,
            survey.keywords,
        )
        if vector is None:
            return []
        bonus = self.style_bonus(survey)
        score = (
            self.vectors.matrix @ vector
            + self.weights.survey_popular * self.popularity
            + self.weights.style * bonus
        )
        ranking = score + TIE_BREAK * self.popularity
        allowed = self._allowed(excluded, survey) & self._fits_gender(survey.gender)
        in_budget = self._in_budget(survey)
        in_interest = np.isin(self.vectors.category_ids, survey.category_ids)
        picked: list[int] = []
        for tier in (
            in_interest & in_budget,
            in_interest & ~in_budget,
            ~in_interest & in_budget,
            ~in_interest & ~in_budget,
        ):
            if len(picked) >= limit:
                break
            picked += self._pick(ranking, allowed & tier, limit - len(picked))
        return [
            Suggestion(
                int(self.vectors.ids[row]),
                float(score[row]),
                self._survey_reason(row, survey, bonus[row]),
                SOURCE_SURVEY,
            )
            for row in picked
        ]

    def _fits_gender(self, gender: str | None) -> np.ndarray:
        """Sản phẩm dùng chung hoặc đúng giới tính `gender`; None thì mọi sản phẩm."""
        genders = self.vectors.genders
        if gender is None:
            return np.ones(len(genders), dtype=bool)
        return (genders == "") | (genders == gender)

    def _in_budget(self, survey: SurveyProfile) -> np.ndarray:
        """Sản phẩm có giá thấp nhất nằm trong khoảng giá khách chọn ở khảo sát."""
        in_budget = self.prices >= survey.price_min
        if survey.price_max is not None:
            in_budget &= self.prices <= survey.price_max
        return in_budget

    def _survey_reason(self, row: int, survey: SurveyProfile, bonus: float) -> str:
        """Lý do theo kiểu người mua khi điểm cộng đáng kể, còn lại theo sở thích chung."""
        if survey.style == "BRAND" and bonus > 0:
            brand = self.brand_names.get(int(self.vectors.brand_ids[row]))
            if brand:
                return f"Thương hiệu bạn yêu thích: {brand}"
        if survey.style == "QUALITY" and not np.isnan(self.ratings[row]) and bonus > 0:
            rating = f"{self.ratings[row]:.1f}".replace(".", ",")
            return f"Được đánh giá {rating} sao"
        if survey.style == "SAVER" and bonus >= 0.5:
            return "Giá tốt trong nhóm sản phẩm bạn quan tâm"
        if survey.style == "TREND" and bonus >= 0.5:
            return "Đang bán chạy trong nhóm sản phẩm bạn quan tâm"
        return "Hợp với sở thích bạn chọn trong khảo sát"

    # ---- trang chi tiết ----

    def related(
        self, product_id: int, limit: int = RELATED_LIMIT, excluded: Collection[int] = ()
    ) -> list[Suggestion]:
        """Sản phẩm liên quan (US-12). KeyError nếu sản phẩm không còn bán."""
        row = self.vectors.index[product_id]
        matrix = self.vectors.matrix
        content = np.asarray((matrix @ matrix[row].T).todense()).ravel()
        together = self.copurchase[row].toarray().ravel()
        top = together.max()
        copurchase = together / top if top > 0 else together
        score = RELATED_CONTENT * content + RELATED_COPURCHASE * copurchase
        allowed = self._allowed({product_id, *excluded}, None)
        ranking = score + TIE_BREAK * self.popularity
        order = np.argsort(-np.where(allowed, ranking, -np.inf), kind="stable")
        picked = [int(r) for r in order[:limit] if allowed[r]]
        return [
            Suggestion(
                int(self.vectors.ids[r]),
                float(score[r]),
                self._related_reason(row, r, content[r], copurchase[r]),
                SOURCE_RELATED,
            )
            for r in picked
        ]

    # ---- trang giỏ hàng ----

    def bought_together(
        self, product_ids: Collection[int], limit: int = RELATED_LIMIT
    ) -> list[Suggestion]:
        """ "Thường được mua cùng" các sản phẩm trong giỏ (chức năng 20): cộng độ "hay mua cùng"
        với từng sản phẩm trong giỏ. Chỉ lấy sản phẩm thật sự có khách mua cùng (điểm > 0), nên
        danh sách có thể ít hơn `limit` hoặc rỗng; không lấp bằng sản phẩm khác."""
        rows = [self.vectors.index[p] for p in product_ids if p in self.vectors.index]
        if not rows or limit <= 0:
            return []
        together = self.copurchase[rows].toarray()  # giỏ × sản phẩm
        score = together.sum(axis=0)
        anchors = together.argmax(axis=0)  # sản phẩm trong giỏ đóng góp nhiều nhất
        allowed = self._allowed(set(product_ids), None) & (score > 0)
        ranking = score + TIE_BREAK * self.popularity
        order = np.argsort(-np.where(allowed, ranking, -np.inf), kind="stable")
        picked = [int(r) for r in order[:limit] if allowed[r]]
        return [
            Suggestion(
                int(self.vectors.ids[r]),
                float(score[r]),
                f"Khách mua {short_name(self.names[rows[anchors[r]]])} cũng hay mua",
                SOURCE_RELATED,
            )
            for r in picked
        ]

    def _related_reason(self, row: int, other: int, content: float, copurchase: float) -> str:
        """Lý do của sản phẩm liên quan, theo phần đóng góp lớn nhất."""
        if copurchase > 0.3 and RELATED_COPURCHASE * copurchase >= 0.5 * RELATED_CONTENT * content:
            return "Thường được mua cùng sản phẩm này"
        same_category = self.vectors.category_ids[row] == self.vectors.category_ids[other]
        brand = int(self.vectors.brand_ids[other])
        same_brand = brand >= 0 and brand == self.vectors.brand_ids[row]
        brand_name = self.brand_names.get(brand, "")
        if same_category and same_brand and brand_name:
            return f"Cùng danh mục và thương hiệu {brand_name}"
        if same_brand and brand_name:
            return f"Cùng thương hiệu {brand_name}"
        if same_category:
            return "Cùng danh mục với sản phẩm bạn đang xem"
        return "Tương tự sản phẩm bạn đang xem"

    # ---- chọn danh sách ----

    def _pick(self, score: np.ndarray, allowed: np.ndarray, limit: int) -> list[int]:
        """Chọn lần lượt sản phẩm điểm cao nhất, trừ điểm sản phẩm trùng danh mục đã chọn."""
        candidates = np.flatnonzero(allowed)
        if candidates.size == 0 or limit <= 0:
            return []
        pool = candidates[np.argsort(-score[candidates], kind="stable")[: max(limit * 15, 100)]]
        categories = self.vectors.category_ids[pool]
        adjusted = score[pool].astype(np.float64).copy()
        taken = np.zeros(pool.size, dtype=bool)
        picked: list[int] = []
        for _ in range(min(limit, pool.size)):
            best = int(np.argmax(np.where(taken, -np.inf, adjusted)))
            taken[best] = True
            picked.append(int(pool[best]))
            adjusted[categories == categories[best]] -= self.weights.diversity
        return picked
