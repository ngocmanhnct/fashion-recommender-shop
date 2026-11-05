# /// script
# requires-python = ">=3.12"
# dependencies = ["pillow>=11"]
# ///
"""Dựng ảnh minh họa theo danh mục cho frontend từ ảnh tải tay (xem README.md cùng thư mục).

Chạy ở gốc repo (uv tự cài Pillow vào môi trường tạm, không đụng ml-service):

    uv run data/anh-san-pham/build_photos.py --init   # tạo raw/<thư mục>/ và tu-khoa.txt (tải tay)
    uv run data/anh-san-pham/build_photos.py          # dựng ảnh từ raw/

Mỗi ảnh trong raw/<thư mục>/ được xoay đúng chiều, cắt vuông ở giữa, thu về 640 × 640, lưu WebP
(không giữ EXIF, nên không lộ vị trí GPS hay thông tin máy ảnh) vào
frontend/public/anh-danh-muc/<thư mục>/1.webp, 2.webp... Kết quả kèm theo:

- frontend/src/data/categoryPhotos.json: danh mục nào có bao nhiêu ảnh (frontend đọc file này).
- data/anh-san-pham/nguon-anh.md: nguồn của từng ảnh (trang gốc, tác giả, giấy phép, đọc từ
  raw/<thư mục>/nguon.json do fetch_openverse.py ghi; ảnh tải tay thì chỉ ghi tên file gốc).

Thư mục frontend/public/anh-danh-muc/ được dựng lại toàn bộ mỗi lần chạy: đừng sửa tay ở đó.
"""

import argparse
import json
import re
import shutil
import sys
import unicodedata
from pathlib import Path

from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SEED = ROOT / "backend/src/main/resources/seed/tiki-catalog.json"
RAW = HERE / "raw"
CREDITS = HERE / "nguon-anh.md"
OUT = ROOT / "frontend/public/anh-danh-muc"
MANIFEST = ROOT / "frontend/src/data/categoryPhotos.json"

SIZE = 640
QUALITY = 80
MAX_PER_CATEGORY = 8
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}

# Từ khóa tiếng Anh để tìm tay trên các kho ảnh (tìm tiếng Anh ra nhiều ảnh hơn hẳn).
KEYWORDS = {
    "Balo du lịch": "travel backpack",
    "Balo laptop": "laptop backpack",
    "Balo thời trang": "fashion backpack, mini backpack",
    "Cặp, túi chống sốc laptop": "laptop sleeve, laptop bag",
    "Dép nam": "men slides, men slippers",
    "Dép nữ": "women slides, women slippers",
    "Ghim cài áo": "brooch, lapel pin",
    "Giày boots nam": "men leather boots, chelsea boots",
    "Giày boots nữ": "women ankle boots",
    "Giày búp bê nữ": "ballet flats",
    "Giày cao gót nữ": "high heels, stiletto",
    "Giày lười nam": "men loafers",
    "Giày lười nữ": "women loafers",
    "Giày thể thao nam": "men sneakers",
    "Giày thể thao nữ": "women sneakers",
    "Giày tây nam": "men dress shoes, oxford shoes",
    "Giày đế xuồng nữ": "wedge shoes, wedge sandals",
    "Găng tay": "gloves, leather gloves",
    "Gọng kính": "eyeglasses frames",
    "Khăn": "scarf",
    "Khẩu trang": "cloth face mask",
    "Kính mát": "sunglasses",
    "Nón nam": "baseball cap, men hat",
    "Nón nữ": "women sun hat, bucket hat",
    "Phụ kiện du lịch": "travel neck pillow, luggage tag, travel accessories",
    "Phụ kiện tóc": "hair clips, scrunchies",
    "Phụ kiện, vệ sinh giày": "shoe cleaning kit, shoe brush",
    "Sandal nam": "men sandals",
    "Sandal nữ": "women sandals",
    "Thắt lưng nam": "men leather belt",
    "Thắt lưng nữ": "women belt",
    "Túi tote nữ": "tote bag",
    "Túi xách nam": "men leather bag, briefcase",
    "Túi xách nữ": "women handbag",
    "Túi đeo chéo nam": "men crossbody bag, sling bag",
    "Túi đeo chéo nữ": "women crossbody bag",
    "Túi đựng đồ du lịch": "toiletry bag, packing cubes",
    "Vali": "suitcase, luggage",
    "Ví nam": "men leather wallet",
    "Ví nữ": "women wallet, purse",
    "Vớ nam": "men socks",
    "Vớ nữ": "women socks",
}


def slugify(name: str) -> str:
    """Tên danh mục → tên thư mục không dấu.

    Vd "Cặp, túi chống sốc laptop" → cap-tui-chong-soc-laptop.
    """
    text = unicodedata.normalize("NFD", name.replace("đ", "d").replace("Đ", "D"))
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn").lower()
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def categories() -> list[str]:
    """Tên các danh mục của danh mục mẫu (NFC, sắp xếp theo tên)."""
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    return sorted({unicodedata.normalize("NFC", p["category"]) for p in seed["products"]})


def init(names: list[str]) -> None:
    """Tạo raw/<thư mục>/ cho mỗi danh mục, kèm tu-khoa.txt ghi tên danh mục và từ khóa tìm ảnh."""
    for name in names:
        folder = RAW / slugify(name)
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "tu-khoa.txt").write_text(
            f"{name}\nTừ khóa: {KEYWORDS.get(name, name)}\n", encoding="utf-8"
        )
    print(f"Đã tạo {len(names)} thư mục trong {RAW}")


def read_sources(folder: Path) -> dict[str, dict]:
    """Thông tin nguồn của ảnh trong thư mục (nguon.json do fetch_openverse.py ghi), có thể rỗng."""
    path = folder / "nguon.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def cell(text: str) -> str:
    """Chữ an toàn trong 1 ô bảng Markdown."""
    return " ".join(text.replace("|", "/").split())


def credit_cells(file_name: str, sources: dict[str, dict]) -> str:
    """3 ô cuối của dòng nguồn ảnh: ảnh gốc (kèm liên kết), tác giả, giấy phép."""
    item = sources.get(file_name)
    if not item:
        return f"{cell(file_name)} | | "
    title = cell(item.get("title") or item["id"])[:80]
    licence = {"cc0": "CC0", "pdm": "Public Domain Mark"}.get(
        item["license"], item["license"].upper()
    )
    licence = f"{licence} {item.get('license_version', '')}".rstrip()
    if item.get("license_url"):
        licence = f"[{licence}]({item['license_url']})"
    origin = f"[{title}]({item['landing_url']})" if item.get("landing_url") else title
    return f"{origin} ({item.get('source', '')}) | {cell(item.get('creator', ''))} | {licence}"


def build(names: list[str]) -> int:
    """Dựng ảnh, file danh sách cho frontend và bảng nguồn ảnh. Trả về số danh mục chưa có ảnh."""
    slugs = {slugify(name): name for name in names}
    if RAW.is_dir():
        # Thư mục bắt đầu bằng "_" là của fetch_openverse.py (ảnh ứng viên, lưu đệm).
        folders = (p.name for p in RAW.iterdir() if p.is_dir() and not p.name.startswith("_"))
        for unknown in sorted(f for f in folders if f not in slugs):
            print(f"CẢNH BÁO: thư mục raw/{unknown} không khớp danh mục nào, bỏ qua")

    shutil.rmtree(OUT, ignore_errors=True)
    manifest: dict[str, dict] = {}
    credit_rows: list[str] = []
    missing: list[str] = []
    for slug, name in slugs.items():
        folder = RAW / slug
        files = (
            sorted(
                p for p in folder.glob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS
            )
            if folder.is_dir()
            else []
        )
        if len(files) > MAX_PER_CATEGORY:
            print(f"{name}: có {len(files)} ảnh, chỉ lấy {MAX_PER_CATEGORY} ảnh đầu")
            files = files[:MAX_PER_CATEGORY]
        if not files:
            missing.append(name)
            continue
        (OUT / slug).mkdir(parents=True, exist_ok=True)
        sources = read_sources(folder)
        for index, source in enumerate(files, start=1):
            with Image.open(source) as image:
                image = ImageOps.exif_transpose(image)
                if image.mode in ("RGBA", "LA", "P"):
                    # Ảnh PNG nền trong suốt: lót nền trắng, nếu không phần trong suốt thành đen.
                    image = image.convert("RGBA")
                    background = Image.new("RGB", image.size, "white")
                    background.paste(image, mask=image.getchannel("A"))
                    image = background
                image = image.convert("RGB")
                if min(image.size) < SIZE:
                    print(f"CẢNH BÁO: {slug}/{source.name} nhỏ ({image.width}×{image.height})")
                image = ImageOps.fit(image, (SIZE, SIZE), Image.Resampling.LANCZOS)
                image.save(OUT / slug / f"{index}.webp", "WEBP", quality=QUALITY, method=6)
            cells = credit_cells(source.name, sources)
            credit_rows.append(f"| {name} | {slug}/{index}.webp | {cells} |")
        manifest[name] = {"slug": slug, "count": len(files)}

    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    CREDITS.write_text(
        "# Nguồn ảnh minh họa theo danh mục\n\n"
        "Sinh tự động bởi `build_photos.py`, đừng sửa tay. Ảnh lấy qua Openverse, chỉ giấy phép"
        " CC0 hoặc Public Domain Mark (dùng tự do, kể cả thương mại, không bắt buộc ghi nguồn;"
        " vẫn ghi lại để kiểm chứng). Ảnh tải tay không có thông tin nguồn thì chỉ ghi tên file"
        " gốc.\n\n"
        "| Danh mục | Ảnh trên web | Ảnh gốc | Tác giả | Giấy phép |\n"
        "| --- | --- | --- | --- | --- |\n" + "\n".join(credit_rows) + "\n",
        encoding="utf-8",
    )

    total = sum(p.stat().st_size for p in OUT.rglob("*.webp")) if OUT.is_dir() else 0
    print(
        f"Xong: {len(manifest)}/{len(names)} danh mục có ảnh, {len(credit_rows)} ảnh, "
        f"{total / 1024 / 1024:.1f} MB trong {OUT.relative_to(ROOT)}"
    )
    if missing:
        print("Chưa có ảnh (vẫn dùng hình vẽ SVG): " + ", ".join(missing))
    return len(missing)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--init", action="store_true", help="chỉ tạo thư mục raw/ và từ khóa")
    args = parser.parse_args()
    names = categories()
    if missing_keywords := [n for n in names if n not in KEYWORDS]:
        print("CẢNH BÁO: danh mục chưa có từ khóa: " + ", ".join(missing_keywords))
    if args.init:
        init(names)
    else:
        build(names)


if __name__ == "__main__":
    sys.exit(main())
