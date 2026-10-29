import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import CartPage from '../../src/pages/CartPage';
import { cartValue, renderWithContext } from '../renderWithContext';

const LINE = {
  variantId: 11,
  productId: 3,
  productName: 'Balo du lịch',
  categoryName: 'Balo',
  variantLabel: 'Đen',
  unitPrice: 200000,
  quantity: 2,
  stock: 5,
  lineTotal: 400000,
};
const CART = { items: [LINE], notices: [], totalQuantity: 2, subtotal: 400000 };

function renderCart(overrides = {}) {
  return renderWithContext(<CartPage />, {
    cart: cartValue({ cart: CART, count: 2, ...overrides }),
    path: '/cart',
  });
}

describe('CartPage', () => {
  it('mở trang là tải lại giỏ, hiện dòng và tạm tính', () => {
    const { cart } = renderCart();
    expect(cart.refresh).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Balo du lịch' })).toHaveAttribute(
      'href',
      '/products/3'
    );
    expect(screen.getByText('Phân loại: Đen')).toBeInTheDocument();
    expect(screen.getByText('Tạm tính (2 sản phẩm)')).toBeInTheDocument();
  });

  it('đổi số lượng, xóa dòng; lỗi hiện ngay ở dòng đó', async () => {
    const { cart } = renderCart({
      removeItem: vi.fn(() => Promise.reject({ response: { data: { detail: 'Không xóa được' } } })),
    });
    await userEvent.click(screen.getByRole('button', { name: 'Tăng số lượng' }));
    expect(cart.updateQuantity).toHaveBeenCalledWith(11, 3);
    await userEvent.click(screen.getByRole('button', { name: 'Xóa' }));
    expect(cart.removeItem).toHaveBeenCalledWith(11);
    expect(await screen.findByRole('alert')).toHaveTextContent('Không xóa được');
  });

  it('khách chưa đăng nhập bấm Mua hàng thì đăng nhập rồi quay lại thanh toán', async () => {
    renderCart();
    await userEvent.click(screen.getByRole('button', { name: 'Mua hàng' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/login {"from":"/checkout"}');
  });

  it('khách hàng bấm Mua hàng thì sang thanh toán', async () => {
    renderCart({ mode: 'customer' });
    await userEvent.click(screen.getByRole('button', { name: 'Mua hàng' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/checkout');
  });

  it('hiện thông báo dòng bị loại, bấm Đã hiểu thì đóng', async () => {
    const notice = {
      variantId: 12,
      type: 'REMOVED',
      productName: 'Ví da',
      variantLabel: 'Nâu',
      message: 'Sản phẩm đã hết hàng nên được bỏ khỏi giỏ.',
    };
    const { cart } = renderCart({ notices: [notice] });
    expect(screen.getByRole('status')).toHaveTextContent('Ví da (Nâu):');
    await userEvent.click(screen.getByRole('button', { name: 'Đã hiểu' }));
    expect(cart.dismissNotices).toHaveBeenCalled();
  });

  it('giỏ trống, đang tải, tải lỗi, quản trị viên', async () => {
    const { unmount } = renderCart({ cart: { ...CART, items: [] } });
    expect(screen.getByText('Giỏ hàng của bạn đang trống.')).toBeInTheDocument();
    unmount();

    const loading = renderCart({ cart: null, loading: true });
    expect(screen.getByText('Đang tải giỏ hàng...')).toBeInTheDocument();
    loading.unmount();

    const failed = renderCart({ cart: null, error: { response: { status: 500 } } });
    expect(screen.getByText('Không tải được giỏ hàng')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(failed.cart.refresh).toHaveBeenCalledTimes(2);
    failed.unmount();

    renderCart({ mode: 'none', cart: null });
    expect(screen.getByText('Tài khoản quản trị không dùng giỏ hàng.')).toBeInTheDocument();
  });
});
