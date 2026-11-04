import { describe, expect, it } from 'vitest';
import { categoryPhotoUrl } from '../../src/services/categoryPhotos';

const PHOTOS = {
  'Balo du lịch': { slug: 'balo-du-lich', count: 5 },
  Vali: { slug: 'vali', count: 0 },
};

describe('categoryPhotoUrl', () => {
  it('chọn 1 trong các ảnh của danh mục, cùng sản phẩm luôn cùng ảnh', () => {
    const url = categoryPhotoUrl('Balo du lịch', 7, PHOTOS);
    expect(url).toMatch(/^\/anh-danh-muc\/balo-du-lich\/[1-5]\.webp$/);
    expect(categoryPhotoUrl('Balo du lịch', 7, PHOTOS)).toBe(url);
  });

  it('sản phẩm có mã cách đều nhau (cùng danh mục mẫu) vẫn ra nhiều ảnh khác nhau', () => {
    const urls = new Set(
      Array.from({ length: 25 }, (_, i) => categoryPhotoUrl('Balo du lịch', 3 + i * 25, PHOTOS))
    );
    expect(urls.size).toBeGreaterThanOrEqual(4);
  });

  it('tên danh mục dạng tách dấu (NFD) vẫn khớp', () => {
    expect(categoryPhotoUrl('Balo du lịch'.normalize('NFD'), 7, PHOTOS)).toBe(
      categoryPhotoUrl('Balo du lịch', 7, PHOTOS)
    );
  });

  it('danh mục chưa có ảnh, thiếu tên danh mục hay mã sản phẩm thì không có ảnh', () => {
    expect(categoryPhotoUrl('Vali', 7, PHOTOS)).toBeNull();
    expect(categoryPhotoUrl('Ví nam', 7, PHOTOS)).toBeNull();
    expect(categoryPhotoUrl(undefined, 7, PHOTOS)).toBeNull();
    expect(categoryPhotoUrl('Balo du lịch', null, PHOTOS)).toBeNull();
  });
});
