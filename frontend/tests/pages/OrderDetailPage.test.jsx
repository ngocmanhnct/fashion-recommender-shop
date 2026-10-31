import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderDetailPage from '../../src/pages/OrderDetailPage';
import * as orderApi from '../../src/services/orderApi';
import { goToVnPay } from '../../src/services/vnpayRedirect';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/orderApi');
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

  it('đơn Đã giao có link đánh giá từng sản phẩm, đơn chưa giao thì không', async () => {
    vi.mocked(orderApi.getOrder).mockResolvedValueOnce({
      ...ORDER,
      status: 'DELIVERED',
      cancellable: false,
    });
    const { unmount } = renderPage();
    expect(await screen.findByRole('link', { name: 'Đánh giá sản phẩm' })).toHaveAttribute(
      'href',
      '/products/3#danh-gia'
    );
    unmount();

    renderPage();
    expect(await screen.findByRole('link', { name: 'Balo du lịch' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Đánh giá sản phẩm' })).not.toBeInTheDocument();
  });
});
