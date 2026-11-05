"""Test suy giới tính sản phẩm từ tên danh mục và tên sản phẩm."""

import pytest

from app.recommendation.gender import FEMALE, MALE, product_gender


@pytest.mark.parametrize(
    ("category", "name", "expected"),
    [
        ("Giày thể thao nam", "Giày chạy bộ Biti's", MALE),
        ("Sandal nữ", "Sandal quai mảnh", FEMALE),
        ("Dép nữ", "Dép quai ngang nam nữ siêu nhẹ", None),
        ("Balo thời trang", "Balo Nam canvas có ngăn laptop", MALE),
        ("Balo thời trang", "Balo nữ mini dễ thương", FEMALE),
        ("Balo thời trang", "Balo Unisex đi học", None),
        ("Kính mát", "Kính râm cho phái mạnh", MALE),
        ("Ví nữ", "Ví cầm tay khóa nam châm", FEMALE),
        ("Vali", "Vali nhựa sản xuất tại Việt Nam", None),
        ("Khăn", "Khăn lụa", None),
        ("Nón nam", None, MALE),
        (None, None, None),
    ],
)
def test_product_gender(category, name, expected):
    assert product_gender(category, name) == expected


def test_decomposed_text_is_recognised():
    assert product_gender("Kính mát", "Kính n\u01b0\u0303 thời trang") == FEMALE
