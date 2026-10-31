import { fireEvent, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Layout from '../../src/components/Layout';
import * as catalogApi from '../../src/services/catalogApi';
import { authValue, cartValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/catalogApi');

function renderLayout(options = {}) {
  return renderWithContext(<Layout />, { path: '/cart', ...options });
}

const categoryMenu = () => screen.getByRole('navigation', { name: 'Danh mục sản phẩm' });

describe('Layout', () => {
  beforeEach(() => {
    vi.mocked(catalogApi.getCategories).mockResolvedValue([
      { id: 1, name: 'Giày boots nam' },
      { id: 2, name: 'Giày tây nam' },
      { id: 3, name: 'Ví nữ' },
    ]);
  });

  it('khách chưa đăng nhập thấy giỏ hàng, đăng nhập, đăng ký và chân trang', () => {
    renderLayout({ cart: cartValue({ count: 3 }) });
    const header = screen.getByRole('navigation', { name: 'Tài khoản' });
    expect(within(header).getByRole('link', { name: /^Giỏ hàng/ })).toHaveAttribute(
      'href',
      '/cart'
    );
    expect(within(header).getByLabelText('3 sản phẩm')).toHaveTextContent('3');
    expect(within(header).getByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(within(header).getByRole('link', { name: 'Đăng ký' })).toBeInTheDocument();
    expect(screen.queryByText(/Xin chào/)).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Kiểm tra kết nối backend' })).toHaveAttribute(
      'href',
      '/health'
    );
  });

  it('giỏ hơn 99 sản phẩm hiện 99+', () => {
    renderLayout({ cart: cartValue({ count: 120 }) });
    expect(screen.getByLabelText('120 sản phẩm')).toHaveTextContent('99+');
  });

  it('khách hàng thấy lời chào, lối tắt riêng và đăng xuất về trang chủ', async () => {
    const auth = authValue({ user: users.CUSTOMER });
    renderLayout({ auth, cart: cartValue({ mode: 'account' }) });
    expect(screen.getByText('Xin chào, Khách Thử')).toBeInTheDocument();
    const header = screen.getByRole('navigation', { name: 'Tài khoản' });
    expect(within(header).getByRole('link', { name: 'Yêu thích' })).toHaveAttribute(
      'href',
      '/wishlist'
    );
    expect(within(header).getByRole('link', { name: 'Đơn hàng' })).toHaveAttribute(
      'href',
      '/orders'
    );
    expect(within(header).queryByRole('link', { name: 'Đăng nhập' })).not.toBeInTheDocument();

    await userEvent.click(screen.getAllByRole('button', { name: 'Đăng xuất' })[0]);
    expect(auth.logout).toHaveBeenCalled();
    expect(screen.getByTestId('location')).toHaveTextContent('/');
  });

  it('quản trị viên thấy lối vào trang quản trị, không có giỏ hàng', () => {
    renderLayout({ auth: authValue({ user: users.ADMIN }), cart: cartValue({ mode: 'none' }) });
    const header = screen.getByRole('navigation', { name: 'Tài khoản' });
    expect(within(header).getByRole('link', { name: 'Quản trị' })).toHaveAttribute(
      'href',
      '/admin'
    );
    expect(within(header).queryByRole('link', { name: /Giỏ hàng/ })).not.toBeInTheDocument();
  });

  it('nút menu mở và đóng ngăn kéo có các nhóm danh mục', async () => {
    renderLayout({ auth: authValue({ user: users.ADMIN }), cart: cartValue({ mode: 'none' }) });
    const toggle = screen.getByRole('button', { name: 'Mở menu' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(toggle).toHaveAccessibleName('Đóng menu');
    const drawer = document.getElementById(toggle.getAttribute('aria-controls'));
    expect(await within(drawer).findByText('Giày')).toBeInTheDocument();
    expect(within(drawer).getByRole('link', { name: 'Giày tây nam' })).toHaveAttribute(
      'href',
      '/products?categoryId=2'
    );
    expect(within(drawer).getByText('Tài khoản của Quản Trị')).toBeInTheDocument();
    expect(within(drawer).getByRole('link', { name: 'Quản trị' })).toBeInTheDocument();

    // Bấm vào vùng tối bên ngoài ngăn kéo thì đóng.
    await userEvent.click(document.querySelector('.drawer-backdrop'));
    expect(toggle).toHaveAttribute('aria-expanded', 'false');
  });

  it('ngăn kéo của khách hàng có đơn hàng, sổ địa chỉ; bấm liên kết là chuyển trang', async () => {
    renderLayout({
      auth: authValue({ user: users.CUSTOMER }),
      cart: cartValue({ mode: 'account' }),
    });
    const toggle = screen.getByRole('button', { name: 'Mở menu' });
    await userEvent.click(toggle);
    const drawer = document.getElementById(toggle.getAttribute('aria-controls'));
    expect(within(drawer).getByRole('link', { name: 'Sổ địa chỉ' })).toHaveAttribute(
      'href',
      '/addresses'
    );
    await userEvent.click(within(drawer).getByRole('link', { name: 'Đơn hàng' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/orders');
  });

  it('ngăn kéo của khách chưa đăng nhập có nút đăng nhập, đăng ký', async () => {
    renderLayout();
    await userEvent.click(screen.getByRole('button', { name: 'Mở menu' }));
    const drawer = document.querySelector('.drawer-panel');
    expect(within(drawer).getByRole('link', { name: 'Đăng nhập' })).toHaveAttribute(
      'href',
      '/login'
    );
  });

  it('menu danh mục mở bảng danh mục con khi bấm, rê chuột; Esc và bấm ra ngoài thì đóng', async () => {
    renderLayout();
    const menu = categoryMenu();
    const shoes = await within(menu).findByRole('button', { name: 'Giày' });
    expect(within(menu).getByRole('button', { name: 'Túi & ví' })).toBeInTheDocument();
    expect(shoes).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(shoes);
    expect(shoes).toHaveAttribute('aria-expanded', 'true');
    expect(within(menu).getByRole('link', { name: 'Giày boots nam' })).toHaveAttribute(
      'href',
      '/products?categoryId=1'
    );

    fireEvent.keyDown(shoes, { key: 'Escape' });
    expect(shoes).toHaveAttribute('aria-expanded', 'false');
    expect(shoes).toHaveFocus();

    fireEvent.mouseEnter(shoes.parentElement);
    expect(shoes).toHaveAttribute('aria-expanded', 'true');
    fireEvent.pointerDown(document.body);
    expect(shoes).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(shoes);
    await userEvent.click(shoes);
    expect(shoes).toHaveAttribute('aria-expanded', 'false');

    fireEvent.mouseEnter(shoes.parentElement);
    fireEvent.mouseLeave(menu);
    expect(shoes).toHaveAttribute('aria-expanded', 'false');
  });

  it('bấm danh mục con thì đóng bảng và chuyển tới danh sách sản phẩm', async () => {
    renderLayout();
    const menu = categoryMenu();
    await userEvent.click(await within(menu).findByRole('button', { name: 'Túi & ví' }));
    await userEvent.click(within(menu).getByRole('link', { name: 'Ví nữ' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/products?categoryId=3');
  });

  it('bấm nút thì hiện vòng gợn sóng rồi tự xóa', () => {
    renderLayout();
    const button = screen.getByRole('button', { name: 'Tìm' });
    fireEvent.pointerDown(button, { button: 0, clientX: 10, clientY: 10 });
    const ripple = button.querySelector('.ripple');
    expect(ripple).not.toBeNull();
    fireEvent.animationEnd(ripple);
    expect(button.querySelector('.ripple')).toBeNull();

    // Chuột phải, phần tử không có hiệu ứng: không vẽ gì.
    fireEvent.pointerDown(button, { button: 2 });
    fireEvent.pointerDown(screen.getByText(/Miễn phí giao hàng cho đơn/), { button: 0 });
    expect(document.querySelectorAll('.ripple')).toHaveLength(0);
  });

  it('không vẽ gợn sóng khi người dùng bật giảm chuyển động', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    try {
      renderLayout();
      fireEvent.pointerDown(screen.getByRole('button', { name: 'Tìm' }), { button: 0 });
      expect(document.querySelectorAll('.ripple')).toHaveLength(0);
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
