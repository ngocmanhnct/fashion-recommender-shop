import PHOTOS from '../data/categoryPhotos.json';

// Ảnh minh họa theo danh mục cho sản phẩm chưa có ảnh (danh mục mẫu Tiki không kèm ảnh). Ảnh là
// file tĩnh trong public/anh-danh-muc/<thư mục>/1.webp, 2.webp...; danh sách danh mục có ảnh ở
// data/categoryPhotos.json, cả hai do data/anh-san-pham/build_photos.py sinh ra.

const BASE = `${import.meta.env.BASE_URL}anh-danh-muc`;

/** Trộn bit mã sản phẩm: mã sản phẩm trong cùng danh mục cách đều nhau, chia dư thẳng sẽ trùng ảnh. */
function mix(id) {
  let h = Math.imul(Number(id) || 0, 0x9e3779b1);
  h = Math.imul(h ^ (h >>> 16), 0x85ebca6b);
  return (h ^ (h >>> 13)) >>> 0;
}

/**
 * Đường dẫn ảnh minh họa của sản phẩm theo danh mục, null khi danh mục chưa có ảnh (khi đó dùng
 * hình vẽ SVG). Cùng sản phẩm luôn ra cùng 1 ảnh ở mọi trang.
 */
export function categoryPhotoUrl(categoryName, productId, photos = PHOTOS) {
  if (!categoryName || productId == null) return null;
  const entry = photos[categoryName.normalize('NFC')];
  if (!entry?.count) return null;
  return `${BASE}/${entry.slug}/${(mix(productId) % entry.count) + 1}.webp`;
}
