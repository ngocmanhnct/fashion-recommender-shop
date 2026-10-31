import { screen, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomePage from '../../src/pages/HomePage';
import * as catalogApi from '../../src/services/catalogApi';
import * as productApi from '../../src/services/productApi';
import { getMyVouchers } from '../../src/services/voucherApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/productApi');
vi.mock('../../src/services/catalogApi');
vi.mock('../../src/services/voucherApi');
// Khối gợi ý riêng có test riêng; ở đây chỉ cần biết nó được đặt vào trang.
vi.mock('../../src/components/PersonalRecommendations', () => ({
  default: () => <p>Khối gợi ý cho bạn</p>,
}));

const product = (id, name) => ({
  id,
  name,
  minPrice: 100000,
  maxPrice: 100000,
  inStock: true,
  categoryName: 'Giày tây nam',
});

describe('HomePage', () => {
  beforeEach(() => {
    vi.mocked(productApi.searchProducts).mockResolvedValue({
      content: [product(5, 'Giày A'), product(6, 'Túi B'), product(7, 'Ví C'), product(8, 'Nón D')],
      page: 0,
      totalPages: 1,
      totalElements: 1050,
    });
    vi.mocked(catalogApi.getCategories).mockResolvedValue([
      { id: 1, name: 'Giày boots nam' },
      { id: 2, name: 'Giày boots nữ' },
      { id: 3, name: 'Giày tây nam' },
      { id: 4, name: 'Giày lười nam' },
      { id: 5, name: 'Giày lười nữ' },
      { id: 6, name: 'Khăn' },
    ]);
  });

  it('phần mở đầu có số sản phẩm và 3 sản phẩm bán chạy nhất', async () => {
    renderWithContext(<HomePage />);
    expect(
      screen.getByRole('heading', { level: 1, name: 'Đồ hợp với bạn, tìm thấy nhanh hơn.' })
    ).toBeInTheDocument();
    expect(await screen.findByText(/1\.050 sản phẩm đang bán/)).toBeInTheDocument();
    const collage = screen.getByRole('list', { name: 'Bán chạy nhất' });
    const cards = within(collage).getAllByRole('link');
    expect(cards).toHaveLength(3);
    expect(cards[0]).toHaveAttribute('href', '/products/5');
    expect(cards[0]).toHaveTextContent('01Giày A');
    expect(cards[2]).toHaveTextContent('03Ví C');
    expect(screen.getByText('Khối gợi ý cho bạn')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Gợi ý hoạt động thế nào?' })).toHaveAttribute(
      'href',
      '#cach-goi-y'
    );
  });

  it('nhóm danh mục hiện tối đa 4 danh mục con và số còn lại', async () => {
    renderWithContext(<HomePage />);
    const heading = await screen.findByRole('heading', { name: 'Giày' });
    const card = heading.closest('li');
    expect(within(card).getAllByRole('link')).toHaveLength(4);
    expect(within(card).getByText('+ 1 danh mục khác')).toBeInTheDocument();
    const accessories = screen.getByRole('heading', { name: 'Phụ kiện' }).closest('li');
    expect(within(accessories).getByRole('link', { name: 'Khăn' })).toHaveAttribute(
      'href',
      '/products?categoryId=6'
    );
  });

  it('khách có mã được tặng thì thấy dải báo dẫn tới "Ưu đãi của bạn"', async () => {
    vi.mocked(getMyVouchers).mockResolvedValue([
      {
        code: 'NEW-TUDONG-3110',
        discountType: 'PERCENT',
        discountValue: 15,
        maxDiscountAmount: 50000,
        validUntil: '2026-11-14T09:00:00',
      },
      { code: 'LOYAL-3110', discountType: 'FIXED', discountValue: 50000 },
    ]);
    renderWithContext(<HomePage />, { auth: authValue({ user: users.CUSTOMER }) });
    const banner = await screen.findByRole('link', { name: /Bạn có 2 mã giảm giá/ });
    expect(banner).toHaveAttribute('href', '/offers');
    expect(banner).toHaveTextContent('NEW-TUDONG-3110: Giảm 15%');
    expect(banner).toHaveTextContent('dùng đến 14/11/2026 09:00');
  });

  it('khách chưa có mã thì không có dải báo', async () => {
    vi.mocked(getMyVouchers).mockResolvedValue([]);
    renderWithContext(<HomePage />, { auth: authValue({ user: users.CUSTOMER }) });
    expect(await screen.findByText(/1\.050 sản phẩm đang bán/)).toBeInTheDocument();
    expect(getMyVouchers).toHaveBeenCalled();
    expect(screen.queryByRole('link', { name: /mã giảm giá/ })).not.toBeInTheDocument();
  });

  it('các bước giải thích cách gợi ý được đánh số', () => {
    renderWithContext(<HomePage />);
    const steps = screen.getByRole('heading', {
      name: 'Càng mua sắm, gợi ý càng đúng ý bạn',
    }).parentElement;
    expect(within(steps).getByText('02')).toBeInTheDocument();
    expect(within(steps).getByRole('link', { name: 'Làm khảo sát sở thích' })).toHaveAttribute(
      'href',
      '/survey'
    );
  });

  it('quản trị viên có lối vào trang quản trị; chưa có sản phẩm thì hiện khung trống', async () => {
    vi.mocked(productApi.searchProducts).mockResolvedValue({
      content: [],
      page: 0,
      totalPages: 0,
      totalElements: 0,
    });
    vi.mocked(catalogApi.getCategories).mockResolvedValue([]);
    const { container } = renderWithContext(<HomePage />, {
      auth: authValue({ user: users.ADMIN }),
    });
    expect(screen.getByRole('link', { name: 'Vào trang quản trị' })).toHaveAttribute(
      'href',
      '/admin'
    );
    expect(await screen.findByText(/Cửa hàng ghi nhận/)).not.toHaveTextContent('sản phẩm đang bán');
    expect(container.querySelector('.hero-collage.is-empty')).not.toBeNull();
    expect(screen.queryByRole('heading', { name: 'Mua theo nhóm' })).not.toBeInTheDocument();
  });
});
