import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import DashboardPage from '../../src/pages/admin/DashboardPage';
import * as dashboardApi from '../../src/services/dashboardApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/dashboardApi');

function row(placement, source, shown, clicked) {
  return { placement, source, shown, clicked, ctr: shown ? (clicked * 100) / shown : null };
}

const DATA = {
  from: '2026-10-29',
  to: '2026-10-31',
  summary: {
    revenue: 1_530_000,
    deliveredOrders: 3,
    orders: 5,
    averageOrderValue: 510_000,
    newCustomers: 2,
    goodsRevenue: 1_440_000,
    recommendedRevenue: 360_000,
    recommendedShare: 25,
  },
  days: [
    { date: '2026-10-29', revenue: 330_000, deliveredOrders: 1, orders: 2 },
    { date: '2026-10-30', revenue: 0, deliveredOrders: 0, orders: 1 },
    { date: '2026-10-31', revenue: 1_200_000, deliveredOrders: 2, orders: 2 },
  ],
  ordersByStatus: [
    { status: 'PENDING', count: 1 },
    { status: 'PROCESSING', count: 0 },
    { status: 'SHIPPING', count: 0 },
    { status: 'DELIVERED', count: 3 },
    { status: 'CANCELLED', count: 1 },
  ],
  topProducts: [
    { productId: 7, name: 'Giày chạy bộ', quantity: 4, revenue: 1_200_000 },
    { productId: 9, name: 'Tất cổ thấp', quantity: 2, revenue: 60_000 },
  ],
  recommendations: {
    total: row(null, null, 200, 16),
    byPlacement: [row('HOME', null, 120, 12), row('PRODUCT_DETAIL', null, 80, 4)],
    bySource: [row(null, 'BEHAVIOR', 150, 14), row(null, 'POPULAR', 50, 2)],
  },
  abTest: {
    id: 3,
    name: 'Gợi ý so với bán chạy',
    startedAt: '2026-10-30T08:00:00',
    treatmentUsers: 40,
    treatmentClickRate: 45,
    controlUsers: 38,
    controlClickRate: 21.05,
  },
};

const EMPTY = {
  ...DATA,
  summary: {
    revenue: 0,
    deliveredOrders: 0,
    orders: 0,
    averageOrderValue: null,
    newCustomers: 0,
    goodsRevenue: 0,
    recommendedRevenue: 0,
    recommendedShare: null,
  },
  days: DATA.days.map((d) => ({ ...d, revenue: 0, deliveredOrders: 0, orders: 0 })),
  ordersByStatus: DATA.ordersByStatus.map((s) => ({ ...s, count: 0 })),
  topProducts: [],
  recommendations: { total: row(null, null, 0, 0), byPlacement: [], bySource: [] },
  abTest: null,
};

function renderPage() {
  return renderWithContext(<DashboardPage />, {
    auth: authValue({ user: users.ADMIN }),
    path: '/admin',
    route: '/admin',
  });
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 9, 31, 10, 0));
    dashboardApi.getDashboard.mockResolvedValue(DATA);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('mặc định 30 ngày gần nhất, hiện số tổng, đơn theo trạng thái và sản phẩm bán chạy', async () => {
    renderPage();
    expect(await screen.findByText('1.530.000 ₫', { exact: false })).toBeInTheDocument();
    expect(dashboardApi.getDashboard).toHaveBeenCalledWith({
      from: '2026-10-02',
      to: '2026-10-31',
    });
    expect(screen.getByRole('button', { name: '30 ngày' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('3 đơn đã giao')).toBeInTheDocument();
    expect(screen.getByText('Từ 29/10/2026 đến 31/10/2026', { exact: false })).toBeInTheDocument();

    const statuses = screen.getByRole('list', { name: 'Đơn hàng theo trạng thái' });
    expect(within(statuses).getByText('Đã giao').closest('li')).toHaveTextContent('3 đơn · 60%');
    expect(within(statuses).getByText('Đang giao').closest('li')).toHaveClass('is-empty');

    expect(screen.getByRole('link', { name: 'Giày chạy bộ' })).toHaveAttribute(
      'href',
      '/admin/products/7'
    );
  });

  it('biểu đồ doanh thu có trục tròn số, hiện số khi rê chuột và có dạng bảng', async () => {
    renderPage();
    const bars = await screen.findByRole('list', { name: 'Doanh thu theo ngày' });
    // Đỉnh 1,2 triệu làm tròn lên 2 triệu, giữa trục 1 triệu.
    expect(screen.getByText('2 tr')).toBeInTheDocument();
    expect(screen.getByText('1 tr')).toBeInTheDocument();
    const items = within(bars).getAllByRole('button');
    expect(items).toHaveLength(3);
    expect(items[2].querySelector('.dash-bar')).toHaveStyle({ height: '60%' });

    fireEvent.mouseEnter(items[0]);
    const tooltip = document.querySelector('.dash-tooltip');
    expect(tooltip).toHaveTextContent('29/10/2026');
    expect(tooltip).toHaveTextContent('1 đơn đã giao · 2 đơn đặt');
    expect(tooltip).toHaveClass('is-start');
    fireEvent.mouseLeave(bars);
    expect(document.querySelector('.dash-tooltip')).toBeNull();

    fireEvent.focus(items[2]);
    expect(document.querySelector('.dash-tooltip')).toHaveClass('is-end');

    const table = screen.getByText('Xem dạng bảng').closest('details');
    expect(within(table).getAllByRole('row')).toHaveLength(4);
  });

  it('hiện hiệu quả gợi ý theo vị trí, nguồn và thử nghiệm A/B đang chạy', async () => {
    renderPage();
    await screen.findByText('Hiệu quả gợi ý');
    expect(screen.getByText('25% tiền hàng đã giao')).toBeInTheDocument();
    expect(screen.getByText('8%')).toBeInTheDocument();
    const byPlacement = screen.getByRole('table', { name: 'Theo vị trí' });
    expect(
      within(byPlacement).getByText('Trang chủ: Gợi ý cho bạn').closest('tr')
    ).toHaveTextContent('1201210%');
    const bySource = screen.getByRole('table', { name: 'Theo nguồn' });
    expect(within(bySource).getByText('Bán chạy')).toBeInTheDocument();
    expect(
      screen.getByText(/Thử nghiệm A\/B đang chạy: Gợi ý so với bán chạy/)
    ).toBeInTheDocument();
    expect(screen.getByText(/Nhóm bán chạy: 38 khách, 21,05% khách bấm gợi ý/)).toBeInTheDocument();
  });

  it('chọn nhanh 7 ngày, chọn ngày tự do và báo lỗi khi ngày bắt đầu sau ngày kết thúc', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Hiệu quả gợi ý');

    await user.click(screen.getByRole('button', { name: '7 ngày' }));
    expect(dashboardApi.getDashboard).toHaveBeenLastCalledWith({
      from: '2026-10-25',
      to: '2026-10-31',
    });

    const from = screen.getByLabelText('Từ ngày');
    const to = screen.getByLabelText('Đến ngày');
    fireEvent.change(from, { target: { value: '2026-10-20' } });
    fireEvent.change(to, { target: { value: '2026-10-10' } });
    const calls = dashboardApi.getDashboard.mock.calls.length;
    await user.click(screen.getByRole('button', { name: 'Xem' }));
    expect(screen.getByRole('alert')).toHaveTextContent(
      'Ngày bắt đầu phải trước hoặc bằng ngày kết thúc'
    );
    expect(dashboardApi.getDashboard).toHaveBeenCalledTimes(calls);

    fireEvent.change(to, { target: { value: '' } });
    await user.click(screen.getByRole('button', { name: 'Xem' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Chọn đủ ngày bắt đầu và ngày kết thúc');

    fireEvent.change(to, { target: { value: '2026-10-25' } });
    await user.click(screen.getByRole('button', { name: 'Xem' }));
    expect(screen.queryByRole('alert')).toBeNull();
    expect(dashboardApi.getDashboard).toHaveBeenLastCalledWith({
      from: '2026-10-20',
      to: '2026-10-25',
    });
    expect(screen.getByRole('button', { name: '7 ngày' })).toHaveAttribute('aria-pressed', 'false');
  });

  it('khoảng không có dữ liệu thì hiện câu báo thay cho biểu đồ và bảng', async () => {
    dashboardApi.getDashboard.mockResolvedValue(EMPTY);
    renderPage();
    expect(await screen.findByText('Chưa có đơn đặt trong khoảng này.')).toBeInTheDocument();
    expect(screen.getAllByText('Chưa có đơn đã giao trong khoảng này.')).toHaveLength(2);
    expect(screen.getByText('Chưa có lượt hiện gợi ý nào trong khoảng này.')).toBeInTheDocument();
    expect(screen.getByText('Chưa có tiền hàng đã giao')).toBeInTheDocument();
    expect(screen.getByText(/Không có thử nghiệm A\/B nào đang chạy/)).toBeInTheDocument();
    expect(screen.queryByRole('list', { name: 'Doanh thu theo ngày' })).toBeNull();
  });

  it('tải lỗi thì báo lỗi, bấm Thử lại thì tải lại', async () => {
    const user = userEvent.setup();
    dashboardApi.getDashboard.mockRejectedValueOnce({
      response: { status: 400, data: { detail: 'Chỉ xem được tối đa 366 ngày mỗi lần' } },
    });
    renderPage();
    expect(await screen.findByText('Chỉ xem được tối đa 366 ngày mỗi lần')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    await waitFor(() => expect(screen.getByText('Hiệu quả gợi ý')).toBeInTheDocument());
    expect(dashboardApi.getDashboard).toHaveBeenCalledTimes(2);
  });
});
