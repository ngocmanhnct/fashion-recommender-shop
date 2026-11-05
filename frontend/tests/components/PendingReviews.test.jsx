import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import PendingReviews from '../../src/components/PendingReviews';
import * as reviewApi from '../../src/services/reviewApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/reviewApi');

const ITEMS = [
  {
    productId: 3,
    productName: 'Balo du lịch',
    categoryName: 'Balo du lịch',
    variantLabel: 'Đen',
    orderId: 55,
    orderedAt: '2026-11-01T09:00:00',
  },
  {
    productId: 4,
    productName: 'Ví nam',
    categoryName: 'Ví nam',
    variantLabel: null,
    orderId: 54,
    orderedAt: '2026-10-30T09:00:00',
  },
];

function renderList(path = '/orders') {
  return renderWithContext(<PendingReviews />, {
    auth: authValue({ user: users.CUSTOMER }),
    path,
  });
}

describe('PendingReviews', () => {
  beforeEach(() => {
    vi.mocked(reviewApi.getPendingReviews).mockResolvedValue({ total: 2, items: ITEMS });
    vi.mocked(reviewApi.saveMyReview).mockResolvedValue({ rating: 4 });
  });

  it('liệt kê sản phẩm chờ đánh giá kèm đơn, đánh giá ngay tại chỗ', async () => {
    const user = userEvent.setup();
    renderList();
    const section = await screen.findByRole('heading', { name: /Chờ đánh giá/ });
    expect(section).toHaveTextContent('(2)');
    expect(screen.getByRole('link', { name: 'Đơn #55' })).toHaveAttribute('href', '/orders/55');
    expect(screen.getByText(/Đen ·/)).toBeInTheDocument();

    const rows = screen.getAllByRole('listitem');
    await user.click(within(rows[0]).getByRole('button', { name: 'Đánh giá' }));
    await user.click(within(rows[0]).getByLabelText('4 sao'));
    await user.click(within(rows[0]).getByRole('button', { name: 'Gửi đánh giá' }));

    expect(reviewApi.saveMyReview).toHaveBeenCalledWith(3, { rating: 4, comment: '' });
    expect(await within(rows[0]).findByText('✓ Đã đánh giá')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Chờ đánh giá/ })).toHaveTextContent('(1)');
  });

  it('bấm lại thì đóng ô đánh giá', async () => {
    const user = userEvent.setup();
    renderList();
    await user.click((await screen.findAllByRole('button', { name: 'Đánh giá' }))[1]);
    expect(screen.getByRole('button', { name: 'Gửi đánh giá' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Đóng' }));
    expect(screen.queryByRole('button', { name: 'Gửi đánh giá' })).not.toBeInTheDocument();
  });

  it('còn nhiều hơn số hiện thì báo số còn lại; không có gì chờ thì không hiện', async () => {
    vi.mocked(reviewApi.getPendingReviews).mockResolvedValueOnce({ total: 25, items: ITEMS });
    const scrollIntoView = vi.fn();
    Element.prototype.scrollIntoView = scrollIntoView;
    const { unmount } = renderList('/orders#cho-danh-gia');
    expect(await screen.findByText(/Còn 23 sản phẩm khác/)).toBeInTheDocument();
    expect(scrollIntoView).toHaveBeenCalledTimes(1);
    delete Element.prototype.scrollIntoView;
    unmount();

    vi.mocked(reviewApi.getPendingReviews).mockResolvedValueOnce({ total: 0, items: [] });
    renderList();
    await vi.waitFor(() => expect(reviewApi.getPendingReviews).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole('heading', { name: /Chờ đánh giá/ })).not.toBeInTheDocument();
  });
});
