import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SegmentDetailPage from '../../src/pages/admin/SegmentDetailPage';
import SegmentPage from '../../src/pages/admin/SegmentPage';
import * as customerAdminApi from '../../src/services/customerAdminApi';
import { renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/customerAdminApi', async (importOriginal) => ({
  ...(await importOriginal()),
  getSegments: vi.fn(),
  getSegmentProfile: vi.fn(),
  getCampaigns: vi.fn(),
  createCampaign: vi.fn(),
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
      goal: 'Giữ chân nhóm mang lại nhiều doanh thu nhất',
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
  suggestion: {
    goal: 'Tăng giá trị mỗi đơn',
    reason: 'Khách mua đều nhưng đơn nhỏ.',
    target: 'CARE',
    discountType: 'FIXED',
    discountValue: 50000,
    maxDiscountAmount: null,
    minOrderAmount: 250000,
    validDays: 14,
  },
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

const CAMPAIGN = {
  id: 4,
  segmentCode: 'LOYAL',
  segmentName: 'Khách thân thiết',
  target: 'CARE',
  createdAt: '2026-10-20T09:00:00',
  recipientCount: 3,
  voucher: {
    id: 11,
    code: 'LOYAL-2010',
    discountType: 'PERCENT',
    discountValue: 10,
    maxDiscountAmount: 50000,
    status: 'ACTIVE',
  },
  buyers: 1,
  conversionRate: 33.3,
  orders: 1,
  revenue: 300000,
  discount: 30000,
  revenuePerDiscount: 10,
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
    expect(screen.getByText('Giữ chân nhóm mang lại nhiều doanh thu nhất')).toBeInTheDocument();
  });
});

describe('SegmentDetailPage', () => {
  beforeEach(() => {
    customerAdminApi.getSegmentProfile.mockResolvedValue(PROFILE);
    customerAdminApi.getCampaigns.mockResolvedValue([]);
  });

  it('gợi ý việc nên làm và tặng mã cho khách cần chăm sóc', async () => {
    const user = userEvent.setup();
    customerAdminApi.createCampaign.mockResolvedValue({ ...CAMPAIGN, recipientCount: 25 });
    renderProfile();
    expect(await screen.findByRole('heading', { name: 'Tăng giá trị mỗi đơn' })).toBeVisible();
    expect(screen.getByText(/Giảm 50\.000/)).toBeInTheDocument();
    expect(screen.getByText(/dùng trong 14 ngày, gửi cho khách cần chăm sóc/)).toBeInTheDocument();
    expect(await screen.findByText('Chưa tặng mã nào cho nhóm này.')).toBeInTheDocument();

    const care = screen.getByRole('button', { name: 'Tặng mã cho khách cần chăm sóc (25)' });
    expect(care).toHaveClass('btn-primary');
    expect(screen.getByRole('button', { name: 'Tặng mã cho cả nhóm (3)' })).toHaveClass(
      'btn-ghost'
    );
    await user.click(care);
    expect(
      screen.getByRole('heading', { name: 'Tặng mã cho 25 khách cần chăm sóc' })
    ).toBeVisible();
    expect(screen.getByLabelText('Mã').value).toMatch(/^LOYAL-\d{4}$/);
    expect(screen.getByLabelText('Đơn tối thiểu (đ)')).toHaveValue(250000);

    await user.click(screen.getByRole('button', { name: 'Tặng mã' }));
    const [code, request] = customerAdminApi.createCampaign.mock.calls[0];
    expect(code).toBe('LOYAL');
    expect(request.target).toBe('CARE');
    expect(request.voucher).toMatchObject({
      discountType: 'FIXED',
      discountValue: 50000,
      maxDiscountAmount: null,
      minOrderAmount: 250000,
      usageLimitTotal: null,
      usageLimitPerAccount: 1,
    });
    expect(await screen.findByRole('status')).toHaveTextContent(
      'Đã tặng mã LOYAL-2010 cho 25 khách.'
    );
    expect(customerAdminApi.getCampaigns).toHaveBeenCalledTimes(2);
  });

  it('tặng cho cả nhóm; lỗi của mã hiện dưới ô, bấm hủy thì đóng biểu mẫu', async () => {
    const user = userEvent.setup();
    customerAdminApi.createCampaign.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dữ liệu không hợp lệ', errors: { 'voucher.code': 'Mã đã tồn tại' } },
      },
    });
    renderProfile();
    await user.click(await screen.findByRole('button', { name: 'Tặng mã cho cả nhóm (3)' }));
    expect(screen.getByRole('heading', { name: 'Tặng mã cho cả nhóm (3 khách)' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Tặng mã' }));
    expect(await screen.findByText('Mã đã tồn tại')).toBeInTheDocument();
    expect(customerAdminApi.createCampaign.mock.calls[0][1].target).toBe('ALL');
    await user.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(screen.queryByRole('button', { name: 'Tặng mã' })).not.toBeInTheDocument();
  });

  it('kết quả các lần tặng: khách quay lại mua, doanh thu trên mỗi đồng giảm', async () => {
    customerAdminApi.getCampaigns.mockResolvedValue([
      CAMPAIGN,
      {
        ...CAMPAIGN,
        id: 3,
        target: 'ALL',
        voucher: { ...CAMPAIGN.voucher, code: 'LOYAL-0110', status: 'EXPIRED' },
        buyers: 0,
        conversionRate: 0,
        orders: 0,
        revenue: 0,
        discount: 0,
        revenuePerDiscount: null,
      },
    ]);
    renderProfile();
    await screen.findByText('LOYAL-0110');
    const section = screen
      .getByRole('heading', { name: 'Kết quả các lần tặng mã' })
      .closest('section');
    const rows = within(section).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('LOYAL-2010');
    expect(rows[1]).toHaveTextContent('Khách cần chăm sóc');
    expect(rows[1]).toHaveTextContent('1 khách33,3%');
    expect(rows[1]).toHaveTextContent('10đ');
    expect(rows[2]).toHaveTextContent('Cả nhóm');
    expect(rows[2]).toHaveTextContent('Đã hết hạn');
    expect(rows[2]).toHaveTextContent('Chưa có');
    expect(screen.getByText(/1\/3 khách đã quay lại mua/)).toBeInTheDocument();
  });

  it('không tải được kết quả tặng mã thì cho thử lại', async () => {
    const user = userEvent.setup();
    customerAdminApi.getCampaigns.mockRejectedValueOnce({ response: { status: 500 } });
    renderProfile();
    expect(await screen.findByText('Không tải được kết quả tặng mã')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa tặng mã nào cho nhóm này.')).toBeInTheDocument();
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
    expect(
      screen.getByRole('button', { name: 'Tặng mã cho khách cần chăm sóc (0)' })
    ).toBeDisabled();
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
