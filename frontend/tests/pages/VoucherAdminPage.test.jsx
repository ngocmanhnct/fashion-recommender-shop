import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import VoucherAdminPage from '../../src/pages/admin/VoucherAdminPage';
import * as voucherAdminApi from '../../src/services/voucherAdminApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/voucherAdminApi');

const ACTIVE = {
  id: 1,
  code: 'CHAOBAN10',
  discountType: 'PERCENT',
  discountValue: 10,
  minOrderAmount: 200000,
  maxDiscountAmount: 20000,
  validFrom: '2026-10-30T00:00:00',
  validUntil: '2026-11-30T23:59:00',
  usageLimitTotal: 100,
  usageLimitPerAccount: 1,
  usedCount: 3,
  remaining: 97,
  active: true,
  status: 'ACTIVE',
};
const DISABLED = {
  ...ACTIVE,
  id: 2,
  code: 'GIAM30K',
  discountType: 'FIXED',
  discountValue: 30000,
  minOrderAmount: null,
  maxDiscountAmount: null,
  usageLimitTotal: null,
  usageLimitPerAccount: null,
  usedCount: 0,
  active: false,
  status: 'DISABLED',
};
const page = (content) => ({ content, page: 0, totalPages: 1, totalElements: content.length });

function renderPage(path = '/admin/vouchers') {
  return renderWithContext(<VoucherAdminPage />, {
    auth: authValue({ user: users.ADMIN }),
    path,
    route: '/admin/vouchers',
  });
}

describe('VoucherAdminPage', () => {
  beforeEach(() => {
    vi.mocked(voucherAdminApi.searchVouchers).mockResolvedValue(page([ACTIVE, DISABLED]));
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('liệt kê mã với mức giảm, điều kiện, lượt dùng và tình trạng', async () => {
    renderPage();
    const row = (await screen.findByText('CHAOBAN10')).closest('tr');
    expect(row).toHaveTextContent('Giảm 10%, tối đa 20.000 ₫');
    expect(row).toHaveTextContent('Đơn từ 200.000 ₫');
    expect(row).toHaveTextContent('3 / 100 lượt, mỗi khách 1');
    expect(row).toHaveTextContent('Đang áp dụng');
    const off = screen.getByText('GIAM30K').closest('tr');
    expect(off).toHaveTextContent('Giảm 30.000 ₫');
    expect(off).toHaveTextContent('0 lượt (không giới hạn)');
    expect(within(off).getByText('Đã ngừng')).toHaveClass('status-muted');
    expect(screen.getByText('2 mã')).toBeInTheDocument();
  });

  it('tạo mã mới: gửi mã viết hoa, ô trống là null, kiểu cố định thì bỏ trần', async () => {
    vi.mocked(voucherAdminApi.createVoucher).mockResolvedValue({ ...ACTIVE, code: 'MOI50' });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Tạo mã mới' }));
    await userEvent.type(screen.getByLabelText(/^Mã/), 'moi50');
    await userEvent.type(screen.getByLabelText('Giảm tối đa (đ)'), '9000');
    await userEvent.selectOptions(screen.getByLabelText('Kiểu giảm'), 'FIXED');
    expect(screen.queryByLabelText('Giảm tối đa (đ)')).not.toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Số tiền giảm (đ)'), '50000');
    await userEvent.type(screen.getByLabelText(/^Đơn tối thiểu/), '300000');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mã' }));

    expect(voucherAdminApi.createVoucher).toHaveBeenCalledWith(
      expect.objectContaining({
        code: 'MOI50',
        discountType: 'FIXED',
        discountValue: 50000,
        maxDiscountAmount: null,
        minOrderAmount: 300000,
        usageLimitTotal: null,
        usageLimitPerAccount: 1,
      })
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Đã tạo mã MOI50');
    expect(screen.queryByRole('button', { name: 'Tạo mã' })).not.toBeInTheDocument();
    await waitFor(() => expect(voucherAdminApi.searchVouchers).toHaveBeenCalledTimes(2));
  });

  it('lỗi validate hiện ngay dưới ô; Hủy đóng biểu mẫu', async () => {
    vi.mocked(voucherAdminApi.createVoucher).mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dữ liệu không hợp lệ', errors: { code: 'Mã chỉ gồm chữ không dấu' } },
      },
    });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Tạo mã mới' }));
    await userEvent.type(screen.getByLabelText(/^Mã/), 'ab');
    await userEvent.type(screen.getByLabelText('Phần trăm giảm'), '10');
    await userEvent.click(screen.getByRole('button', { name: 'Tạo mã' }));
    expect(await screen.findByText('Mã chỉ gồm chữ không dấu')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Dữ liệu không hợp lệ');
    await userEvent.click(screen.getByRole('button', { name: 'Hủy' }));
    expect(screen.queryByLabelText('Kiểu giảm')).not.toBeInTheDocument();
  });

  it('sửa mã đã có người dùng: không đổi được chuỗi mã, lưu điều kiện mới', async () => {
    vi.mocked(voucherAdminApi.updateVoucher).mockResolvedValue(ACTIVE);
    renderPage();
    const row = (await screen.findByText('CHAOBAN10')).closest('tr');
    await userEvent.click(within(row).getByRole('button', { name: 'Sửa' }));
    expect(screen.getByRole('heading', { name: 'Sửa mã CHAOBAN10' })).toBeInTheDocument();
    expect(screen.getByLabelText(/^Mã/)).toBeDisabled();
    expect(screen.getByText(/không đổi được chuỗi mã/)).toBeInTheDocument();
    const total = screen.getByLabelText(/^Tổng số lượt/);
    expect(total).toHaveValue(100);
    await userEvent.clear(total);
    await userEvent.type(total, '200');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
    expect(voucherAdminApi.updateVoucher).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        code: 'CHAOBAN10',
        validFrom: '2026-10-30T00:00',
        maxDiscountAmount: 20000,
        usageLimitTotal: 200,
      })
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Đã lưu mã CHAOBAN10');
  });

  it('ngừng mã sau khi xác nhận, bật lại mã đã ngừng; lỗi thì báo', async () => {
    vi.mocked(voucherAdminApi.setVoucherActive).mockResolvedValue({});
    renderPage();
    const row = (await screen.findByText('CHAOBAN10')).closest('tr');
    await userEvent.click(within(row).getByRole('button', { name: 'Ngừng' }));
    expect(window.confirm).toHaveBeenCalled();
    expect(voucherAdminApi.setVoucherActive).toHaveBeenCalledWith(1, false);
    expect(await screen.findByRole('status')).toHaveTextContent('Đã ngừng mã CHAOBAN10');

    const off = screen.getByText('GIAM30K').closest('tr');
    await userEvent.click(within(off).getByRole('button', { name: 'Bật lại' }));
    expect(voucherAdminApi.setVoucherActive).toHaveBeenCalledWith(2, true);
    expect(await screen.findByRole('status')).toHaveTextContent('Đã bật lại mã GIAM30K');

    vi.mocked(window.confirm).mockReturnValue(false);
    await userEvent.click(within(row).getByRole('button', { name: 'Ngừng' }));
    expect(voucherAdminApi.setVoucherActive).toHaveBeenCalledTimes(2);

    vi.mocked(window.confirm).mockReturnValue(true);
    vi.mocked(voucherAdminApi.setVoucherActive).mockRejectedValue({ response: { status: 500 } });
    await userEvent.click(within(row).getByRole('button', { name: 'Ngừng' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Không đổi được tình trạng mã');
  });

  it('tìm theo mã đưa từ khóa lên URL; danh sách trống; tải lỗi thì thử lại', async () => {
    vi.mocked(voucherAdminApi.searchVouchers)
      .mockRejectedValueOnce({ response: { status: 500 } })
      .mockResolvedValue(page([]));
    renderPage('/admin/vouchers?keyword=abc&page=2');
    expect(await screen.findByText('Không tải được danh sách mã')).toBeInTheDocument();
    expect(voucherAdminApi.searchVouchers).toHaveBeenCalledWith({ keyword: 'abc', page: 1 });
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Không có mã nào khớp.')).toBeInTheDocument();

    const box = screen.getByPlaceholderText('Tìm theo mã');
    await userEvent.clear(box);
    await userEvent.click(screen.getByRole('button', { name: 'Tìm' }));
    expect(await screen.findByText('Chưa có mã giảm giá nào.')).toBeInTheDocument();
    expect(voucherAdminApi.searchVouchers).toHaveBeenLastCalledWith({ keyword: '', page: 0 });
  });
});
