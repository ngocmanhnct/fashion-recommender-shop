import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductDetailPage from '../../src/pages/ProductDetailPage';
import * as productApi from '../../src/services/productApi';
import * as reviewApi from '../../src/services/reviewApi';
import * as wishlistApi from '../../src/services/wishlistApi';
import { authValue, cartValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/productApi');
vi.mock('../../src/services/recommendationApi');
vi.mock('../../src/services/wishlistApi');
vi.mock('../../src/services/reviewApi');

const variant = (id, option1Value, option2Value, stock, price = 500000) => ({
  id,
  option1Value,
  option2Value,
  stock,
  price,
});

const PRODUCT = {
  id: 3,
  name: 'Giày chạy bộ',
  description: 'Đế êm, nhẹ',
  category: { id: 2, name: 'Giày thể thao nam' },
  brand: { id: 4, name: 'Adidas' },
  imageUrls: ['/a.jpg', '/b.jpg', '/a.jpg'],
  variationName1: 'Màu',
  variationName2: 'Kích cỡ',
  minPrice: 500000,
  maxPrice: 550000,
  inStock: true,
  ratingAverage: 4.2,
  reviewCount: 10,
  soldCount: 25,
  variants: [
    variant(31, 'Đen', '41', 2),
    variant(32, 'Đen', '42', 0),
    variant(33, 'Trắng', '42', 5, 550000),
  ],
};

const RELATED = [
  { product: { id: 8, name: 'Vớ thể thao', minPrice: 50000, maxPrice: 50000, inStock: true } },
];

function renderPage({
  id = '3',
  user = users.CUSTOMER,
  cart = cartValue({ mode: 'customer' }),
} = {}) {
  return renderWithContext(<ProductDetailPage />, {
    auth: authValue({ user }),
    cart,
    path: `/products/${id}`,
    route: '/products/:id',
  });
}

describe('ProductDetailPage', () => {
  beforeEach(() => {
    vi.mocked(productApi.getProduct).mockResolvedValue(PRODUCT);
    vi.mocked(productApi.getRelatedProducts).mockResolvedValue(RELATED);
    vi.mocked(productApi.recordProductView).mockResolvedValue(undefined);
    vi.mocked(wishlistApi.getWishlistIds).mockResolvedValue([]);
    vi.mocked(reviewApi.getProductReviews).mockResolvedValue({
      ratingAverage: null,
      reviewCount: 0,
      starCounts: [0, 0, 0, 0, 0],
      reviews: { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
    });
    vi.mocked(reviewApi.getMyReview).mockResolvedValue({ canReview: false, review: null });
  });

  it('hiện thông tin sản phẩm, ghi 1 lượt xem cho khách hàng và gợi ý liên quan', async () => {
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Giày chạy bộ' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Giày thể thao nam' })).toHaveAttribute(
      'href',
      '/products?categoryId=2'
    );
    expect(screen.getByRole('link', { name: 'Adidas' })).toBeInTheDocument();
    expect(screen.getByText('Đế êm, nhẹ')).toBeInTheDocument();
    expect(screen.getByText(/10 đánh giá/)).toBeInTheDocument();
    // Lượt xem được ghi trong useEffect, có thể chạy sau khi DOM đã cập nhật.
    await waitFor(() => expect(productApi.recordProductView).toHaveBeenCalledTimes(1));
    expect(productApi.recordProductView).toHaveBeenCalledWith(3);
    expect(await screen.findByRole('link', { name: /Vớ thể thao/ })).toBeInTheDocument();
  });

  it('chọn đủ phân loại thì hiện giá, tồn kho và thêm đúng biến thể vào giỏ', async () => {
    const cart = cartValue({ mode: 'customer' });
    renderPage({ cart });
    await screen.findByRole('heading', { name: 'Giày chạy bộ' });
    expect(screen.getByText(/Chọn màu và kích cỡ/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Đen' }));
    // Đen – 42 hết hàng nên 42 bị khóa khi đã chọn Đen.
    expect(screen.getByRole('button', { name: '42, hết hàng' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: '41' }));
    expect(screen.getByText(/Còn 2 sản phẩm/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Thêm vào giỏ hàng' }));
    expect(cart.addItem).toHaveBeenCalledWith(31, 1);

    await userEvent.click(screen.getByRole('button', { name: 'Bỏ chọn phân loại' }));
    expect(screen.getByText(/Chọn màu và kích cỡ/)).toBeInTheDocument();
  });

  it('đổi ảnh lớn bằng dãy ảnh nhỏ, bỏ ảnh trùng', async () => {
    const { container } = renderPage();
    await screen.findByRole('heading', { name: 'Giày chạy bộ' });
    const thumbs = screen.getAllByRole('button', { name: /^Ảnh \d$/ });
    expect(thumbs).toHaveLength(2);
    await userEvent.click(thumbs[1]);
    expect(container.querySelector('.product-gallery-main')).toHaveAttribute('src', '/b.jpg');
  });

  it('chưa đăng nhập không ghi lượt xem; hết hàng thì không có nút thêm', async () => {
    vi.mocked(productApi.getProduct).mockResolvedValue({ ...PRODUCT, inStock: false });
    renderPage({ user: null, cart: cartValue() });
    expect(await screen.findByText(/Sản phẩm này hiện đã bán hết/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Thêm vào giỏ hàng' })).not.toBeInTheDocument();
    expect(productApi.recordProductView).not.toHaveBeenCalled();
  });

  it('mã sai hoặc không tồn tại thì báo không tìm thấy; lỗi khác cho thử lại', async () => {
    const bad = renderPage({ id: 'abc' });
    expect(productApi.getProduct).not.toHaveBeenCalled();
    bad.unmount();

    vi.mocked(productApi.getProduct).mockRejectedValueOnce({ response: { status: 404 } });
    const missing = renderPage({ id: '9' });
    expect(await screen.findByRole('heading', { level: 1 })).toBeInTheDocument();
    expect(screen.queryByText('Giày chạy bộ')).not.toBeInTheDocument();
    missing.unmount();

    vi.mocked(productApi.getProduct).mockRejectedValueOnce({ response: { status: 500 } });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('heading', { name: 'Giày chạy bộ' })).toBeInTheDocument();
  });
});
