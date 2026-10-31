import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import RecommendationStatsPage from '../../src/pages/admin/RecommendationStatsPage';
import * as abTestApi from '../../src/services/abTestApi';
import { describeVerdict } from '../../src/services/recommendationLabels';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/abTestApi');

const RUNNING = {
  id: 2,
  name: 'Gợi ý so với bán chạy',
  treatmentPercent: 50,
  startedAt: '2026-10-31T10:00:00',
  endedAt: null,
  running: true,
};
const STOPPED = {
  ...RUNNING,
  id: 1,
  name: 'Lần thử đầu',
  startedAt: '2026-10-20T09:00:00',
  endedAt: '2026-10-27T09:00:00',
  running: false,
};

function group(overrides = {}) {
  return {
    users: 40,
    shown: 320,
    clicks: 24,
    ctr: 7.5,
    usersClicked: 18,
    clickRate: 45,
    usersOrdered: 6,
    orderRate: 15,
    orders: 7,
    revenue: 2_100_000,
    revenuePerUser: 52_500,
    usersBoughtClicked: 4,
    boughtClickedRate: 10,
    ...overrides,
  };
}

function result(experiment = RUNNING) {
  return {
    experiment,
    treatment: group(),
    control: group({ users: 38, ctr: 3.1, clickRate: 21.05, revenuePerUser: null }),
    clickRate: { difference: 23.95, pValue: 0.0262, verdict: 'TREATMENT_BETTER' },
    orderRate: { difference: -2.1, pValue: 0.7811, verdict: 'NO_DIFFERENCE' },
    boughtClickedRate: { difference: null, pValue: null, verdict: 'NOT_ENOUGH_DATA' },
    minUsersPerGroup: 30,
  };
}

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
    vi.mocked(abTestApi.listAbTests).mockResolvedValue([RUNNING, STOPPED]);
    vi.mocked(abTestApi.getAbTestResult).mockImplementation((id) =>
      Promise.resolve(result(id === '1' ? STOPPED : RUNNING))
    );
    vi.mocked(abTestApi.getRecommendationCtr).mockResolvedValue(CTR);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('hiện thử nghiệm đang chạy, bảng so sánh 2 nhóm và kết luận', async () => {
    renderPage();
    expect(await screen.findByText('Đang chạy')).toBeInTheDocument();
    expect(screen.getByText(/nhóm gợi ý 50% khách, nhóm bán chạy 50%/)).toBeInTheDocument();
    const row = (await screen.findByText('Tỷ lệ bấm (lượt bấm ÷ lượt hiện)')).closest('tr');
    expect(row).toHaveTextContent('7,5%');
    expect(row).toHaveTextContent('3,1%');
    const perUser = screen.getByText('Doanh thu trên mỗi khách').closest('tr');
    expect(perUser).toHaveTextContent('52.500 ₫');
    expect(perUser).toHaveTextContent('–');
    expect(screen.getByText('Nhóm gợi ý cao hơn rõ rệt (p = 0,0262 < 0,05).')).toBeInTheDocument();
    expect(screen.getByText('A − B = +23,95 điểm phần trăm')).toBeInTheDocument();
    expect(screen.getByText('A − B = -2,1 điểm phần trăm')).toBeInTheDocument();
    expect(
      screen.getByText('Chưa đủ khách để kết luận: mỗi nhóm cần ít nhất 30 khách.')
    ).toBeInTheDocument();
    expect(screen.getByText(/đến bây giờ/)).toBeInTheDocument();
  });

  it('chọn thử nghiệm cũ để xem kết quả đến lúc dừng', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText(/đến bây giờ/);
    await user.selectOptions(screen.getByLabelText('Xem kết quả'), '1');
    expect(await screen.findByText(/\(đã dừng\)/)).toBeInTheDocument();
    expect(abTestApi.getAbTestResult).toHaveBeenLastCalledWith('1');
  });

  it('dừng thử nghiệm sau khi xác nhận, báo lỗi nếu không dừng được', async () => {
    const user = userEvent.setup();
    vi.mocked(abTestApi.stopAbTest)
      .mockRejectedValueOnce({ response: { data: { detail: 'Thử nghiệm đã dừng' } } })
      .mockResolvedValueOnce({ ...RUNNING, running: false });
    renderPage();
    const stop = await screen.findByRole('button', { name: 'Dừng thử nghiệm' });
    await user.click(stop);
    expect(await screen.findByRole('alert')).toHaveTextContent('Thử nghiệm đã dừng');
    await user.click(stop);
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã dừng thử nghiệm "Gợi ý so với bán chạy"'
    );
    expect(abTestApi.stopAbTest).toHaveBeenCalledWith(2);

    window.confirm.mockReturnValue(false);
    await user.click(stop);
    expect(abTestApi.stopAbTest).toHaveBeenCalledTimes(2);
  });

  it('chưa có thử nghiệm chạy thì bắt đầu thử nghiệm mới', async () => {
    const user = userEvent.setup();
    vi.mocked(abTestApi.listAbTests).mockResolvedValue([]);
    vi.mocked(abTestApi.startAbTest)
      .mockRejectedValueOnce({ response: { data: { detail: 'Đang có thử nghiệm chạy' } } })
      .mockResolvedValueOnce({ ...RUNNING, id: 3, name: 'Thử 70/30' });
    renderPage();
    const name = await screen.findByLabelText('Tên thử nghiệm');
    await user.clear(name);
    await user.type(name, 'Thử 70/30');
    const percent = screen.getByLabelText('Phần trăm khách vào nhóm gợi ý học máy');
    await user.clear(percent);
    await user.type(percent, '70');
    const submit = screen.getByRole('button', { name: 'Bắt đầu thử nghiệm' });
    await user.click(submit);
    expect(await screen.findByRole('alert')).toHaveTextContent('Đang có thử nghiệm chạy');
    await user.click(submit);
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã bắt đầu thử nghiệm "Thử 70/30"'
    );
    expect(abTestApi.startAbTest).toHaveBeenLastCalledWith({
      name: 'Thử 70/30',
      treatmentPercent: 70,
    });
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
    await waitFor(() => expect(abTestApi.getRecommendationCtr).toHaveBeenCalledTimes(2));
    const { from, to } = vi.mocked(abTestApi.getRecommendationCtr).mock.lastCall[0];
    expect((new Date(to) - new Date(from)) / 86_400_000).toBe(6);
  });

  it('báo lỗi khi không tải được danh sách thử nghiệm hoặc tỷ lệ bấm', async () => {
    vi.mocked(abTestApi.listAbTests).mockRejectedValue({ response: { status: 500, data: {} } });
    vi.mocked(abTestApi.getRecommendationCtr).mockResolvedValue({ ...CTR, rows: [] });
    renderPage();
    expect(await screen.findByText('Không tải được thử nghiệm')).toBeInTheDocument();
    expect(
      await screen.findByText('Chưa có lượt hiện gợi ý nào trong khoảng này.')
    ).toBeInTheDocument();
  });
});

describe('describeVerdict', () => {
  it('có câu cho nhóm bán chạy tốt hơn', () => {
    expect(describeVerdict({ pValue: 0.001, verdict: 'CONTROL_BETTER' }, 30)).toBe(
      'Nhóm bán chạy cao hơn rõ rệt (p = 0,001 < 0,05).'
    );
  });
});
