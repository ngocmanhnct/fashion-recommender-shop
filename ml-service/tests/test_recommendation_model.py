"""Test mô hình gợi ý không cần CSDL: vector sản phẩm, hay mua cùng, trộn nguồn, lý do."""

import numpy as np
import pytest

from app.recommendation.copurchase import build_copurchase
from app.recommendation.data import ProductStats, SurveyProfile, UserSignals
from app.recommendation.features import CatalogItem, ItemVectors, normalize_text
from app.recommendation.model import (
    SOURCE_BEHAVIOUR,
    SOURCE_SURVEY,
    Recommender,
    Weights,
    short_name,
)

SHOES, BAGS, SOCKS = 1, 2, 3
BITIS, ADIDAS = 10, 11
BRANDS = {BITIS: "Biti's", ADIDAS: "Adidas"}

ITEMS = [
    CatalogItem(1, SHOES, BITIS, "Giày thể thao nam Biti's Hunter chạy bộ", "êm chân", 500_000),
    CatalogItem(2, SHOES, BITIS, "Giày thể thao nam Biti's Hunter đi phố", "êm chân", 550_000),
    CatalogItem(3, SHOES, ADIDAS, "Giày thể thao nam Adidas chạy bộ", "nhẹ", 1_500_000),
    CatalogItem(4, BAGS, None, "Túi xách nữ da công sở", "túi xách da", 400_000),
    CatalogItem(5, BAGS, None, "Túi xách nữ vải canvas", "túi xách vải đi học", 150_000),
    CatalogItem(6, SOCKS, None, "Vớ nam cổ ngắn cotton", "vớ thể thao", 30_000),
    CatalogItem(7, SOCKS, None, "Vớ nam cổ cao", "vớ thể thao cotton", 40_000),
    CatalogItem(8, SHOES, ADIDAS, "Giày thể thao nam Adidas đi phố", "nhẹ", 1_200_000),
]

STATS = {
    1: ProductStats(500_000, True, 40, 4.0, 10),
    2: ProductStats(550_000, True, 10, 4.9, 30),
    3: ProductStats(1_500_000, True, 5, 3.0, 2),
    4: ProductStats(400_000, True, 20, None),
    5: ProductStats(150_000, True, 1, 5.0, 1),
    6: ProductStats(30_000, True, 60, None),
    7: ProductStats(40_000, True, 3, None),
    8: ProductStats(1_200_000, False, 8, None),  # hết hàng
}

# Khách 100–104 mua giày 1 cùng vớ 6; khách 105 mua túi 4 và túi 5.
PAIRS = [(100, 1), (100, 6), (101, 1), (101, 6), (102, 1), (102, 6), (103, 1), (103, 6)]
PAIRS += [(104, 2), (104, 6), (105, 4), (105, 5)]


@pytest.fixture(scope="module")
def vectors():
    return ItemVectors(ITEMS)


@pytest.fixture
def model(vectors):
    copurchase = build_copurchase(PAIRS, vectors.index, len(ITEMS))
    return Recommender(vectors, ITEMS, STATS, copurchase, BRANDS)


def similarity(vectors, a, b):
    m = vectors.matrix
    return float((m[vectors.index[a]] @ m[vectors.index[b]].T).toarray()[0, 0])


# ---- vector sản phẩm ----


def test_vectors_have_unit_length_and_similar_products_are_closer(vectors):
    norms = np.sqrt(np.asarray(vectors.matrix.multiply(vectors.matrix).sum(axis=1)).ravel())
    assert np.allclose(norms, 1.0)
    assert similarity(vectors, 1, 2) > similarity(vectors, 1, 3) > similarity(vectors, 1, 4)
    assert similarity(vectors, 1, 1) == pytest.approx(1.0)


def test_normalize_text_composes_decomposed_vietnamese():
    assert normalize_text("Giày") == "giày"
    assert normalize_text(None) == ""


def test_empty_catalog_is_rejected():
    with pytest.raises(ValueError):
        ItemVectors([])


def test_survey_vector_matches_chosen_category_and_price(vectors):
    vector = vectors.survey_vector([SHOES], BITIS, 400_000, 600_000)
    assert np.linalg.norm(vector) == pytest.approx(1.0)
    scores = vectors.matrix @ vector
    by_id = dict(zip(vectors.ids.tolist(), scores, strict=True))
    assert by_id[1] > by_id[3] > by_id[4]
    assert vectors.survey_vector([999], None, 0, None) is None


def test_price_buckets_give_neighbours_half_weight(vectors):
    weights = vectors.price_bucket_weights(np.log10(30_000))
    assert weights[0] == 1.0 and weights[1] == 0.5 and weights[2:].sum() == 0
    open_range = vectors._price_range_weights(0, None)
    assert open_range.sum() == len(open_range)


# ---- hay mua cùng ----


def test_copurchase_links_products_bought_by_the_same_customers(vectors):
    matrix = build_copurchase(PAIRS + [(106, 999)], vectors.index, len(ITEMS))
    i, j = vectors.index[1], vectors.index[6]
    assert matrix[i, j] > 0 and matrix[i, j] == pytest.approx(matrix[j, i])
    assert matrix.diagonal().sum() == 0
    # 1 khách chung bị co nhiều hơn 4 khách chung.
    assert matrix[vectors.index[4], vectors.index[5]] < matrix[i, j]


def test_copurchase_without_purchases_is_empty(vectors):
    assert build_copurchase([], vectors.index, len(ITEMS)).nnz == 0


# ---- trang chủ ----


def test_behaviour_excludes_bought_cart_and_out_of_stock(model):
    signals = UserSignals(1, purchased={1}, cart={2}, delivered_orders=1)
    result = model.for_account(signals, limit=6)
    ids = [s.product_id for s in result]
    assert ids and not {1, 2, 8} & set(ids)
    assert all(s.source == SOURCE_BEHAVIOUR for s in result)
    assert len(ids) == len(set(ids)) <= 6


def test_behaviour_reason_names_the_product_bought_together(model):
    result = model.for_account(UserSignals(1, purchased={1}, delivered_orders=1), limit=3)
    socks = next(s for s in result if s.product_id == 6)
    assert socks.reason.startswith("Khách mua Giày thể thao nam Biti's Hunter")
    assert socks.reason.endswith("cũng hay mua sản phẩm này")


def test_behaviour_reason_names_the_most_similar_viewed_product(model):
    signals = UserSignals(1, viewed={3, 4, 5, 6, 7})
    assert signals.has_enough_behaviour
    result = {s.product_id: s.reason for s in model.for_account(signals, limit=8)}
    # Sản phẩm đã xem vẫn được gợi ý lại; sản phẩm hết hàng thì không.
    assert set(result) == {1, 2, 3, 4, 5, 6, 7}
    assert result[2] == "Giống Giày thể thao nam Adidas chạy bộ bạn đã xem"
    assert result[3] == "Bạn đã xem sản phẩm này gần đây"
    assert result[1] == "Khách mua Vớ nam cổ ngắn cotton cũng hay mua sản phẩm này"


def test_wishlisted_product_can_come_back(model):
    signals = UserSignals(1, purchased={4}, wishlist={5}, delivered_orders=1)
    result = {s.product_id: s.reason for s in model.for_account(signals, limit=8)}
    assert result[5] == "Có trong danh sách yêu thích của bạn"


def test_survey_is_used_until_there_is_enough_behaviour(model):
    survey = SurveyProfile((SHOES,), ADIDAS, 1_000_000, None, "BRAND")
    signals = UserSignals(1, viewed={4}, survey=survey)
    result = model.for_account(signals, limit=3)
    assert result[0].product_id == 3
    assert result[0].reason == "Thương hiệu bạn yêu thích: Adidas"
    assert {s.source for s in result} == {SOURCE_SURVEY}


def test_behaviour_also_counts_the_survey(model):
    survey = SurveyProfile((SOCKS,), None, 0, None, "TREND")
    plain = model.for_account(UserSignals(1, purchased={4}, delivered_orders=1), limit=3)
    mixed = model.for_account(
        UserSignals(1, purchased={4}, delivered_orders=1, survey=survey), limit=3
    )
    assert 6 in [s.product_id for s in mixed]
    assert mixed != plain


def test_no_behaviour_and_no_survey_means_best_sellers_elsewhere(model):
    assert model.for_account(UserSignals(1, viewed={1, 2})) == []


def test_quality_style_skips_low_rated_products_and_explains_rating(model):
    survey = SurveyProfile((SHOES,), None, 0, None, "QUALITY", min_rating=4.0)
    result = model.for_survey(survey, limit=3)
    ids = [s.product_id for s in result]
    assert 3 not in ids  # 3,0 sao
    reasons = {s.product_id: s.reason for s in result}
    assert reasons[2] == "Được đánh giá 4,9 sao"


def test_saver_and_trend_reasons(model):
    saver = model.for_survey(SurveyProfile((BAGS,), None, 0, None, "SAVER"), limit=1)
    assert saver[0].product_id == 5
    assert saver[0].reason == "Giá tốt trong nhóm sản phẩm bạn quan tâm"
    trend = model.for_survey(SurveyProfile((SOCKS,), None, 0, None, "TREND"), limit=1)
    assert trend[0].product_id == 6
    assert trend[0].reason == "Đang bán chạy trong nhóm sản phẩm bạn quan tâm"
    plain = model.for_survey(SurveyProfile((SOCKS,), None, 0, None, None), limit=1)
    assert plain[0].reason == "Hợp với sở thích bạn chọn trong khảo sát"


def test_survey_keeps_to_the_budget_then_fills_from_outside(model):
    survey = SurveyProfile((SHOES,), None, 0, 600_000, "TREND")
    assert [s.product_id for s in model.for_survey(survey, limit=2)] == [1, 2]
    # Giày 3 (1,5 triệu) chỉ đứng sau mọi sản phẩm còn hàng trong khoảng giá; giày 8 hết hàng.
    ids = [s.product_id for s in model.for_survey(survey, limit=8)]
    assert ids[:2] == [1, 2] and ids[-1] == 3 and len(ids) == 7
    cheap = SurveyProfile((BAGS,), None, 200_000, None, "SAVER")
    assert model.for_survey(cheap, limit=1)[0].product_id == 4


def test_survey_with_unknown_categories_gives_nothing(model):
    assert model.for_survey(SurveyProfile((999,), None, 0, None, "SAVER")) == []


def test_brand_style_without_brand_has_no_bonus(model):
    bonus = model.style_bonus(SurveyProfile((SHOES,), None, 0, None, "BRAND"))
    assert not bonus.any()
    assert not model.style_bonus(None).any()


def test_diversity_spreads_categories(vectors):
    copurchase = build_copurchase(PAIRS, vectors.index, len(ITEMS))
    signals = UserSignals(1, purchased={1}, delivered_orders=1)
    spread = Recommender(vectors, ITEMS, STATS, copurchase, BRANDS, Weights(diversity=1.0))
    first_three = [s.product_id for s in spread.for_account(signals, limit=3)]
    categories = {ITEMS[vectors.index[p]].category_id for p in first_three}
    assert len(categories) == 3


# ---- trang chi tiết ----


def test_related_products(model):
    result = model.related(1)
    ids = [s.product_id for s in result]
    assert 1 not in ids and 8 not in ids and len(ids) == 4
    reasons = {s.product_id: s.reason for s in result}
    assert reasons[2] == "Cùng danh mục và thương hiệu Biti's"
    assert reasons[6] == "Thường được mua cùng sản phẩm này"
    assert model.related(3, limit=1)[0].reason in {
        "Cùng danh mục với sản phẩm bạn đang xem",
        "Cùng thương hiệu Adidas",
    }
    with pytest.raises(KeyError):
        model.related(999)


def test_related_reason_for_other_category(model):
    assert model._related_reason(model.vectors.index[4], model.vectors.index[6], 0.1, 0.0) == (
        "Tương tự sản phẩm bạn đang xem"
    )
    assert model._related_reason(model.vectors.index[1], model.vectors.index[3], 0.1, 0.0) == (
        "Cùng danh mục với sản phẩm bạn đang xem"
    )


def test_short_name():
    assert short_name("Vớ nam") == "Vớ nam"
    long = "Giày thể thao nam Biti's Hunter X phiên bản giới hạn màu đen trắng"
    assert short_name(long) == "Giày thể thao nam Biti's Hunter X phiên…"
    assert len(short_name(long)) <= 41
