import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import WishlistButton from '../../src/components/WishlistButton';
import { addToWishlist, getWishlistIds, removeFromWishlist } from '../../src/services/wishlistApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/wishlistApi');

function renderButton(user = users.CUSTOMER) {
  return renderWithContext(<WishlistButton productId={3} />, {
    auth: authValue({ user }),
    path: '/products/3',
  });
}

describe('WishlistButton', () => {
  beforeEach(() => {
    getWishlistIds.mockResolvedValue([1]);
    addToWishlist.mockResolvedValue([1, 3]);
    removeFromWishlist.mockResolvedValue([1]);
  });

  it('khách hàng bấm để thêm rồi bỏ yêu thích', async () => {
    renderButton();
    const button = await screen.findByRole('button', { name: /Yêu thích/ });
    await waitFor(() => expect(button).toBeEnabled());
    expect(button).toHaveAttribute('aria-pressed', 'false');

    await userEvent.click(button);
    expect(addToWishlist).toHaveBeenCalledWith(3);
    expect(await screen.findByRole('button', { name: /Đã yêu thích/ })).toHaveAttribute(
      'aria-pressed',
      'true'
    );

    await userEvent.click(screen.getByRole('button', { name: /Đã yêu thích/ }));
    expect(removeFromWishlist).toHaveBeenCalledWith(3);
    expect(await screen.findByRole('button', { name: 'Yêu thích' })).toBeInTheDocument();
  });

  it('sản phẩm đã có trong danh sách thì hiện "Đã yêu thích"; lỗi hiện ngay dưới nút', async () => {
    getWishlistIds.mockResolvedValue([3]);
    removeFromWishlist.mockRejectedValue({ response: { data: { detail: 'Lỗi máy chủ' } } });
    renderButton();
    const button = await screen.findByRole('button', { name: /Đã yêu thích/ });
    await userEvent.click(button);
    expect(await screen.findByRole('alert')).toHaveTextContent('Lỗi máy chủ');
    expect(button).toHaveAttribute('aria-pressed', 'true');
  });

  it('khách chưa đăng nhập bấm thì đăng nhập rồi quay lại sản phẩm', async () => {
    renderButton(null);
    await userEvent.click(screen.getByRole('button', { name: /Yêu thích/ }));
    expect(getWishlistIds).not.toHaveBeenCalled();
    expect(screen.getByTestId('location')).toHaveTextContent('/login {"from":"/products/3"}');
  });

  it('quản trị viên không thấy nút', () => {
    renderButton(users.ADMIN);
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});
