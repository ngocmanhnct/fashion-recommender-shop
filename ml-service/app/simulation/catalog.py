"""Đọc danh mục sản phẩm đang bán từ CSDL và gắn các thuộc tính ẩn dùng khi mô phỏng."""

import math
import random
import unicodedata
from bisect import bisect_left
from dataclasses import dataclass, field

from sqlalchemy import Connection, text

MALE_SUFFIX = " nam"
FEMALE_SUFFIX = " nữ"


def category_gender(name: str) -> str | None:
    """Danh mục chỉ dành cho nam (MALE), nữ (FEMALE) hay dùng chung (None), theo đuôi tên.

    Cùng quy tắc với SurveyService của backend: tên kết thúc bằng " nam" / " nữ".
    """
    normalized = unicodedata.normalize("NFC", name).lower()
    if normalized.endswith(MALE_SUFFIX):
        return "MALE"
    if normalized.endswith(FEMALE_SUFFIX):
        return "FEMALE"
    return None


def variant_label(option1: str | None, option2: str | None) -> str | None:
    """Nhãn phân loại lưu ở dòng đơn, giống ProductVariant.labelOf của backend ("Đen – 42")."""
    if option1 is None:
        return None
    return option1 if option2 is None else f"{option1} – {option2}"


@dataclass
class Variant:
    """Một biến thể đang bán."""

    id: int
    price: int
    stock: int
    label: str | None


@dataclass
class Product:
    """Sản phẩm đang bán, kèm 2 thuộc tính ẩn chỉ dùng để mô phỏng.

    `popularity`: độ "hot" chung của sản phẩm (phân phối log-chuẩn: vài sản phẩm rất được ưa
    chuộng, đa số ít người mua), tạo ra nhóm bán chạy. `quality`: chất lượng thật (thang 1-5),
    quyết định điểm khách đánh giá và được khách kiểu "cầu toàn" ưu tiên.
    """

    id: int
    name: str
    category_id: int
    brand_id: int | None
    variants: list[Variant]
    popularity: float = 1.0
    quality: float = 4.0
    price_rank: float = 0.5  # vị trí giá trong danh mục: 0 = rẻ nhất, 1 = đắt nhất

    @property
    def price(self) -> int:
        """Giá thấp nhất trong các biến thể (giá hiển thị ở trang danh sách)."""
        return min(v.price for v in self.variants)

    def variants_in_stock(self) -> list[Variant]:
        """Các biến thể còn hàng; hết cả thì trả về mọi biến thể."""
        return [v for v in self.variants if v.stock > 0] or self.variants


@dataclass
class Catalog:
    """Danh mục sản phẩm đang bán, nhóm theo danh mục."""

    categories: dict[int, str]
    products: dict[int, Product]
    by_category: dict[int, list[Product]] = field(default_factory=dict)

    def category_ids_named(self, names: set[str]) -> list[int]:
        """Mã các danh mục có tên trong `names` (so theo dạng NFC), tăng dần."""
        wanted = {unicodedata.normalize("NFC", n) for n in names}
        return sorted(
            cid
            for cid, name in self.categories.items()
            if unicodedata.normalize("NFC", name) in wanted and self.by_category.get(cid)
        )

    def category_ids_for(self, gender: str | None) -> list[int]:
        """Các danh mục có hàng mà khách giới tính `gender` mua (dùng chung + đúng giới tính)."""
        return sorted(
            cid
            for cid, name in self.categories.items()
            if self.by_category.get(cid) and category_gender(name) in (None, gender)
        )


_PRODUCTS = text(
    """
    SELECT p.id, p.name, p.category_id, p.brand_id,
           v.id, v.price, v.stock, v.option1_value, v.option2_value
    FROM product p JOIN product_variant v ON v.product_id = p.id
    WHERE p.deleted_at IS NULL
    ORDER BY p.id, v.id
    """
)


def load_catalog(conn: Connection, seed: int) -> Catalog:
    """Đọc sản phẩm chưa xóa và các biến thể; thuộc tính ẩn sinh theo `seed` và mã sản phẩm.

    Sinh theo từng mã sản phẩm (không theo thứ tự đọc) nên thêm hay xóa 1 sản phẩm không làm
    đổi thuộc tính của các sản phẩm khác.
    """
    categories = {cid: name for cid, name in conn.execute(text("SELECT id, name FROM category"))}
    products: dict[int, Product] = {}
    for pid, name, cid, bid, vid, price, stock, opt1, opt2 in conn.execute(_PRODUCTS):
        product = products.get(pid)
        if product is None:
            product = products[pid] = Product(pid, name, cid, bid, [])
        product.variants.append(Variant(vid, int(price), int(stock), variant_label(opt1, opt2)))
    return build_catalog(categories, list(products.values()), seed)


def build_catalog(categories: dict[int, str], products: list[Product], seed: int) -> Catalog:
    """Nhóm sản phẩm theo danh mục, gắn độ "hot", chất lượng và vị trí giá trong danh mục."""
    catalog = Catalog(categories, {p.id: p for p in products})
    for product in products:
        rng = random.Random(f"{seed}-product-{product.id}")
        product.popularity = math.exp(rng.gauss(0, 1.0))
        product.quality = min(5.0, max(1.5, rng.gauss(4.1, 0.6)))
        catalog.by_category.setdefault(product.category_id, []).append(product)

    for items in catalog.by_category.values():
        prices = sorted(p.price for p in items)
        top = max(len(prices) - 1, 1)
        for product in items:
            product.price_rank = bisect_left(prices, product.price) / top
    return catalog
