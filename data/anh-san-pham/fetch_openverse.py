# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Tải ảnh CC0 / public domain cho từng danh mục qua Openverse (xem README.md cùng thư mục).

Chạy ở gốc repo:

    uv run data/anh-san-pham/fetch_openverse.py search [thư-mục ...]  # tìm ảnh ứng viên
    uv run data/anh-san-pham/fetch_openverse.py download              # tải ảnh đã chọn vào raw/

- search: gọi API Openverse (chỉ giấy phép CC0 và Public Domain Mark), tải ảnh ứng viên vào
  raw/_ung-vien/<thư mục>/ kèm ung-vien.json để xem và chọn. API cho khách vô danh 20 lượt/phút,
  200 lượt/ngày, nên kết quả tìm được lưu lại trong raw/_openverse-cache/ và không gọi lại.
- download: đọc anh-chon.json (ảnh đã chọn, kèm tác giả, giấy phép, trang gốc), tải từng ảnh vào
  raw/<thư mục>/ và ghi raw/<thư mục>/nguon.json để build_photos.py ghi bảng nguồn ảnh. Bước này
  không gọi API, chỉ tải file ảnh từ trang gốc (Flickr, Wikimedia...).
"""

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
RAW = HERE / "raw"
CACHE = RAW / "_openverse-cache"
CANDIDATES = RAW / "_ung-vien"
CHOSEN = HERE / "anh-chon.json"
API = "https://api.openverse.org/v1/images/"
# Wikimedia chặn (429) User-Agent không có cách liên hệ: https://w.wiki/4wJS
USER_AGENT = (
    "fashion-recommender-shop/1.0 (https://github.com/ngocmanhnct/fashion-recommender-shop)"
    " python-urllib"
)
LICENSES = "cc0,pdm"
# Nguồn ảnh mặc định: kho ảnh CC0 WordPress Photo Directory, rawpixel (public domain), Flickr và
# Wikimedia Commons. Nguồn khác trên Openverse thường ảnh nhỏ hoặc trang gốc không rõ tác giả.
SOURCES = "wordpress,rawpixel,flickr,wikimedia"
MIN_SIDE = 640
PREVIEW_WIDTH = 330  # ảnh ứng viên chỉ để xem và chọn
FULL_WIDTH = 960  # ảnh đã chọn: đủ để cắt vuông 640 px
CANDIDATES_PER_QUERY = 20

# Từ khóa cho Openverse: ngắn, chung hơn KEYWORDS của build_photos.py vì kho ảnh CC0 nhỏ hơn nhiều
# (từ khóa dài như "men sneakers" chỉ ra 1, 2 ảnh). Danh mục nam, nữ dùng chung từ khóa,
# chọn ảnh khác nhau.
QUERIES = {
    "Balo du lịch": "hiking backpack, rucksack",
    "Balo laptop": "laptop backpack, backpack",
    "Balo thời trang": "backpack, leather backpack",
    "Cặp, túi chống sốc laptop": "laptop bag, laptop sleeve, laptop case",
    "Dép nam": "slippers, flip flops",
    "Dép nữ": "slippers, flip flops",
    "Ghim cài áo": "brooch, lapel pin",
    "Giày boots nam": "leather boots, chelsea boot, work boots",
    "Giày boots nữ": "ankle boots, women boots",
    "Giày búp bê nữ": "ballet flats, ballet shoes",
    "Giày cao gót nữ": "high heels, high heel shoe",
    "Giày lười nam": "loafers, loafer shoe, slip on shoes",
    "Giày lười nữ": "loafers, moccasins",
    "Giày thể thao nam": "sneakers, running shoe",
    "Giày thể thao nữ": "sneakers, canvas shoes",
    "Giày tây nam": "oxford shoes, dress shoes",
    "Giày đế xuồng nữ": "wedge shoes, wedge sandal, espadrilles",
    "Găng tay": "gloves, leather gloves",
    "Gọng kính": "eyeglasses, spectacles",
    "Khăn": "scarf, silk scarf",
    "Khẩu trang": "cloth face mask, face mask, fabric mask",
    "Kính mát": "sunglasses",
    "Nón nam": "baseball cap, fedora",
    "Nón nữ": "sun hat, straw hat",
    "Phụ kiện du lịch": "travel pillow, luggage tag, passport holder",
    "Phụ kiện tóc": "hair clip, scrunchie",
    "Phụ kiện, vệ sinh giày": "shoe polish, shoe brush, shoe care",
    "Sandal nam": "sandals, leather sandals",
    "Sandal nữ": "sandals, women sandals",
    "Thắt lưng nam": "leather belt, belt buckle",
    "Thắt lưng nữ": "belt, leather belt",
    "Túi tote nữ": "tote bag",
    "Túi xách nam": "briefcase, leather bag",
    "Túi xách nữ": "handbag, purse",
    "Túi đeo chéo nam": "messenger bag, shoulder bag",
    "Túi đeo chéo nữ": "crossbody bag, shoulder bag",
    "Túi đựng đồ du lịch": "toiletry bag, duffel bag",
    "Vali": "suitcase, luggage",
    "Ví nam": "wallet, leather wallet",
    "Ví nữ": "purse, wallet",
    "Vớ nam": "socks, wool socks",
    "Vớ nữ": "socks, stockings",
}

sys.path.insert(0, str(HERE))
from build_photos import categories, slugify  # noqa: E402


def http_get(url: str, attempts: int = 4) -> bytes:
    """GET có thử lại khi API quá tải (429) hoặc lỗi tạm (5xx), chờ tăng dần."""
    for attempt in range(attempts):
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code not in (429, 500, 502, 503, 504) or attempt == attempts - 1:
                raise
            wait = int(error.headers.get("Retry-After") or 0) or 10 * 2**attempt
            print(f"  HTTP {error.code}, chờ {wait} s rồi thử lại")
            time.sleep(wait)
    raise RuntimeError("unreachable")


def search_query(query: str, sources: str) -> list[dict]:
    """Kết quả Openverse của 1 từ khóa (lưu đệm theo từ khóa)."""
    CACHE.mkdir(parents=True, exist_ok=True)
    cached = CACHE / f"{slugify(query)}--{slugify(sources)}.json"
    if cached.exists():
        return json.loads(cached.read_text(encoding="utf-8"))
    params = urllib.parse.urlencode(
        {
            "q": query,
            "license": LICENSES,
            "source": sources,
            "page_size": CANDIDATES_PER_QUERY,
            "mature": "false",
        }
    )
    try:
        results = json.loads(http_get(f"{API}?{params}"))["results"]
    except urllib.error.HTTPError as error:
        # Openverse thỉnh thoảng lỗi 500 với vài từ khóa: bỏ qua từ khóa đó, chạy lại sau.
        print(f"  bỏ từ khóa '{query}': {error}")
        return []
    cached.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    time.sleep(3.5)  # dưới 20 lượt/phút
    return results


def credit(result: dict) -> dict:
    """Thông tin nguồn cần giữ của 1 ảnh Openverse."""
    return {
        "id": result["id"],
        "title": result.get("title") or "",
        "creator": result.get("creator") or "",
        "license": result["license"],
        "license_version": result.get("license_version") or "",
        "license_url": result.get("license_url") or "",
        "source": result.get("source") or "",
        "landing_url": result.get("foreign_landing_url") or "",
        "url": result["url"],
        "width": result.get("width") or 0,
    }


def sized_url(item: dict, width: int) -> str:
    """Bản thu nhỏ của ảnh: Wikimedia chặn (429) khi tải nhiều ảnh gốc liền nhau và khuyên dùng
    ảnh thu nhỏ theo các cỡ chuẩn (https://w.wiki/GHai); Flickr có sẵn hậu tố cỡ ảnh."""
    url = item["url"]
    prefix = "https://upload.wikimedia.org/wikipedia/commons/"
    if url.startswith(prefix) and (item.get("width") or 0) > width:
        path = url[len(prefix) :]
        name = path.rsplit("/", 1)[-1]
        return f"{prefix}thumb/{path}/{width}px-{name}"
    if "staticflickr.com" in url and width < MIN_SIDE:
        return re.sub(r"_[a-z]\.(jpg|png)$", r"_n.\1", url)
    return url


def fetch_image(item: dict, width: int) -> bytes:
    """Tải 1 ảnh, nghỉ 1 giây giữa các lần tải để không bị chặn."""
    time.sleep(1)
    return http_get(sized_url(item, width), attempts=1)


def file_name(item: dict) -> str:
    suffix = Path(urllib.parse.urlparse(item["url"]).path).suffix.lower()
    return item["id"] + (suffix if suffix in {".jpg", ".jpeg", ".png", ".webp"} else ".jpg")


def search(slugs: list[str], sources: str) -> None:
    """Tìm và tải ảnh ứng viên của các danh mục vào raw/_ung-vien/<thư mục>/."""
    for slug in slugs:
        name = SLUGS[slug]
        seen: dict[str, dict] = {}
        for query in (q.strip() for q in QUERIES.get(name, name).split(",")):
            for result in search_query(query, sources):
                small = min(result.get("width") or 0, result.get("height") or 0) < MIN_SIDE
                if not small and result["id"] not in seen:
                    seen[result["id"]] = credit(result) | {"query": query}
        folder = CANDIDATES / slug
        folder.mkdir(parents=True, exist_ok=True)
        listing = folder / "ung-vien.json"
        # Giữ ứng viên của các lần tìm trước (vd lần trước tìm nguồn khác).
        ok = json.loads(listing.read_text(encoding="utf-8")) if listing.exists() else []
        for item in ok:
            seen.pop(item["id"], None)
        for item in seen.values():
            target = folder / file_name(item)
            if not target.exists():
                try:
                    target.write_bytes(fetch_image(item, PREVIEW_WIDTH))
                except (urllib.error.URLError, TimeoutError) as error:
                    print(f"  bỏ {item['id']} ({item['source']}): {error}")
                    continue
            ok.append(item)
        listing.write_text(json.dumps(ok, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{name}: {len(ok)} ảnh ứng viên")


def download() -> None:
    """Tải ảnh trong anh-chon.json vào raw/<thư mục>/ kèm nguon.json."""
    chosen: dict[str, list[dict]] = json.loads(CHOSEN.read_text(encoding="utf-8"))
    failed = 0
    for slug, items in chosen.items():
        if slug not in SLUGS:
            print(f"CẢNH BÁO: {slug} trong anh-chon.json không khớp danh mục nào, bỏ qua")
            continue
        folder = RAW / slug
        folder.mkdir(parents=True, exist_ok=True)
        sources = {}
        for item in items:
            name = file_name(item)
            target = folder / name
            if not target.exists():
                try:
                    target.write_bytes(fetch_image(item, FULL_WIDTH))
                except (urllib.error.URLError, TimeoutError) as error:
                    failed += 1
                    print(f"  lỗi tải {slug}/{name}: {error} (chạy lại lệnh để thử tiếp)")
                    continue
            sources[name] = item
        (folder / "nguon.json").write_text(
            json.dumps(sources, ensure_ascii=False, indent=1), encoding="utf-8"
        )
    print(f"Đã tải ảnh của {len(chosen)} danh mục vào {RAW}, {failed} ảnh lỗi")


SLUGS = {slugify(name): name for name in categories()}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    sub = parser.add_subparsers(dest="command", required=True)
    find = sub.add_parser("search", help="tìm ảnh ứng viên")
    find.add_argument("slugs", nargs="*", help="thư mục danh mục (mặc định: tất cả)")
    find.add_argument("--sources", default=SOURCES, help=f"nguồn Openverse (mặc định {SOURCES})")
    sub.add_parser("download", help="tải ảnh đã chọn trong anh-chon.json")
    args = parser.parse_args()
    if args.command == "search":
        unknown = [s for s in args.slugs if s not in SLUGS]
        if unknown:
            parser.error("không có danh mục: " + ", ".join(unknown))
        search(args.slugs or list(SLUGS), args.sources)
    else:
        download()


if __name__ == "__main__":
    main()
