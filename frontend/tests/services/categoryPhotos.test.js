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

  it('sản phẩm có mã liên tiếp (cùng danh mục mẫu) ra các ảnh khác nhau, hết ảnh mới lặp', () => {
    const urls = Array.from({ length: 5 }, (_, i) =>
      categoryPhotoUrl('Balo du lịch', 51 + i, PHOTOS)
    );
    expect(new Set(urls).size).toBe(5);
    expect(categoryPhotoUrl('Balo du lịch', 56, PHOTOS)).toBe(urls[0]);
  });

  it('mã sản phẩm dạng chuỗi số dùng như số, mã không phải số thì về ảnh đầu', () => {
    expect(categoryPhotoUrl('Balo du lịch', '52', PHOTOS)).toBe(
      categoryPhotoUrl('Balo du lịch', 52, PHOTOS)
    );
    expect(categoryPhotoUrl('Balo du lịch', 'abc', PHOTOS)).toBe(
      '/anh-danh-muc/balo-du-lich/1.webp'
    );
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
