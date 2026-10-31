"""API gợi ý tính ngay cho backend gọi (US-12, US-23, giỏ hàng); trình duyệt không gọi thẳng
ml-service.

Backend chờ tối đa 500ms, quá hạn hay lỗi thì tự dùng sản phẩm bán chạy (BR-22).
"""

from typing import Literal

from fastapi import APIRouter, HTTPException, Query, Request
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from app.recommendation.data import SurveyProfile
from app.recommendation.model import RELATED_LIMIT, Recommender, Suggestion

router = APIRouter(prefix="/recommendations", tags=["recommendations"])

SURVEY_LIMIT = 8


class CamelModel(BaseModel):
    """Đọc/ghi JSON theo camelCase như backend Java."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SuggestionResponse(CamelModel):
    """1 sản phẩm gợi ý."""

    product_id: int
    score: float
    reason: str
    source: str

    @classmethod
    def of(cls, suggestion: Suggestion) -> "SuggestionResponse":
        """Đổi từ kết quả của mô hình."""
        return cls(
            product_id=suggestion.product_id,
            score=round(suggestion.score, 6),
            reason=suggestion.reason,
            source=suggestion.source,
        )


class SurveyRecommendationRequest(CamelModel):
    """Hồ sơ khảo sát backend đã tính từ câu trả lời (SurveyService)."""

    category_ids: list[int] = Field(min_length=1, max_length=50)
    brand_id: int | None = None
    price_min: int = Field(default=0, ge=0)
    price_max: int | None = Field(default=None, ge=0)
    style: Literal["SAVER", "BRAND", "QUALITY", "TREND"] | None = None
    min_rating: float | None = Field(default=None, ge=0, le=5)
    exclude_product_ids: list[int] = Field(default_factory=list, max_length=200)
    limit: int = Field(default=SURVEY_LIMIT, ge=1, le=50)


def _model(request: Request) -> Recommender:
    """Mô hình hiện tại; 503 nếu chưa có sản phẩm nào để gợi ý."""
    with request.app.state.engine.connect() as conn:
        model = request.app.state.model_cache.get(conn)
    if model is None:
        raise HTTPException(status_code=503, detail="Chưa có sản phẩm nào để gợi ý")
    return model


@router.get("/related/{product_id}", response_model_by_alias=True)
def related(
    product_id: int,
    request: Request,
    limit: int = Query(default=RELATED_LIMIT, ge=1, le=20),
) -> list[SuggestionResponse]:
    """Sản phẩm liên quan của sản phẩm đang xem (US-12): giống về nội dung, hay mua cùng."""
    model = _model(request)
    try:
        suggestions = model.related(product_id, limit)
    except KeyError:
        raise HTTPException(status_code=404, detail="Không có sản phẩm này") from None
    return [SuggestionResponse.of(s) for s in suggestions]


class BoughtTogetherRequest(CamelModel):
    """Các sản phẩm trong giỏ hàng."""

    product_ids: list[int] = Field(min_length=1, max_length=50)
    limit: int = Field(default=RELATED_LIMIT, ge=1, le=20)


@router.post("/bought-together", response_model_by_alias=True)
def bought_together(body: BoughtTogetherRequest, request: Request) -> list[SuggestionResponse]:
    """ "Thường được mua cùng" các sản phẩm trong giỏ (chức năng 20); rỗng nếu chưa ai mua cùng."""
    model = _model(request)
    return [SuggestionResponse.of(s) for s in model.bought_together(body.product_ids, body.limit)]


@router.post("/survey", response_model_by_alias=True)
def survey(body: SurveyRecommendationRequest, request: Request) -> list[SuggestionResponse]:
    """Gợi ý theo câu trả lời khảo sát, công thức (1.4) (US-23, kể cả khách chưa đăng nhập)."""
    if body.price_max is not None and body.price_max < body.price_min:
        raise HTTPException(status_code=422, detail="priceMax phải lớn hơn hoặc bằng priceMin")
    profile = SurveyProfile(
        category_ids=tuple(body.category_ids),
        brand_id=body.brand_id,
        price_min=body.price_min,
        price_max=body.price_max,
        style=body.style,
        min_rating=body.min_rating,
    )
    model = _model(request)
    suggestions = model.for_survey(profile, body.limit, excluded=set(body.exclude_product_ids))
    return [SuggestionResponse.of(s) for s in suggestions]
