"""Giới tính của sản phẩm, suy ra từ tên danh mục và tên sản phẩm (gợi ý theo khảo sát).

Cùng quy tắc với `ProductGender` của backend:
1. Tên sản phẩm ghi cả "nam" lẫn "nữ", hoặc "unisex": dùng chung (None).
2. Danh mục kết thúc bằng " nam" / " nữ": theo danh mục.
3. Danh mục dùng chung (Balo, Kính mát...): tên chỉ ghi "nam" (hoặc "phái mạnh") là đồ nam, chỉ ghi
   "nữ" (hoặc "phái đẹp") là đồ nữ, còn lại dùng chung.
Bỏ các cụm có chữ "nam" không chỉ giới tính: "Việt Nam", "nam châm", "miền Nam".
"""

import re
import unicodedata

MALE = "MALE"
FEMALE = "FEMALE"

_NOT_GENDER = re.compile(r"việt nam|nam châm|miền nam")
_MALE = re.compile(r"\bnam\b|phái mạnh")
_FEMALE = re.compile(r"\bnữ\b|phái đẹp")
_UNISEX = re.compile(r"\bunisex\b")


def _normalize(value: str | None) -> str:
    return unicodedata.normalize("NFC", value or "").lower()


def product_gender(category_name: str | None, product_name: str | None) -> str | None:
    """MALE, FEMALE, hoặc None nếu sản phẩm dùng chung."""
    name = _NOT_GENDER.sub(" ", _normalize(product_name))
    male = bool(_MALE.search(name))
    female = bool(_FEMALE.search(name))
    if _UNISEX.search(name) or (male and female):
        return None
    category = _normalize(category_name)
    if category.endswith(" nam"):
        return MALE
    if category.endswith(" nữ"):
        return FEMALE
    if male:
        return MALE
    return FEMALE if female else None
