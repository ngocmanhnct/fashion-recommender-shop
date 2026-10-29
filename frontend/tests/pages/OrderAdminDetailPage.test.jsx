import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderAdminDetailPage from '../../src/pages/admin/OrderAdminDetailPage';
import * as orderAdminApi from '../../src/services/orderAdminApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/orderAdminApi');

const ORDER = {
  id: 55,
  status: 'PENDING',
  paymentMethod: 'COD',
  paymentStatus: 'UNPAID',
  createdAt: '2026-10-29T09:00:00',
  updatedAt: '2026-10-29T09:00:00',
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
};
const DETAIL = {
  order: ORDER,
  customerId: 7,
  customerName: 'Khách Thử',
  customerEmail: 'khach@example.com',
  nextStatus: 'PROCESSING',
  adminCancellable: true,
};

function renderPage(id = '55') {
  return renderWithContext(<OrderAdminDetailPage />, {
    auth: authValue({ user: users.ADMIN }),
    path: `/admin/orders/${id}`,
    route: '/admin/orders/:id',
  });
}

describe('OrderAdminDetailPage', () => {
  beforeEach(() => {
    vi.mocked(orderAdminApi.getAdminOrder).mockResolvedValue(DETAIL);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('chuyển đơn sang trạng thái kế tiếp sau khi xác nhận', async () => {
    vi.mocked(orderAdminApi.changeOrderStatus).mockResolvedValue({
      ...DETAIL,
      order: { ...ORDER, status: 'PROCESSING' },
      nextStatus: 'SHIPPING',
    });
    renderPage();
    expect(await screen.findByRole('heading', { name: 'Đơn #55' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Khách Thử' })).toHaveAttribute(
      'href',
      '/admin/customers/7'
    );
    await userEvent.click(screen.getByRole('button', { name: /^Chuyển sang/ }));
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Chuyển đơn #55 sang'));
    expect(orderAdminApi.changeOrderStatus).toHaveBeenCalledWith(55, 'PROCESSING');
    expect(await screen.findByRole('button', { name: /^Chuyển sang/ })).not.toHaveTextContent(
      'Đang xử lý'
    );
  });

  it('không xác nhận thì không đổi; hủy đơn lỗi thì hiện lỗi', async () => {
    vi.mocked(window.confirm).mockReturnValueOnce(false);
    vi.mocked(orderAdminApi.changeOrderStatus).mockRejectedValue({
      response: { status: 409, data: { detail: 'Đơn đã giao, không hủy được' } },
    });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: /^Chuyển sang/ }));
    expect(orderAdminApi.changeOrderStatus).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Hủy đơn' }));
    expect(window.confirm).toHaveBeenLastCalledWith(expect.stringContaining('Tồn kho'));
    expect(orderAdminApi.changeOrderStatus).toHaveBeenCalledWith(55, 'CANCELLED');
    expect(await screen.findByRole('alert')).toHaveTextContent('Đơn đã giao, không hủy được');
  });

  it('đơn đã kết thúc thì không còn nút đổi trạng thái', async () => {
    vi.mocked(orderAdminApi.getAdminOrder).mockResolvedValue({
      ...DETAIL,
      order: { ...ORDER, status: 'DELIVERED' },
      nextStatus: null,
      adminCancellable: false,
    });
    renderPage();
    expect(await screen.findByText(/không đổi trạng thái được nữa/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Hủy đơn' })).not.toBeInTheDocument();
  });

  it('hoàn tiền tự động thất bại thì xác nhận hoàn tiền thủ công', async () => {
    const order = {
      ...ORDER,
      status: 'CANCELLED',
      paymentMethod: 'ONLINE',
      paymentStatus: 'PAID',
      refund: { status: 'FAILED', amount: 430000, failureReason: 'VNPay từ chối' },
    };
    vi.mocked(orderAdminApi.getAdminOrder).mockResolvedValue({
      ...DETAIL,
      order,
      nextStatus: null,
      adminCancellable: false,
    });
    vi.mocked(orderAdminApi.completeRefundManually).mockResolvedValue({
      ...DETAIL,
      order: { ...order, refund: { ...order.refund, status: 'COMPLETED' } },
      nextStatus: null,
      adminCancellable: false,
    });
    renderPage();
    expect(await screen.findByText('VNPay từ chối')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /hoàn tiền thủ công/ }));
    expect(orderAdminApi.completeRefundManually).toHaveBeenCalledWith(55);
    expect(screen.queryByRole('button', { name: /hoàn tiền thủ công/ })).not.toBeInTheDocument();
  });

  it('mã đơn sai hoặc không tồn tại thì báo không tìm thấy', async () => {
    const { unmount } = renderPage('abc');
    expect(screen.getByText('Không tìm thấy đơn hàng')).toBeInTheDocument();
    expect(orderAdminApi.getAdminOrder).not.toHaveBeenCalled();
    unmount();

    vi.mocked(orderAdminApi.getAdminOrder).mockRejectedValue({ response: { status: 404 } });
    renderPage('999');
    expect(await screen.findByText('Không tìm thấy đơn hàng')).toBeInTheDocument();
  });
});
