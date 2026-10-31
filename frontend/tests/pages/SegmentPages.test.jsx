import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SegmentDetailPage from '../../src/pages/admin/SegmentDetailPage';
import SegmentPage from '../../src/pages/admin/SegmentPage';
import * as customerAdminApi from '../../src/services/customerAdminApi';
import { renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/customerAdminApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getSegments: vi.fn(),
  getSegmentProfile: vi.fn(),
}));

const OVERVIEW = {
  model: {
    trainedAt: '2026-10-28T02:00:00',
    customerCount: 40,
    clusterCount: 4,
    elbowClusterCount: 4,
    silhouette: 0.384,
  },
  segments: [
    {
      code: 'VIP',
      name: 'Khách VIP',
      description: 'Mua nhiều, chi nhiều',
      customerCount: 5,
      share: 12.5,
      avgRecency: 6,
      avgFrequency: 7.2,
      avgMonetary: 9000000,
      revenue: 45000000,
      revenueShare: 61.5,
    },
  ],
  segmentedCount: 40,
  unsegmentedCount: 12,
};

const bucket = (key, count, share) => ({ key, count, share });
const PROFILE = {
  code: 'LOYAL',
  name: 'Khách thân thiết',
  description: 'Mua đều đặn',
  customerCount: 3,
  customerShare: 75,
  revenue: 800000,
  revenueShare: 61.5,
  deliveredOrders: 5,
  avgOrderValue: 160000,
  avgPurchaseIntervalDays: 15,
  repeatCustomers: 2,
  avgTenureDays: 55,
  topCategories: [{ name: 'Giày thể thao nam', quantity: 4, share: 80 }],
  genders: [
    bucket('MALE', 1, 33.3),
    bucket('FEMALE', 1, 33.3),
    bucket('OTHER', 0, 0),
    bucket('UNKNOWN', 1, 33.3),
  ],
  ageGroups: [
    bucket('UNDER_18', 0, 0),
    bucket('18_24', 1, 33.3),
    bucket('25_34', 1, 33.3),
    bucket('35_44', 0, 0),
    bucket('45_54', 0, 0),
    bucket('55_PLUS', 0, 0),
    bucket('UNKNOWN', 1, 33.3),
  ],
  careCount: 25,
  careList: [
    {
      accountId: 9,
      fullName: 'Nguyễn Lan',
      email: 'lan@example.com',
      orders: 3,
      lastOrderAt: '2026-09-01T10:00:00',
      purchaseIntervalDays: 20,
      daysSinceLastOrder: 60,
    },
  ],
};

function renderProfile(code = 'LOYAL') {
  return renderWithContext(<SegmentDetailPage />, {
    path: `/admin/segments/${code}`,
    route: '/admin/segments/:code',
  });
}

describe('SegmentPage', () => {
  beforeEach(() => {
    customerAdminApi.getSegments.mockResolvedValue(OVERVIEW);
  });

  it('mỗi nhóm hiện doanh thu và mở được hồ sơ nhóm', async () => {
    renderWithContext(<SegmentPage />, { path: '/admin/segments' });
    expect(await screen.findByText('5 khách')).toBeInTheDocument();
    expect(screen.getByText(/45\.000\.000/)).toBeInTheDocument();
    expect(screen.getByText('(61,5% tổng)')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Hồ sơ nhóm' })).toHaveAttribute(
      'href',
      '/admin/segments/VIP'
    );
  });
});

describe('SegmentDetailPage', () => {
  beforeEach(() => {
    customerAdminApi.getSegmentProfile.mockResolvedValue(PROFILE);
  });

  it('hiện số liệu chính của nhóm', async () => {
    renderProfile();
    expect(await screen.findByRole('heading', { name: 'Khách thân thiết' })).toBeInTheDocument();
    expect(customerAdminApi.getSegmentProfile).toHaveBeenCalledWith('LOYAL');
    expect(screen.getByText('75% khách đã phân nhóm')).toBeInTheDocument();
    expect(screen.getByText('61,5% doanh thu các nhóm')).toBeInTheDocument();
    expect(screen.getByText('15 ngày')).toBeInTheDocument();
    expect(
      screen.getByText('Giữa 2 đơn liên tiếp, tính trên 2 khách mua từ 2 đơn')
    ).toBeInTheDocument();
    expect(screen.getByText('55 ngày')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xem khách trong nhóm' })).toHaveAttribute(
      'href',
      '/admin/customers?segment=LOYAL'
    );
  });

  it('chia khách theo danh mục, giới tính, độ tuổi', async () => {
    renderProfile();
    const categories = await screen.findByRole('list', { name: 'Danh mục mua nhiều' });
    expect(categories).toHaveTextContent('Giày thể thao nam');
    expect(categories).toHaveTextContent('4 sản phẩm · 80%');
    const genders = screen.getByRole('list', { name: 'Giới tính' });
    expect(genders).toHaveTextContent('Nữ');
    expect(genders).toHaveTextContent('Chưa khai');
    expect(screen.getByRole('list', { name: 'Độ tuổi' })).toHaveTextContent('18–24');
  });

  it('liệt kê khách cần chăm sóc kèm số lần quá nhịp', async () => {
    renderProfile();
    expect(await screen.findByText('Khách cần chăm sóc (25)')).toBeInTheDocument();
    expect(screen.getByText(/Đang hiện 1 khách đầu tiên/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Nguyễn Lan' })).toHaveAttribute(
      'href',
      '/admin/customers/9'
    );
    expect(screen.getByText('gấp 3 lần nhịp')).toBeInTheDocument();
  });

  it('nhóm chưa có khách mua lại, chưa mua gì, không ai cần chăm sóc', async () => {
    customerAdminApi.getSegmentProfile.mockResolvedValue({
      ...PROFILE,
      avgPurchaseIntervalDays: null,
      repeatCustomers: 0,
      topCategories: [],
      careCount: 0,
      careList: [],
    });
    renderProfile();
    expect(await screen.findByText('Chưa có')).toBeInTheDocument();
    expect(screen.getByText('Chưa khách nào mua từ 2 đơn')).toBeInTheDocument();
    expect(screen.getByText('Khách trong nhóm chưa mua sản phẩm nào.')).toBeInTheDocument();
    expect(screen.getByText('Không có khách nào quá nhịp mua.')).toBeInTheDocument();
  });

  it('mã nhóm sai hoặc không còn thì báo không tìm thấy', async () => {
    renderProfile('vip; drop');
    expect(screen.getByRole('heading', { name: 'Không tìm thấy nhóm khách hàng' })).toBeVisible();
    expect(customerAdminApi.getSegmentProfile).not.toHaveBeenCalled();
  });

  it('API trả 404 thì báo không tìm thấy, lỗi khác thì cho thử lại', async () => {
    customerAdminApi.getSegmentProfile.mockRejectedValueOnce({ response: { status: 404 } });
    const { unmount } = renderProfile('OLD');
    expect(
      await screen.findByRole('heading', { name: 'Không tìm thấy nhóm khách hàng' })
    ).toBeInTheDocument();
    unmount();

    customerAdminApi.getSegmentProfile.mockRejectedValueOnce({ response: { status: 500 } });
    renderProfile('VIP');
    expect(await screen.findByText('Không tải được hồ sơ nhóm')).toBeInTheDocument();
    screen.getByRole('button', { name: 'Thử lại' }).click();
    expect(await screen.findByRole('heading', { name: 'Khách thân thiết' })).toBeInTheDocument();
  });
});
