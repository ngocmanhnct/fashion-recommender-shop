import { screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AdminLayout from '../../src/components/AdminLayout';
import { renderWithContext } from '../renderWithContext';

describe('AdminLayout', () => {
  it('menu quản trị chia khối theo nhóm, mỗi khối có tiêu đề và mục riêng', () => {
    renderWithContext(<AdminLayout />, { path: '/admin/orders' });
    const menu = screen.getByRole('complementary', { name: 'Menu quản trị' });
    const store = within(menu).getByRole('region', { name: 'Cửa hàng' });
    expect(within(store).getAllByRole('link')).toHaveLength(3);
    const customers = within(menu).getByRole('region', { name: 'Khách hàng' });
    expect(within(customers).getByRole('link', { name: /Phân nhóm khách hàng/ })).toHaveAttribute(
      'href',
      '/admin/segments'
    );
    // Không còn mục trùng tên nhóm "Khách hàng".
    expect(within(customers).queryByRole('link', { name: /^Khách hàng/ })).toBeNull();
    expect(within(menu).getByRole('link', { name: /Đơn hàng/ })).toHaveClass('active');
  });

  it('trang con vẫn đánh dấu mục cha và hiện vị trí nhóm › mục', () => {
    renderWithContext(<AdminLayout />, { path: '/admin/segments/VIP', route: '/admin/*' });
    expect(screen.getByRole('link', { name: /Phân nhóm khách hàng/ })).toHaveClass('active');
    const breadcrumb = screen.getByRole('navigation', { name: 'Vị trí trong trang quản trị' });
    expect(breadcrumb).toHaveTextContent('Khách hàngPhân nhóm khách hàng');
  });

  it('Bảng điều khiển ở /admin chỉ sáng khi đứng đúng /admin, không sáng ở trang con', () => {
    renderWithContext(<AdminLayout />, { path: '/admin', route: '/admin/*' });
    const dashboard = screen.getByRole('link', { name: /Bảng điều khiển/ });
    expect(dashboard).toHaveAttribute('href', '/admin');
    expect(dashboard).toHaveClass('active');
    const breadcrumb = screen.getByRole('navigation', { name: 'Vị trí trong trang quản trị' });
    expect(breadcrumb).toHaveTextContent('Tổng quanBảng điều khiển');
  });

  it('trang con của mục khác không đánh dấu Bảng điều khiển', () => {
    renderWithContext(<AdminLayout />, { path: '/admin/orders/5', route: '/admin/*' });
    expect(screen.getByRole('link', { name: /Bảng điều khiển/ })).not.toHaveClass('active');
    expect(screen.getByRole('link', { name: /Đơn hàng/ })).toHaveClass('active');
  });

  it('đường dẫn không thuộc mục nào thì không hiện vị trí', () => {
    renderWithContext(<AdminLayout />, { path: '/admin/khong-co', route: '/admin/*' });
    expect(screen.queryByRole('navigation', { name: 'Vị trí trong trang quản trị' })).toBeNull();
  });
});
