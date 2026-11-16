import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RecommendationStatsPage from '../../src/pages/admin/RecommendationStatsPage';
import * as recommendationAdminApi from '../../src/services/recommendationAdminApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/recommendationAdminApi');

const CTR = {
  from: '2026-10-02',
  to: '2026-10-31',
  total: { shown: 500, clicked: 25, ctr: 5 },
  rows: [
    { placement: 'HOME', source: 'BEHAVIOR', shown: 300, clicked: 20, ctr: 6.67 },
    { placement: 'CART', source: 'NEW_SOURCE', shown: 200, clicked: 5, ctr: 2.5 },
  ],
};

function renderPage() {
  return renderWithContext(<RecommendationStatsPage />, {
    auth: authValue({ user: users.ADMIN }),
    path: '/admin/recommendations',
    route: '/admin/recommendations',
  });
}

describe('RecommendationStatsPage', () => {
  beforeEach(() => {
    vi.mocked(recommendationAdminApi.getRecommendationCtr).mockResolvedValue(CTR);
  });

  it('hiện tỷ lệ bấm theo vị trí, nguồn và đổi khoảng thời gian', async () => {
    const user = userEvent.setup();
    renderPage();
    const table = (await screen.findByText('Trang chủ: Gợi ý cho bạn')).closest('table');
    expect(within(table).getByText('Học máy, theo hành vi')).toBeInTheDocument();
    expect(within(table).getByText('Giỏ hàng: thường được mua cùng')).toBeInTheDocument();
    expect(within(table).getByText('NEW_SOURCE')).toBeInTheDocument();
    expect(within(table).getByText('Tổng').closest('tr')).toHaveTextContent('5%');
    await user.selectOptions(screen.getByLabelText('Khoảng thời gian'), '7');
    await waitFor(() =>
      expect(recommendationAdminApi.getRecommendationCtr).toHaveBeenCalledTimes(2)
    );
    const { from, to } = vi.mocked(recommendationAdminApi.getRecommendationCtr).mock.lastCall[0];
    expect((new Date(to) - new Date(from)) / 86_400_000).toBe(6);
  });

  it('báo khi chưa có lượt hiện gợi ý nào trong khoảng', async () => {
    vi.mocked(recommendationAdminApi.getRecommendationCtr).mockResolvedValue({ ...CTR, rows: [] });
    renderPage();
    expect(
      await screen.findByText('Chưa có lượt hiện gợi ý nào trong khoảng này.')
    ).toBeInTheDocument();
    expect(screen.queryByText('Tổng')).not.toBeInTheDocument();
  });

  it('báo lỗi khi không tải được tỷ lệ bấm và cho tải lại', async () => {
    const user = userEvent.setup();
    vi.mocked(recommendationAdminApi.getRecommendationCtr)
      .mockRejectedValueOnce({ response: { status: 500, data: {} } })
      .mockResolvedValueOnce(CTR);
    renderPage();
    expect(await screen.findByText('Không tải được tỷ lệ bấm')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Trang chủ: Gợi ý cho bạn')).toBeInTheDocument();
  });
});
