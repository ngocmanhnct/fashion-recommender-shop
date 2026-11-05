import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderDetailPage from '../../src/pages/OrderDetailPage';
import * as orderApi from '../../src/services/orderApi';
import * as reviewApi from '../../src/services/reviewApi';
import { goToVnPay } from '../../src/services/vnpayRedirect';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/orderApi');
vi.mock('../../src/services/reviewApi');
vi.mock('../../src/services/vnpayRedirect');

const ORDER = {
  id: 55,
  status: 'PENDING',
  paymentMethod: 'COD',
  paymentStatus: 'UNPAID',
  createdAt: '2026-10-29T09:00:00',
  recipientName: 'Mạnh',
  phone: '0901234567',
  addressDetail: 'Thủ Đức',
  items: [
    {
      variantId: 11,
      productId: 3,
      productName: 'Balo du lịch',
      unitPrice: 200000,
      quantity: 2,
      lineTotal: 400000,
    },
  ],
  subtotal: 400000,
  shippingFee: 30000,
  discount: 0,
  total: 430000,
  cancellable: true,
};

function renderPage({ id = '55', state } = {}) {
  return renderWithContext(<OrderDetailPage />, {
    auth: authValue({ user: users.CUSTOMER }),
    path: `/orders/${id}`,
    route: '/orders/:id',
    state,
  });
}

describe('OrderDetailPage', () => {
  beforeEach(() => {
    vi.mocked(orderApi.getOrder).mockResolvedValue(ORDER);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('vừa đặt xong thì báo thành công; khách tự hủy đơn còn chờ xử lý', async () => {
    vi.mocked(orderApi.cancelOrder).mockResolvedValue({
      ...ORDER,
      status: 'CANCELLED',
      cancellable: false,
    });
    renderPage({ state: { placed: true } });
    expect(await screen.findByRole('status')).toHaveTextContent('Mã đơn hàng của bạn là #55');
    expect(screen.getByRole('link', { name: 'Balo du lịch' })).toHaveAttribute(
      'href',
      '/products/3'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Hủy đơn hàng' }));
    expect(orderApi.cancelOrder).toHaveBeenCalledWith(55);
    expect(screen.queryByRole('button', { name: 'Hủy đơn hàng' })).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('đơn có mã giảm giá thì hiện dòng giảm giá kèm mã', async () => {
    vi.mocked(orderApi.getOrder).mockResolvedValue({
      ...ORDER,
      discount: 40000,
      voucherCode: 'CHAOBAN',
      total: 390000,
    });
    renderPage();
    expect(await screen.findByText('Giảm giá (CHAOBAN)')).toBeInTheDocument();
    expect(screen.getByText('−40.000 ₫')).toBeInTheDocument();
  });

  it('hủy lỗi thì hiện lỗi; đơn đã rời kho thì không tự hủy được', async () => {
    vi.mocked(orderApi.cancelOrder).mockRejectedValue({
      response: { data: { detail: 'Đơn đang giao' } },
    });
    const { unmount } = renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Hủy đơn hàng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Đơn đang giao');
    unmount();

    vi.mocked(orderApi.getOrder).mockResolvedValue({
      ...ORDER,
      status: 'SHIPPING',
      cancellable: false,
    });
    renderPage();
    expect(await screen.findByText(/đã rời kho/)).toBeInTheDocument();
  });

  it('đơn VNPay chưa trả thì có nút thanh toán và lỗi lần mở trước', async () => {
    vi.mocked(orderApi.getOrder).mockResolvedValue({
      ...ORDER,
      paymentMethod: 'ONLINE',
      paymentStatus: 'PENDING',
      paymentDeadline: '2026-10-29T09:30:00',
    });
    vi.mocked(goToVnPay).mockResolvedValue(undefined);
    renderPage({ state: { paymentError: 'Không mở được trang thanh toán VNPay' } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Không mở được');
    await userEvent.click(screen.getByRole('button', { name: 'Thanh toán qua VNPay' }));
    expect(goToVnPay).toHaveBeenCalledWith(55, expect.any(Function));
  });

  it('mã sai, không tồn tại, tải lỗi', async () => {
    const bad = renderPage({ id: 'x' });
    expect(screen.getByText('Không tìm thấy đơn hàng')).toBeInTheDocument();
    bad.unmount();

    vi.mocked(orderApi.getOrder).mockRejectedValueOnce({ response: { status: 404 } });
    const missing = renderPage({ id: '9' });
    expect(await screen.findByText('Không tìm thấy đơn hàng')).toBeInTheDocument();
    missing.unmount();

    vi.mocked(orderApi.getOrder).mockRejectedValueOnce({ response: { status: 500 } });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('heading', { name: 'Đơn #55' })).toBeInTheDocument();
  });

  it('đơn Đã giao: đánh giá ngay dưới dòng đơn, sản phẩm đã đánh giá thì sửa', async () => {
    const user = userEvent.setup();
    vi.mocked(orderApi.getOrder).mockResolvedValueOnce({
      ...ORDER,
      status: 'DELIVERED',
      cancellable: false,
      items: [
        ...ORDER.items,
        { ...ORDER.items[0], variantId: 12, productName: 'Balo du lịch (xám)' },
        { ...ORDER.items[0], variantId: 13, productId: 4, productName: 'Ví nam' },
      ],
    });
    vi.mocked(reviewApi.getPendingReviews).mockResolvedValue({
      total: 1,
      items: [{ productId: 3, productName: 'Balo du lịch', orderId: 55 }],
    });
    vi.mocked(reviewApi.getMyReview).mockResolvedValue({ canReview: true, review: null });
    vi.mocked(reviewApi.saveMyReview).mockResolvedValue({ rating: 5 });
    renderPage();

    // 1 nút cho mỗi sản phẩm, dù sản phẩm 3 có 2 dòng (2 phân loại)
    expect(await screen.findAllByRole('button', { name: 'Đánh giá ngay' })).toHaveLength(1);
    expect(screen.getAllByRole('button', { name: 'Sửa đánh giá' })).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Đánh giá ngay' }));
    await user.click(await screen.findByLabelText('5 sao'));
    await user.click(screen.getByRole('button', { name: 'Gửi đánh giá' }));
    expect(reviewApi.saveMyReview).toHaveBeenCalledWith(3, { rating: 5, comment: '' });
    expect(await screen.findByText('Đã lưu đánh giá của bạn.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(screen.getAllByRole('button', { name: 'Sửa đánh giá' })).toHaveLength(2);
  });

  it('đơn chưa giao thì không có ô đánh giá và không tải danh sách chờ đánh giá', async () => {
    renderPage();
    expect(await screen.findByRole('link', { name: 'Balo du lịch' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /đánh giá/i })).not.toBeInTheDocument();
    expect(reviewApi.getPendingReviews).not.toHaveBeenCalled();
  });
});
