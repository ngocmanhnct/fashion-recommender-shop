import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CheckoutPage from '../../src/pages/CheckoutPage';
import * as addressApi from '../../src/services/addressApi';
import * as orderApi from '../../src/services/orderApi';
import { goToVnPay } from '../../src/services/vnpayRedirect';
import { cartValue, renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/addressApi');
vi.mock('../../src/services/orderApi');
vi.mock('../../src/services/vnpayRedirect');

const ITEM = {
  variantId: 11,
  productId: 3,
  productName: 'Balo du lịch',
  variantLabel: 'Đen',
  unitPrice: 200000,
  quantity: 2,
  lineTotal: 400000,
};
const PREVIEW = {
  items: [ITEM],
  notices: [],
  subtotal: 400000,
  shippingFee: 30000,
  total: 430000,
  onlinePaymentAvailable: true,
};
const HOME = { id: 1, recipientName: 'Mạnh', phone: '0901234567', addressDetail: 'Thủ Đức' };
const OFFICE = { id: 2, recipientName: 'Mạnh', phone: '0907654321', addressDetail: 'Quận 1' };

function renderCheckout() {
  return renderWithContext(<CheckoutPage />, {
    cart: cartValue({ mode: 'customer' }),
    path: '/checkout',
  });
}

describe('CheckoutPage', () => {
  beforeEach(() => {
    vi.mocked(orderApi.getCheckoutPreview).mockResolvedValue(PREVIEW);
    vi.mocked(orderApi.newIdempotencyKey).mockReturnValue('lan-dat-1');
    vi.mocked(orderApi.placeOrder).mockResolvedValue({ id: 55 });
    vi.mocked(addressApi.listAddresses).mockResolvedValue([HOME, OFFICE]);
  });

  it('đặt đơn COD với địa chỉ mặc định, tổng tiền đang thấy và mã lần đặt', async () => {
    const { cart } = renderCheckout();
    expect(await screen.findByText('Mua thêm 100.000 ₫ để được miễn phí vận chuyển.')).toBeTruthy();
    await userEvent.click(screen.getByRole('button', { name: 'Đặt hàng' }));
    expect(orderApi.placeOrder).toHaveBeenCalledWith({
      idempotencyKey: 'lan-dat-1',
      paymentMethod: 'COD',
      addressId: 1,
      expectedTotal: 430000,
    });
    expect(cart.refresh).toHaveBeenCalled();
    expect(await screen.findByTestId('location')).toHaveTextContent('/orders/55 {"placed":true}');
  });

  it('chọn địa chỉ khác và VNPay thì chuyển sang trang thanh toán', async () => {
    renderCheckout();
    await userEvent.click(await screen.findByRole('radio', { name: /Quận 1/ }));
    await userEvent.click(screen.getByRole('radio', { name: /VNPay/ }));
    expect(screen.getByText(/tự hủy sau 30 phút/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Đặt hàng và thanh toán' }));
    expect(orderApi.placeOrder).toHaveBeenCalledWith(
      expect.objectContaining({ paymentMethod: 'ONLINE', addressId: 2 })
    );
    expect(goToVnPay).toHaveBeenCalledWith(55, expect.any(Function));
  });

  it('cửa hàng chưa bật VNPay thì chỉ COD; miễn phí vận chuyển', async () => {
    vi.mocked(orderApi.getCheckoutPreview).mockResolvedValue({
      ...PREVIEW,
      subtotal: 600000,
      shippingFee: 0,
      total: 600000,
      onlinePaymentAvailable: false,
    });
    renderCheckout();
    expect(await screen.findByText('Miễn phí')).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /cửa hàng chưa bật/ })).toBeDisabled();
  });

  it('giá vừa đổi (409) thì báo lỗi và tải lại tóm tắt', async () => {
    vi.mocked(orderApi.placeOrder).mockRejectedValue({
      response: { status: 409, data: { detail: 'Giá sản phẩm đã thay đổi' } },
    });
    renderCheckout();
    await userEvent.click(await screen.findByRole('button', { name: 'Đặt hàng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Giá sản phẩm đã thay đổi');
    await waitFor(() => expect(orderApi.getCheckoutPreview).toHaveBeenCalledTimes(2));
  });

  it('chưa có địa chỉ thì thêm địa chỉ mới rồi mới đặt được', async () => {
    vi.mocked(addressApi.listAddresses)
      .mockResolvedValueOnce([])
      .mockResolvedValue([{ ...HOME, id: 9 }]);
    vi.mocked(addressApi.createAddress).mockResolvedValue({ ...HOME, id: 9 });
    renderCheckout();
    expect(await screen.findByText('Hãy thêm địa chỉ giao hàng trước khi đặt.')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Đặt hàng' })).toBeDisabled();

    await userEvent.type(screen.getByLabelText('Tên người nhận'), 'Mạnh');
    await userEvent.type(screen.getByLabelText('Số điện thoại'), '090 123.4567');
    await userEvent.type(screen.getByLabelText(/^Địa chỉ/), 'Thủ Đức');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu địa chỉ' }));
    expect(addressApi.createAddress).toHaveBeenCalledWith({
      recipientName: 'Mạnh',
      phone: '0901234567',
      addressDetail: 'Thủ Đức',
      isDefault: true,
    });
    await waitFor(() => expect(screen.getByRole('button', { name: 'Đặt hàng' })).toBeEnabled());
  });

  it('chờ giỏ hàng tải xong (gộp giỏ sau đăng nhập) rồi mới xem trước đơn (issue #63)', async () => {
    renderWithContext(<CheckoutPage />, {
      cart: cartValue({ mode: 'customer', cart: null, loading: true }),
      path: '/checkout',
    });
    await waitFor(() => expect(addressApi.listAddresses).toHaveBeenCalled());
    expect(orderApi.getCheckoutPreview).not.toHaveBeenCalled();
    expect(screen.getByText('Đang tải...')).toBeInTheDocument();
  });

  it('giỏ trống, tải lỗi', async () => {
    vi.mocked(orderApi.getCheckoutPreview).mockResolvedValueOnce({ ...PREVIEW, items: [] });
    const { unmount } = renderCheckout();
    expect(await screen.findByText('Giỏ hàng của bạn đang trống.')).toBeInTheDocument();
    unmount();

    vi.mocked(orderApi.getCheckoutPreview).mockRejectedValueOnce({ response: { status: 500 } });
    renderCheckout();
    expect(await screen.findByText('Không tải được đơn hàng')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('button', { name: 'Đặt hàng' })).toBeInTheDocument();
  });
});
