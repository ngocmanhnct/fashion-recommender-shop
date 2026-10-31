import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WishlistPage from '../../src/pages/WishlistPage';
import { getWishlist, removeFromWishlist } from '../../src/services/wishlistApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/wishlistApi');

const item = (id, name, inStock = true) => ({
  product: { id, name, minPrice: 200000, maxPrice: 200000, inStock },
  addedAt: '2026-10-31T08:00:00',
});

function renderPage() {
  return renderWithContext(<WishlistPage />, {
    auth: authValue({ user: users.CUSTOMER }),
    path: '/wishlist',
  });
}

describe('WishlistPage', () => {
  beforeEach(() => {
    getWishlist.mockResolvedValue([item(2, 'Túi tote'), item(5, 'Ví da', false)]);
    removeFromWishlist.mockResolvedValue([5]);
  });

  it('hiện sản phẩm yêu thích, hết hàng vẫn hiện, bỏ 1 sản phẩm thì ẩn ngay', async () => {
    renderPage();
    expect(await screen.findByRole('link', { name: /Túi tote/ })).toHaveAttribute(
      'href',
      '/products/2'
    );
    expect(screen.getByText('Hết hàng')).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole('button', { name: 'Bỏ Túi tote khỏi danh sách yêu thích' })
    );
    expect(removeFromWishlist).toHaveBeenCalledWith(2);
    expect(screen.queryByRole('link', { name: /Túi tote/ })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Ví da/ })).toBeInTheDocument();
  });

  it('bỏ lỗi thì báo lỗi và giữ sản phẩm', async () => {
    removeFromWishlist.mockRejectedValue({ response: { data: { detail: 'Mất kết nối' } } });
    renderPage();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Bỏ Ví da khỏi danh sách yêu thích' })
    );
    expect(await screen.findByRole('alert')).toHaveTextContent('Mất kết nối');
    expect(screen.getByRole('link', { name: /Ví da/ })).toBeInTheDocument();
  });

  it('danh sách trống thì mời xem sản phẩm; tải lỗi thì cho thử lại', async () => {
    getWishlist.mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('Network Error'));
    const { unmount } = renderPage();
    expect(await screen.findByText(/Bạn chưa lưu sản phẩm nào/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem sản phẩm' })).toHaveAttribute('href', '/products');
    unmount();

    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('link', { name: /Túi tote/ })).toBeInTheDocument();
    expect(getWishlist).toHaveBeenCalledTimes(3);
  });
});
