import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ProductGallery from '../../src/components/ProductGallery';
import ProductImage from '../../src/components/ProductImage';

vi.mock('../../src/data/categoryPhotos.json', () => ({
  default: { 'Balo du lịch': { slug: 'balo-du-lich', count: 3 } },
}));

describe('ProductImage với ảnh minh họa theo danh mục', () => {
  it('sản phẩm có ảnh riêng thì dùng ảnh riêng', () => {
    const { container } = render(
      <ProductImage src="/uploads/a.jpg" productId={7} categoryName="Balo du lịch" />
    );
    expect(container.querySelector('img')).toHaveAttribute('src', '/uploads/a.jpg');
  });

  it('chưa có ảnh riêng thì dùng ảnh của danh mục, ảnh lỗi thì về hình vẽ SVG', () => {
    const { container } = render(<ProductImage productId={7} categoryName="Balo du lịch" />);
    const img = container.querySelector('img');
    expect(img.getAttribute('src')).toMatch(/^\/anh-danh-muc\/balo-du-lich\/[1-3]\.webp$/);

    fireEvent.error(img);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('svg')).not.toBeNull();
  });

  it('danh mục chưa có ảnh thì hiện hình vẽ SVG', () => {
    const { container } = render(<ProductImage productId={7} categoryName="Vali" />);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('svg')).not.toBeNull();
  });
});

describe('ProductGallery', () => {
  it('sản phẩm chưa có ảnh thì ghi rõ là ảnh minh họa', () => {
    render(<ProductGallery imageUrls={[]} productId={7} categoryName="Balo du lịch" />);
    expect(screen.getByText(/Ảnh minh họa theo danh mục/)).toBeInTheDocument();
  });

  it('sản phẩm có ảnh thì không có dòng ảnh minh họa', () => {
    render(<ProductGallery imageUrls={['/a.jpg']} productId={7} categoryName="Balo du lịch" />);
    expect(screen.queryByText(/Ảnh minh họa theo danh mục/)).toBeNull();
  });
});
