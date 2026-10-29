import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import Pagination from '../../src/components/Pagination';
import ProductArt from '../../src/components/ProductArt';
import ProductCard from '../../src/components/ProductCard';
import ProductImage from '../../src/components/ProductImage';
import SegmentBadge from '../../src/components/SegmentBadge';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';

const PRODUCT = {
  id: 42,
  name: 'Giày thể thao nam',
  categoryName: 'Giày thể thao nam',
  minPrice: 250000,
  maxPrice: 300000,
  inStock: true,
  ratingAverage: 4.5,
  soldCount: 1200,
};

function renderCard(product, reason) {
  return render(
    <MemoryRouter>
      <ProductCard product={product} reason={reason} />
    </MemoryRouter>
  );
}

describe('ProductCard', () => {
  it('hiện tên, khoảng giá, điểm đánh giá, số đã bán, lý do gợi ý', () => {
    renderCard(PRODUCT, 'Cùng thương hiệu');
    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/products/42');
    expect(link).toHaveTextContent('Giày thể thao nam');
    expect(link).toHaveTextContent(/250\.000/);
    expect(link).toHaveTextContent(/300\.000/);
    expect(link).toHaveTextContent('4,5 trên 5 sao');
    expect(link).toHaveTextContent('Đã bán 1.200');
    expect(link).toHaveTextContent('Cùng thương hiệu');
    expect(screen.queryByText('Hết hàng')).not.toBeInTheDocument();
  });

  it('chưa có đánh giá, chưa bán thì ẩn; hết hàng thì có nhãn', () => {
    renderCard({ ...PRODUCT, ratingAverage: null, soldCount: 0, inStock: false });
    expect(screen.getByRole('link')).not.toHaveTextContent('sao');
    expect(screen.getByRole('link')).not.toHaveTextContent('Đã bán');
    expect(screen.getByText('Hết hàng')).toBeInTheDocument();
  });
});

describe('ProductImage', () => {
  it('có ảnh thì hiện ảnh, không có thì vẽ minh họa, thiếu mã thì khung trống', () => {
    const { container, rerender } = render(<ProductImage src="/a.jpg" productId={1} />);
    expect(container.querySelector('img')).toHaveAttribute('src', '/a.jpg');
    rerender(<ProductImage productId={1} categoryName="Balo" />);
    expect(container.querySelector('svg.product-art')).toBeInTheDocument();
    rerender(<ProductImage />);
    expect(container.querySelector('.product-image-empty')).toBeInTheDocument();
  });
});

describe('ProductArt', () => {
  const CATEGORIES = [
    'Giày thể thao nam',
    'Boots nữ',
    'Giày cao gót',
    'Giày đế xuồng',
    'Giày búp bê',
    'Giày lười nam',
    'Giày tây nam',
    'Dụng cụ vệ sinh giày',
    'Sandal nữ',
    'Dép nam',
    'Balo nữ',
    'Cặp xách',
    'Túi tote',
    'Túi đeo chéo',
    'Túi đựng đồ du lịch',
    'Túi xách nữ',
    'Vali',
    'Ví nam',
    'Thắt lưng nam',
    'Kính mát',
    'Gọng kính',
    'Nón nữ',
    'Nón nam',
    'Khăn choàng',
    'Khẩu trang',
    'Găng tay',
    'Vớ',
    'Ghim cài áo',
    'Phụ kiện tóc',
    'Gối du lịch',
    'Danh mục chưa có hình',
  ];

  it.each(CATEGORIES)('vẽ được hình cho danh mục "%s"', (categoryName) => {
    const { container } = render(
      <>
        {[1, 2, 3, 4, 5, 6, 7, 8].map((id) => (
          <ProductArt key={id} productId={id} categoryName={categoryName} />
        ))}
      </>
    );
    expect(container.querySelectorAll('svg')).toHaveLength(8);
    expect(container.querySelectorAll('svg g')[0].childElementCount).toBeGreaterThan(0);
  });

  it('cùng mã sản phẩm thì cùng hình, khác mã thì khác', () => {
    const html = (id) =>
      render(<ProductArt productId={id} categoryName="Balo" />).container.innerHTML.replace(
        /art-[\w-]+/g,
        'art'
      );
    expect(html(5)).toBe(html(5));
    expect(html(5)).not.toBe(html(6));
  });
});

describe('Pagination, SegmentBadge', () => {
  it('ẩn khi chỉ 1 trang, chuyển trang bằng nút Trước/Sau', async () => {
    const onChange = vi.fn();
    const { container, rerender } = render(
      <Pagination page={0} totalPages={1} onChange={onChange} />
    );
    expect(container).toBeEmptyDOMElement();
    rerender(<Pagination page={0} totalPages={3} onChange={onChange} />);
    expect(screen.getByRole('button', { name: '‹ Trước' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Sau ›' }));
    expect(onChange).toHaveBeenCalledWith(1);
    expect(screen.getByText('Trang 1/3')).toBeInTheDocument();
  });

  it('nhãn nhóm theo mã, chưa phân nhóm theo BR-24', () => {
    const { rerender } = render(<SegmentBadge segment={null} />);
    expect(screen.getByText('Khách hàng mới / chưa đủ dữ liệu')).toBeInTheDocument();
    rerender(<SegmentBadge segment={{ code: 'AT_RISK', name: 'Có nguy cơ rời bỏ' }} />);
    expect(screen.getByText('Có nguy cơ rời bỏ')).toHaveClass('segment-at-risk');
  });
});
