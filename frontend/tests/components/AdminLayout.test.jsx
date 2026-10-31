import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import AdminLayout from '../../src/components/AdminLayout';
import { renderWithContext } from '../renderWithContext';

describe('AdminLayout', () => {
  it('menu quản trị chia nhóm, đánh dấu mục đang mở', () => {
    renderWithContext(<AdminLayout />, { path: '/admin/orders' });
    const menu = screen.getByRole('complementary', { name: 'Menu quản trị' });
    expect(menu).toHaveTextContent('Cửa hàng');
    expect(menu).toHaveTextContent('Bán hàng');
    expect(screen.getByRole('link', { name: 'Đơn hàng' })).toHaveClass('active');
    expect(screen.getByRole('link', { name: 'Phân nhóm' })).toHaveAttribute(
      'href',
      '/admin/segments'
    );
  });
});
