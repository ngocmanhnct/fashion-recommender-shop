import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CartPage from '../../src/pages/CartPage';
import { getBoughtTogether } from '../../src/services/productApi';
import { logRecommendationEvent } from '../../src/services/recommendationApi';
import { cartValue, renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/productApi');
vi.mock('../../src/services/recommendationApi');

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

const TOGETHER = [
  {
    product: { id: 8, name: 'Ví da nam', minPrice: 150000, maxPrice: 150000, inStock: true },
    reason: 'Khách mua Balo du lịch cũng hay mua',
    source: 'RELATED',
  },
];

describe('CartPage', () => {
  beforeEach(() => {
    getBoughtTogether.mockResolvedValue([]);
    logRecommendationEvent.mockResolvedValue(undefined);
  });

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

  it('hiện "Thường được mua cùng" theo các sản phẩm trong giỏ', async () => {
    getBoughtTogether.mockResolvedValue(TOGETHER);
    const second = { ...LINE, variantId: 12, variantLabel: 'Xám', quantity: 1 };
    const other = { ...LINE, variantId: 13, productId: 1, productName: 'Túi đeo chéo' };
    renderCart({ cart: { ...CART, items: [LINE, second, other] } });
    expect(await screen.findByRole('heading', { name: 'Thường được mua cùng' })).toBeVisible();
    expect(screen.getByText('Khách mua Balo du lịch cũng hay mua')).toBeInTheDocument();
    // Mỗi sản phẩm 1 lần, tăng dần, dù có 2 dòng cùng sản phẩm.
    expect(getBoughtTogether).toHaveBeenCalledWith([1, 3]);
    expect(logRecommendationEvent).toHaveBeenCalledWith('SHOWN', 'CART', [
      { productId: 8, source: 'RELATED', rank: 1 },
    ]);
  });

  it('chưa ai mua cùng hoặc lỗi thì ẩn khối; giỏ trống thì không hỏi', async () => {
    const { unmount } = renderCart();
    await waitFor(() => expect(getBoughtTogether).toHaveBeenCalledWith([3]));
    expect(screen.queryByText('Thường được mua cùng')).not.toBeInTheDocument();
    unmount();

    getBoughtTogether.mockRejectedValue(new Error('Network Error'));
    const failed = renderCart();
    await waitFor(() => expect(getBoughtTogether).toHaveBeenCalledTimes(2));
    expect(screen.queryByText('Thường được mua cùng')).not.toBeInTheDocument();
    failed.unmount();

    renderCart({ cart: { ...CART, items: [], totalQuantity: 0, subtotal: 0 } });
    expect(getBoughtTogether).toHaveBeenCalledTimes(2);
  });
});
