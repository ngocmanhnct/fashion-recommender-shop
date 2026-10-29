import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import AddToCart from '../../src/components/AddToCart';
import { cartValue, renderWithContext } from '../renderWithContext';

const VARIANT = { id: 11, stock: 3 };

function renderAdd(props, cart = cartValue()) {
  return renderWithContext(<AddToCart missingTiers={[]} {...props} />, { cart });
}

describe('AddToCart', () => {
  it('quản trị viên không dùng giỏ hàng', () => {
    renderAdd({ variant: VARIANT }, cartValue({ mode: 'none', cart: null }));
    expect(screen.getByText('Tài khoản quản trị không dùng giỏ hàng.')).toBeInTheDocument();
  });

  it('chưa chọn đủ phân loại thì nhắc chọn', async () => {
    const { cart } = renderAdd({ variant: null, missingTiers: ['Kích cỡ', 'Màu'] });
    await userEvent.click(screen.getByRole('button', { name: 'Thêm vào giỏ hàng' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Vui lòng chọn kích cỡ và màu');
    expect(cart.addItem).not.toHaveBeenCalled();
  });

  it('thêm đúng số lượng đã chọn rồi báo thành công', async () => {
    const { cart } = renderAdd({ variant: VARIANT });
    await userEvent.click(screen.getByRole('button', { name: 'Tăng số lượng' }));
    await userEvent.click(screen.getByRole('button', { name: 'Thêm vào giỏ hàng' }));
    expect(cart.addItem).toHaveBeenCalledWith(11, 2);
    expect(await screen.findByRole('status')).toHaveTextContent('Đã thêm vào giỏ hàng.');
    expect(screen.getByRole('link', { name: 'Xem giỏ hàng' })).toHaveAttribute('href', '/cart');
  });

  it('lỗi từ backend hiện ngay cạnh nút', async () => {
    const error = { response: { data: { detail: 'Sản phẩm đã ngừng bán' } } };
    const cart = cartValue({ addItem: vi.fn(() => Promise.reject(error)) });
    renderAdd({ variant: VARIANT }, cart);
    await userEvent.click(screen.getByRole('button', { name: 'Thêm vào giỏ hàng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sản phẩm đã ngừng bán');
  });

  it('số lượng tối đa là tồn kho trừ số đã có trong giỏ', () => {
    const cart = cartValue({ cart: { items: [{ variantId: 11, quantity: 2 }] } });
    renderAdd({ variant: VARIANT }, cart);
    expect(screen.getByText('Giỏ hàng đã có 2 sản phẩm này.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tăng số lượng' })).toBeDisabled();
  });

  it('giỏ đã có bằng tồn kho thì không cho thêm', () => {
    const cart = cartValue({ cart: { items: [{ variantId: 11, quantity: 3 }] } });
    renderAdd({ variant: VARIANT }, cart);
    expect(screen.queryByRole('button', { name: 'Thêm vào giỏ hàng' })).not.toBeInTheDocument();
    expect(screen.getByText(/bằng số lượng còn lại/)).toBeInTheDocument();
  });
});
