"""Kiểm tra script dựng danh mục mẫu từ bộ Tiki trên vài dòng dữ liệu tự tạo."""

from scripts.build_tiki_catalog import (
    CATEGORIES,
    brand_of,
    clean_description,
    clean_name,
    fold,
    is_near_duplicate,
    select,
    stock_of,
    to_product,
    words,
)

MEN_SPORT = next(c for c in CATEGORIES if c.name == "Giày thể thao nam")
WOMEN_WALLET = next(c for c in CATEGORIES if c.name == "Ví nữ")


def row(id_, name, category, file="men_shoes", sold="10", price="150000", brand="OEM"):
    return {
        "id": id_,
        "name": name,
        "description": "Mô tả ngắn",
        "price": price,
        "brand": brand,
        "review_count": "3",
        "rating_average": "4.5",
        "category": category,
        "quantity_sold": sold,
        "_file": file,
    }


def test_fold_removes_vietnamese_marks():
    assert fold("  Giầy ĐẸP  Đi Mưa ") == "giay dep di mua"


def test_clean_name_drops_leading_tags_and_shouting():
    assert clean_name("[Ảnh Thật] Dép Quai Ngang") == "Dép Quai Ngang"
    assert clean_name("GIÀY TÂY NAM CÔNG SỞ") == "Giày tây nam công sở"


def test_clean_description_cuts_truncated_text_at_word():
    long_text = "chữ " * 60
    assert clean_description(long_text).endswith("chữ…")
    assert clean_description("   ") is None
    assert clean_description("Mô tả ngắn") == "Mô tả ngắn"


def test_clean_description_splits_glued_lines_but_not_brand_names():
    text = "Đế chống trơn trượtMàu sắc trẻ trung.Hợp iPhone, dày 3.5cm"
    assert (
        clean_description(text) == "Đế chống trơn trượt. Màu sắc trẻ trung. Hợp iPhone, dày 3.5cm"
    )


def test_brand_of_treats_oem_as_no_brand():
    assert brand_of("\tOEM") is None
    assert brand_of("No Brand") is None
    assert brand_of(" Biti's ") == "Biti's"


def test_stock_is_fixed_per_product_and_option():
    # Giá trị cố định theo SHA-256: chạy lại script ở máy nào cũng ra cùng tồn kho.
    assert stock_of("123", "40") == 7
    assert stock_of("123", None) == 37


def test_near_duplicate_by_shared_words():
    chosen = [words("Giày sneaker thể thao nam buộc dây siêu nhẹ V266")]
    assert is_near_duplicate("Giày sneaker thể thao nam thời trang buộc dây siêu nhẹ 266", chosen)
    assert not is_near_duplicate("Giày da nam oxford công sở", chosen)


def test_shoe_gets_size_tier_and_one_variant_per_size():
    product = to_product(MEN_SPORT, row("7", "Giày chạy bộ", "Giày thể thao nam cổ thấp"))
    assert product["variationName1"] == "Kích cỡ"
    assert [v["option1Value"] for v in product["variants"]] == ["39", "40", "41", "42", "43"]
    assert product["variants"][0]["skuCode"] == "TK7-39"
    assert product["brand"] is None


def test_wallet_has_no_tier_and_one_variant():
    product = to_product(WOMEN_WALLET, row("8", "Ví ngắn nữ", "Ví ngắn", file="women_bags"))
    assert product["variationName1"] is None
    assert len(product["variants"]) == 1
    assert product["variants"][0]["skuCode"] == "TK8"


def test_select_keeps_best_sellers_and_drops_bad_rows():
    rows = [
        row("1", "Giày thể thao nam A", "Giày thể thao nam cổ thấp", sold="5"),
        row("2", "Giày thể thao nam đế cao B", "Giày thể thao nam cổ cao", sold="50"),
        row("3", "Giày thể thao C chưa bán", "Giày thể thao nam cổ thấp", sold="0"),
        row("4", "Giày thể thao D giá 0", "Giày thể thao nam cổ thấp", price="0"),
        row("5", "Combo 2 giày thể thao", "Giày thể thao nam cổ thấp", sold="99"),
        row("6", "Mặt nạ dưỡng da", "Giày thể thao nam cổ thấp", sold="99"),
        row("7", "Giày thể thao chung chung", "Root", sold="99"),
        row("8", "Ví ngắn nữ da", "Ví ngắn", file="women_bags", sold="20"),
        row("9", "Combo 10 bao đựng thẻ", "Ví ngắn", file="women_bags", sold="999"),
    ]

    products = select(rows, per_category=18)

    names = [(p["category"], p["tikiId"]) for p in products]
    assert names == [("Giày thể thao nam", 2), ("Giày thể thao nam", 1), ("Ví nữ", 8)]
