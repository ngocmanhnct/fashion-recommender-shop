import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OffersPage from '../../src/pages/OffersPage';
import { getMyVouchers } from '../../src/services/voucherApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/voucherApi');

function renderPage() {
  return renderWithContext(<OffersPage />, {
    auth: authValue({ user: users.CUSTOMER }),
    path: '/offers',
  });
}

describe('OffersPage', () => {
  beforeEach(() => {
    getMyVouchers.mockResolvedValue([
      {
        code: 'AT_RISK-3110',
        discountType: 'PERCENT',
        discountValue: 20,
        maxDiscountAmount: 100000,
        minOrderAmount: null,
        validUntil: '2026-11-07T09:00:00',
      },
      {
        code: 'LOYAL-3110',
        discountType: 'FIXED',
        discountValue: 50000,
        maxDiscountAmount: null,
        minOrderAmount: 250000,
        validUntil: '2026-11-14T09:00:00',
      },
    ]);
  });

  it('hiện mã được tặng với mức giảm, đơn tối thiểu và hạn dùng', async () => {
    renderPage();
    const cards = await screen.findAllByRole('listitem');
    expect(cards).toHaveLength(2);
    expect(cards[0]).toHaveTextContent('Giảm 20%, tối đa 100.000');
    expect(cards[0]).toHaveTextContent('Áp dụng cho mọi đơn');
    expect(cards[0]).toHaveTextContent('AT_RISK-3110');
    expect(cards[0]).toHaveTextContent('Hết hạn 07/11/2026 09:00');
    expect(cards[1].textContent.replace(/\s/g, ' ')).toContain('Cho đơn từ 250.000 ₫');
    expect(screen.getByRole('link', { name: 'Mua sắm ngay' })).toHaveAttribute('href', '/products');
  });

  it('chưa có ưu đãi; tải lỗi thì cho thử lại', async () => {
    getMyVouchers.mockRejectedValueOnce({ response: { status: 500 } }).mockResolvedValueOnce([]);
    renderPage();
    expect(await screen.findByText('Không tải được ưu đãi')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Hiện bạn chưa có ưu đãi nào.')).toBeInTheDocument();
  });
});
