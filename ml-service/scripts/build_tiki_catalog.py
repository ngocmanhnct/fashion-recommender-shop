"""Dựng danh mục sản phẩm mẫu cho cửa hàng từ bộ dữ liệu Tiki.

Nguồn: "TIKI E-Commerce Fashion Products Dataset" trên Kaggle
(michaelminhpham/vietnamese-tiki-e-commerce-dataset), 6 tệp CSV ngày 13/09/2023, 41.603 sản phẩm
giày, túi, balo - vali và phụ kiện, thu thập từ API công khai của Tiki, dùng cho mục đích giáo
dục. Không có quần áo, màu, size, tồn kho, ảnh. Xem data/tiki/README.md.

Cách chọn (quyết định D16, mở rộng ngày 16/10/2026 để demo gợi ý rõ hơn: 42 danh mục, mỗi danh
mục 25 sản phẩm): gộp danh mục lá của Tiki thành các danh mục của cửa hàng (CATEGORIES), bỏ sản
phẩm thuộc danh mục chung chung ("Root", "Thời Trang"), giá 0 hoặc chưa bán được cái nào; tên sản
phẩm phải có từ khóa của danh mục và không có từ bị loại (lọc hàng xếp nhầm danh mục, vd mặt nạ
trong "Kính mát nữ", giày đi mưa trong "Giày búp bê"); bỏ hàng combo và sản phẩm gần trùng tên
với sản phẩm đã chọn; trong mỗi danh mục lấy các sản phẩm bán nhiều nhất trên Tiki.

Phần sinh thêm (ghi rõ trong báo cáo): giày dép có 1 tầng phân loại "Kích cỡ" (nam 39-43, nữ
35-39), sản phẩm khác không có phân loại; tồn kho 0-40 sinh giả ngẫu nhiên nhưng cố định theo mã
sản phẩm; mã SKU "TK<mã Tiki>[-<cỡ>]". Thương hiệu "OEM", "No Brand" để trống. Số lượng đã bán,
điểm đánh giá của Tiki chỉ ghi vào file để đối chứng khi đánh giá gợi ý, không nạp vào CSDL.

Chạy trong ml-service/:
    uv run python scripts/build_tiki_catalog.py
Đọc mọi tệp .zip, .csv trong data/tiki/raw và ghi backend/src/main/resources/seed/tiki-catalog.json
(backend tự nạp khi bảng product còn trống). Hai đường dẫn này cố định theo vị trí của repo, không
nhận từ dòng lệnh, để script không bao giờ đọc hay ghi ra ngoài repo.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import unicodedata
import zipfile
from dataclasses import dataclass
from pathlib import Path

SOURCE = (
    "TIKI E-Commerce Fashion Products Dataset (Kaggle: "
    "michaelminhpham/vietnamese-tiki-e-commerce-dataset), tệp ngày 13/09/2023. "
    "Giấy phép: Product crawled from open public API from TIKI. "
    "Dataset used for education purpose."
)

REPO_ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = REPO_ROOT / "data" / "tiki" / "raw"
OUT_FILE = REPO_ROOT / "backend" / "src" / "main" / "resources" / "seed" / "tiki-catalog.json"

MEN_SIZES = ["39", "40", "41", "42", "43"]
WOMEN_SIZES = ["35", "36", "37", "38", "39"]
SIZE_TIER = "Kích cỡ"
MAX_STOCK = 40
NEAR_DUPLICATE = 0.6
NO_BRAND = {"oem", "no brand", "khong su dung", ""}
EXCLUDED_WORDS = ["combo"]
COMBINING_MARK = re.compile("[\u0300-\u036f]")


@dataclass(frozen=True)
class ShopCategory:
    """Một danh mục của cửa hàng, gộp từ các danh mục lá của Tiki trong các tệp CSV đã nêu."""

    name: str
    files: tuple[str, ...]
    tiki_categories: tuple[str, ...]
    keywords: tuple[str, ...]
    excluded: tuple[str, ...] = ()
    sizes: tuple[str, ...] = ()


SHOE_CARE_EXCLUDED = ("da nang",)
WALLET_EXCLUDED = ("bao dung", "vo boc", "passport", "ho chieu", "tui nhua")
GLASSES_EXCLUDED = ("chan giot", "mat na", "trong kinh", "bao ho")
BELT_EXCLUDED = ("dinh", "duc lo", "vong tay", "chu y")
MEN = tuple(MEN_SIZES)
WOMEN = tuple(WOMEN_SIZES)

CATEGORIES = [
    # Giày dép nam
    ShopCategory(
        "Giày thể thao nam",
        ("men_shoes",),
        ("Giày thể thao nam cổ cao", "Giày thể thao nam cổ thấp"),
        ("giay",),
        sizes=MEN,
    ),
    ShopCategory(
        "Giày tây nam",
        ("men_shoes",),
        ("Giày tây nam có dây", "Giày tây nam không dây", "Giày tây nam"),
        ("giay",),
        sizes=MEN,
    ),
    ShopCategory(
        "Giày lười nam",
        ("men_shoes",),
        ("Giày lười da nam", "Giày lười vải nam"),
        ("giay",),
        sizes=MEN,
    ),
    ShopCategory(
        "Giày boots nam",
        ("men_shoes",),
        ("Giày boots nam cổ cao", "Giày boots nam cổ thấp"),
        ("giay", "boot", "boots"),
        sizes=MEN,
    ),
    ShopCategory(
        "Sandal nam",
        ("men_shoes",),
        ("Giày sandals nam quai ngang", "Giày sandals nam quai chéo", "Giày sandals nam"),
        ("sandal", "sandals", "xang dan", "dep"),
        sizes=MEN,
    ),
    ShopCategory(
        "Dép nam",
        ("men_shoes",),
        ("Dép nam xỏ ngón", "Dép nam quai ngang", "Dép nam đi trong nhà"),
        ("dep",),
        sizes=MEN,
    ),
    # Giày dép nữ
    ShopCategory(
        "Giày thể thao nữ",
        ("women_shoes",),
        ("Giày thể thao cổ cao", "Giày thể thao cổ thấp"),
        ("giay",),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Giày cao gót nữ",
        ("women_shoes",),
        (
            "Giày cao gót quai sau",
            "Giày cao gót hở mũi",
            "Giày cao gót sục",
            "Giày cao gót nhọn",
            "Giày lười cao gót",
        ),
        ("giay", "guoc", "sandal"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Giày đế xuồng nữ",
        ("women_shoes",),
        ("Giày Đế xuồng nữ",),
        ("xuong",),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Giày búp bê nữ",
        ("women_shoes",),
        (
            "Giày búp bê mũi nhọn",
            "Giày búp bê mũi tròn",
            "Giày búp bê mũi vuông",
            "Giày búp bê",
            "Giày bít mũi quai ngang",
        ),
        ("giay",),
        excluded=("ballet", "bale", "di mua", "giay mua", "nhua", "cao got"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Giày lười nữ",
        ("women_shoes",),
        ("Giày lười mũi nhọn", "Giày lười hở gót", "Giày lười mũi vuông"),
        ("giay", "suc"),
        excluded=("di mua", "nhua", "the thao"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Giày boots nữ",
        ("women_shoes",),
        (
            "Giày boots nữ cổ ngắn",
            "Giày boots nữ cổ cao",
            "Giày boots nữ",
            "Giày boots nữ gót nhọn",
        ),
        ("giay", "boot", "boots", "bot"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Sandal nữ",
        ("women_shoes",),
        (
            "Giày sandals đế bằng",
            "Giày sandals xỏ ngón",
            "Giày sandals chiến binh",
            "Giày sandals buộc dây",
        ),
        ("sandal", "sandals", "xang dan", "dep", "giay"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Dép nữ",
        ("women_shoes",),
        ("Dép xỏ ngón", "Dép quai ngang", "Dép đi trong nhà", "Dép - Guốc nữ"),
        ("dep", "guoc"),
        sizes=WOMEN,
    ),
    ShopCategory(
        "Phụ kiện, vệ sinh giày",
        ("men_shoes", "women_shoes"),
        (
            "Phụ kiện cho giày nam",
            "Phụ kiện cho giày",
            "Vệ sinh giày",
            "Miếng lót giày nam",
            "Miếng lót giày",
        ),
        ("giay",),
        excluded=SHOE_CARE_EXCLUDED,
    ),
    # Túi ví
    ShopCategory(
        "Túi xách nữ",
        ("women_bags",),
        ("Túi xách to bản", "Túi xách vừa và nhỏ", "Túi thời trang nữ"),
        ("tui",),
        excluded=WALLET_EXCLUDED,
    ),
    ShopCategory(
        "Túi tote nữ",
        ("women_bags",),
        ("Túi tote có khóa", "Túi tote không khóa"),
        ("tui",),
    ),
    ShopCategory(
        "Túi đeo chéo nữ",
        ("women_bags",),
        ("Túi đeo chéo dạo phố", "Túi đeo chéo công sở"),
        ("tui",),
    ),
    ShopCategory(
        "Ví nữ",
        ("women_bags",),
        ("Ví ngắn", "Ví dài"),
        ("vi", "bop"),
        excluded=WALLET_EXCLUDED,
    ),
    ShopCategory(
        "Túi xách nam",
        ("men_bags",),
        ("Túi xách công sở nam", "Túi thời trang nam"),
        ("tui", "cap"),
        excluded=WALLET_EXCLUDED,
    ),
    ShopCategory(
        "Túi đeo chéo nam",
        ("men_bags",),
        ("Túi đeo chéo nam", "Túi bao tử, túi đeo bụng"),
        ("tui",),
    ),
    ShopCategory(
        "Ví nam",
        ("men_bags",),
        ("Ví nam đứng", "Ví nam ngang", "Ví nam"),
        ("vi", "bop"),
        excluded=WALLET_EXCLUDED,
    ),
    # Balo - vali
    ShopCategory(
        "Balo laptop",
        ("backpacks_suitcases",),
        ("Balo laptop",),
        ("balo", "ba lo"),
    ),
    ShopCategory(
        "Balo thời trang",
        ("backpacks_suitcases",),
        ("Balo nam", "Balo nữ", "Balo dây rút", "Balo và Vali"),
        ("balo", "ba lo"),
    ),
    ShopCategory(
        "Balo du lịch",
        ("backpacks_suitcases",),
        ("Balo du lịch, phượt",),
        ("balo", "ba lo"),
    ),
    ShopCategory(
        "Cặp, túi chống sốc laptop",
        ("backpacks_suitcases",),
        ("Cặp đựng laptop", "Túi chống sốc"),
        ("cap", "tui"),
        excluded=("bong khi", "chiet", "my pham"),
    ),
    ShopCategory(
        "Túi đựng đồ du lịch",
        ("backpacks_suitcases",),
        ("Túi trống",),
        ("tui",),
        excluded=("zip", "ca phe"),
    ),
    ShopCategory(
        "Vali",
        ("backpacks_suitcases",),
        ("Vali nhựa", "Vali vải", "Vali trẻ em"),
        ("vali", "valy"),
    ),
    ShopCategory(
        "Phụ kiện du lịch",
        ("backpacks_suitcases",),
        ("Phụ kiện vali", "Phụ kiện du lịch khác", "Ổ khóa"),
        ("vali", "valy", "du lich", "hanh ly", "o khoa"),
        excluded=("khan", "khau trang", "mat kinh"),
    ),
    # Phụ kiện thời trang
    ShopCategory(
        "Nón nam",
        ("fashion_accessories",),
        (
            "Nón lưỡi trai nam",
            "Nón snapback nam",
            "Nón tai bèo nam - bucket nam",
            "Nón len nam",
            "Nón nam",
            "Nón phớt cao bồi nam",
        ),
        ("non", "mu"),
    ),
    ShopCategory(
        "Nón nữ",
        ("fashion_accessories",),
        (
            "Nón lưỡi trai nữ",
            "Nón tai bèo nữ",
            "Nón chống nắng nữ",
            "Nón rộng vành nữ",
            "Nón nửa đầu nữ",
        ),
        ("non", "mu"),
        excluded=("khau trang",),
    ),
    ShopCategory(
        "Kính mát",
        ("fashion_accessories",),
        ("Kính mát nam", "Kính mát nữ", "Kính mát"),
        ("kinh",),
        excluded=GLASSES_EXCLUDED,
    ),
    ShopCategory(
        "Gọng kính",
        ("fashion_accessories",),
        ("Gọng kính nữ", "Gọng kính nam", "Gọng kính"),
        ("gong", "kinh"),
        excluded=(*GLASSES_EXCLUDED, "kinh lao", "vien thi", "doc sach"),
    ),
    ShopCategory(
        "Thắt lưng nam",
        ("fashion_accessories",),
        ("Dây thắt lưng - Dây nịt", "Thắt lưng, dây nịt nam"),
        ("that lung", "day nit", "dai"),
        excluded=BELT_EXCLUDED,
    ),
    ShopCategory(
        "Thắt lưng nữ",
        ("fashion_accessories",),
        ("Thắt lưng nữ bản to", "Thắt lưng nữ bản nhỏ"),
        ("that lung", "day nit", "dai"),
        excluded=BELT_EXCLUDED,
    ),
    ShopCategory(
        "Vớ nam",
        ("fashion_accessories",),
        ("Vớ cổ ngắn nam", "Vớ cổ cao nam", "Tất, vớ nam"),
        ("vo", "tat"),
    ),
    ShopCategory(
        "Vớ nữ",
        ("fashion_accessories",),
        ("Vớ ngắn nữ", "Vớ dài nữ"),
        ("vo", "tat"),
    ),
    ShopCategory(
        "Khăn",
        ("fashion_accessories",),
        ("Khăn choàng - Khăn tay nữ", "Khăn - Găng tay nam"),
        ("khan",),
    ),
    ShopCategory(
        "Găng tay",
        ("fashion_accessories",),
        ("Găng tay nam",),
        ("gang tay", "bao tay"),
    ),
    ShopCategory(
        "Phụ kiện tóc",
        ("fashion_accessories",),
        ("Cài tóc", "Cột tóc", "Băng đô", "Phụ kiện tóc khác", "Phụ kiện thời trang nữ"),
        ("toc", "bang do", "bom", "turban"),
    ),
    ShopCategory(
        "Khẩu trang",
        ("fashion_accessories",),
        ("Khẩu trang nữ",),
        ("khau trang",),
        excluded=("moc", "day deo", "tai gia", "dem", "khung", "hat khu mui", "hat lam thom"),
    ),
    ShopCategory(
        "Ghim cài áo",
        ("fashion_accessories",),
        ("Cài Áo",),
        ("cai ao", "ghim", "tram"),
    ),
]


def fold(text: str) -> str:
    """Bỏ dấu tiếng Việt (kể cả đ), chữ thường, gộp khoảng trắng: để so từ khóa và so trùng."""
    decomposed = unicodedata.normalize("NFD", text.replace("đ", "d").replace("Đ", "D"))
    plain = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return " ".join(plain.lower().split())


def compose(text: str) -> str:
    """Chữ ở dạng dựng sẵn (Unicode NFC), bỏ các dấu kết hợp còn sót (gạch chân trang trí, dấu lạc).

    Backend tìm từ khóa bằng LIKE của MySQL, so từng ký tự: chữ tách dấu (NFD) hoặc chữ kèm dấu kết
    hợp không tìm được. Chữ tiếng Việt ở dạng NFC không còn dấu kết hợp nào.
    """
    return COMBINING_MARK.sub("", unicodedata.normalize("NFC", text))


def words(text: str) -> set[str]:
    """Tập từ (đã bỏ dấu) của một chuỗi, chỉ giữ chữ và số."""
    return set(re.findall(r"[a-z0-9]+", fold(text)))


def has_phrase(folded: str, phrase: str) -> bool:
    """Chuỗi đã bỏ dấu có chứa cụm từ (khớp trọn từ) hay không."""
    return re.search(rf"(?<![a-z0-9]){re.escape(phrase)}(?![a-z0-9])", folded) is not None


def clean_name(name: str) -> str:
    """Bỏ nhãn quảng cáo đầu tên như "[Ảnh Thật]", "(Dép 35k)"; tên viết hoa toàn bộ thì hạ xuống.

    Chữ đưa về dạng dựng sẵn bằng compose() để tìm được bằng từ khóa.
    """
    text = " ".join(compose(name).split())
    text = re.sub(r"^(\s*[\[(][^\])]*[\])]\s*)+", "", text).strip()
    letters = [ch for ch in text if ch.isalpha()]
    if letters and sum(ch.isupper() for ch in letters) / len(letters) > 0.8:
        text = text.lower()
    return text[:1].upper() + text[1:]


def split_glued_sentences(text: str) -> str:
    """Tách câu dính liền: "trượtMàu" -> "trượt. Màu", "Nữ.Chất" -> "Nữ. Chất"."""
    out = []
    for i, ch in enumerate(text):
        glued = (
            2 <= i < len(text) - 1
            and ch.isupper()
            and text[i - 1].islower()
            and text[i - 2].islower()
            and text[i + 1].islower()
        )
        if glued:
            out.append(". ")
        elif i >= 1 and ch.isupper() and text[i - 1] in ".!?":
            out.append(" ")
        out.append(ch)
    return "".join(out)


def clean_description(description: str) -> str | None:
    """Làm gọn mô tả Tiki: tách các dòng bị dính liền, gộp khoảng trắng, cắt về hết từ.

    Bộ dữ liệu đã bỏ xuống dòng nên câu sau dính vào câu trước ("trượtMàu sắc"): chèn ". " khi
    một từ viết thường (từ 2 chữ cái) liền với một từ viết hoa chữ đầu. Mô tả bị cắt ở khoảng 200
    ký tự nên cắt về hết từ cuối và thêm "…". Chữ đưa về dạng dựng sẵn bằng compose() như tên.
    """
    text = " ".join(split_glued_sentences(compose(description)).split())
    if not text:
        return None
    if len(description.rstrip()) >= 195 and " " in text:
        text = text[: text.rfind(" ")].rstrip(" ,.-:;") + "…"
    return text


def brand_of(raw: str) -> str | None:
    """Tên thương hiệu (chữ dựng sẵn), None nếu là hàng không thương hiệu."""
    brand = " ".join(compose(raw).split())
    return None if fold(brand) in NO_BRAND else brand


def stock_of(tiki_id: str, option: str | None) -> int:
    """Tồn kho giả 0..MAX_STOCK, cố định theo mã sản phẩm và giá trị phân loại."""
    digest = hashlib.sha256(f"{tiki_id}/{option or ''}".encode()).digest()
    return int.from_bytes(digest[:4], "big") % (MAX_STOCK + 1)


def is_near_duplicate(name: str, chosen: list[set[str]]) -> bool:
    """Tên gần trùng (độ giống Jaccard trên tập từ) với một sản phẩm đã chọn trong danh mục."""
    current = words(name)
    return any(len(current & other) / len(current | other) >= NEAR_DUPLICATE for other in chosen)


def matches(category: ShopCategory, name: str) -> bool:
    """Tên có từ khóa của danh mục, không có từ bị loại, và không phải hàng combo."""
    folded = fold(name)
    if any(has_phrase(folded, w) for w in EXCLUDED_WORDS + list(category.excluded)):
        return False
    return any(has_phrase(folded, w) for w in category.keywords)


def to_product(category: ShopCategory, row: dict[str, str]) -> dict:
    """Chuyển 1 dòng CSV của Tiki thành 1 sản phẩm của file mẫu."""
    tiki_id = row["id"].strip()
    price = int(row["price"])
    options = list(category.sizes) or [None]
    variants = [
        {
            "option1Value": option,
            "option2Value": None,
            "price": price,
            "stock": stock_of(tiki_id, option),
            "skuCode": f"TK{tiki_id}" + (f"-{option}" if option else ""),
        }
        for option in options
    ]
    return {
        "tikiId": int(tiki_id),
        "tikiCategory": row["category"].strip(),
        "tikiQuantitySold": int(row["quantity_sold"]),
        "tikiRatingAverage": float(row["rating_average"] or 0),
        "tikiReviewCount": int(row["review_count"] or 0),
        "category": category.name,
        "brand": brand_of(row["brand"]),
        "name": clean_name(row["name"]),
        "description": clean_description(row["description"]),
        "variationName1": SIZE_TIER if category.sizes else None,
        "variationName2": None,
        "variants": variants,
    }


def select(rows: list[dict[str, str]], per_category: int) -> list[dict]:
    """Chọn sản phẩm cho từng danh mục của cửa hàng, theo thứ tự CATEGORIES."""
    products = []
    seen_ids: set[str] = set()
    for category in CATEGORIES:
        candidates = [
            row
            for row in rows
            if row["_file"] in category.files
            and row["category"].strip() in category.tiki_categories
            and int(row["price"] or 0) > 0
            and int(row["quantity_sold"] or 0) > 0
            and matches(category, row["name"])
        ]
        candidates.sort(key=lambda row: (-int(row["quantity_sold"]), int(row["id"])))
        chosen: list[set[str]] = []
        for row in candidates:
            if len(chosen) == per_category:
                break
            name = clean_name(row["name"])
            if row["id"] in seen_ids or is_near_duplicate(name, chosen):
                continue
            seen_ids.add(row["id"])
            chosen.append(words(name))
            products.append(to_product(category, row))
    return products


def read_rows(raw_dir: Path) -> list[dict[str, str]]:
    """Đọc mọi tệp CSV (để rời hoặc trong .zip) của bộ Tiki; _file là phần tên sau tiền tố."""
    rows = []

    def add(name: str, text: str) -> None:
        file_key = Path(name).stem.removeprefix("vietnamese_tiki_products_")
        for row in csv.DictReader(io.StringIO(text)):
            row["_file"] = file_key
            rows.append(row)

    for path in sorted(raw_dir.iterdir()):
        if path.suffix == ".zip":
            with zipfile.ZipFile(path) as archive:
                for member in sorted(archive.namelist()):
                    if member.endswith(".csv"):
                        add(member, archive.read(member).decode("utf-8-sig"))
        elif path.suffix == ".csv":
            add(path.name, path.read_text(encoding="utf-8-sig"))
    return rows


def main() -> None:
    """Đọc bộ Tiki, chọn sản phẩm và ghi file JSON cho backend."""
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--per-category", type=int, default=25)
    args = parser.parse_args()

    rows = read_rows(RAW_DIR)
    if not rows:
        raise SystemExit(f"Không thấy tệp .zip hay .csv nào trong {RAW_DIR}")
    products = select(rows, args.per_category)
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(
        json.dumps({"source": SOURCE, "products": products}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Đọc {len(rows)} dòng, chọn {len(products)} sản phẩm, ghi {OUT_FILE}")


if __name__ == "__main__":
    main()
