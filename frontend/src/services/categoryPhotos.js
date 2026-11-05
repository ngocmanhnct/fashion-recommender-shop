import PHOTOS from '../data/categoryPhotos.json';

// Ảnh minh họa theo danh mục cho sản phẩm chưa có ảnh (danh mục mẫu Tiki không kèm ảnh). Ảnh là
// file tĩnh trong public/anh-danh-muc/<thư mục>/1.webp, 2.webp...; danh sách danh mục có ảnh ở
// data/categoryPhotos.json, cả hai do data/anh-san-pham/build_photos.py sinh ra.

const BASE = `${import.meta.env.BASE_URL}anh-danh-muc`;

/**
 * Đường dẫn ảnh minh họa của sản phẩm theo danh mục, null khi danh mục chưa có ảnh (khi đó dùng
 * hình vẽ SVG). Cùng sản phẩm luôn ra cùng 1 ảnh ở mọi trang. Ảnh chọn theo mã sản phẩm chia dư
 * cho số ảnh: sản phẩm mẫu cùng danh mục có mã liên tiếp (nạp theo thứ tự file), nên các sản phẩm
 * đứng gần nhau (vd khối "liên quan" cùng danh mục) lần lượt ra các ảnh khác nhau. Không trộn mã
 * kiểu băm: băm ngẫu nhiên với 4-8 ảnh cho 25 sản phẩm thì khối 3-4 sản phẩm hay trùng ảnh.
 */
export function categoryPhotoUrl(categoryName, productId, photos = PHOTOS) {
  if (!categoryName || productId == null) return null;
  const entry = photos[categoryName.normalize('NFC')];
  if (!entry?.count) return null;
  const index = (((Number(productId) || 0) % entry.count) + entry.count) % entry.count;
  return `${BASE}/${entry.slug}/${index + 1}.webp`;
}
