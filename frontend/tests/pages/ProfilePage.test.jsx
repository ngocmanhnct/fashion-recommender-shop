import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProfilePage from '../../src/pages/ProfilePage';
import * as authApi from '../../src/services/authApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/authApi');

const USER = { ...users.CUSTOMER, gender: 'FEMALE', birthdate: '2001-02-03' };
const badRequest = (detail, errors) => ({ response: { status: 400, data: { detail, errors } } });

function renderPage(user = USER) {
  return renderWithContext(<ProfilePage />, { auth: authValue({ user }), path: '/profile' });
}

describe('ProfilePage', () => {
  beforeEach(() => {
    authApi.updateProfile.mockImplementation((profile) => Promise.resolve({ ...USER, ...profile }));
    authApi.changePassword.mockResolvedValue({});
  });

  it('điền sẵn hồ sơ, lưu thì gửi họ tên đã bỏ khoảng trắng và cập nhật tài khoản đang hiện', async () => {
    const { auth } = renderPage();
    expect(screen.getByText('khach@example.com')).toBeInTheDocument();
    expect(screen.getByLabelText('Giới tính (không bắt buộc)')).toHaveValue('FEMALE');
    expect(screen.getByLabelText('Ngày sinh (không bắt buộc)')).toHaveValue('2001-02-03');

    const name = screen.getByLabelText('Họ và tên');
    await userEvent.clear(name);
    await userEvent.type(name, '  Trần Thị B ');
    await userEvent.selectOptions(screen.getByLabelText('Giới tính (không bắt buộc)'), '');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));

    expect(authApi.updateProfile).toHaveBeenCalledWith({
      fullName: 'Trần Thị B',
      gender: null,
      birthdate: '2001-02-03',
    });
    expect(await screen.findByText('Đã lưu hồ sơ')).toBeInTheDocument();
    expect(auth.updateUser).toHaveBeenCalledWith(
      expect.objectContaining({ fullName: 'Trần Thị B' })
    );
  });

  it('lỗi từng trường của hồ sơ hiện dưới ô tương ứng', async () => {
    authApi.updateProfile.mockRejectedValue(
      badRequest('Dữ liệu không hợp lệ', { fullName: 'Họ và tên không được để trống' })
    );
    renderPage({ ...users.CUSTOMER, gender: null, birthdate: null });
    await userEvent.clear(screen.getByLabelText('Họ và tên'));
    await userEvent.click(screen.getByRole('button', { name: 'Lưu hồ sơ' }));
    expect(await screen.findByText('Họ và tên không được để trống')).toBeInTheDocument();
    expect(screen.queryByText('Đã lưu hồ sơ')).toBeNull();
  });

  it('đổi mật khẩu: nhập lại không khớp thì không gửi, đúng thì gửi và xóa các ô', async () => {
    renderPage();
    await userEvent.type(screen.getByLabelText('Mật khẩu hiện tại'), 'matkhau1');
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), 'matkhaumoi2');
    await userEvent.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), 'khac123');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
    expect(screen.getByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument();
    expect(authApi.changePassword).not.toHaveBeenCalled();

    const confirm = screen.getByLabelText(/^Nhập lại mật khẩu mới/);
    await userEvent.clear(confirm);
    await userEvent.type(confirm, 'matkhaumoi2');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
    expect(authApi.changePassword).toHaveBeenCalledWith('matkhau1', 'matkhaumoi2');
    expect(await screen.findByText(/Đã đổi mật khẩu/)).toBeInTheDocument();
    expect(screen.getByLabelText('Mật khẩu hiện tại')).toHaveValue('');
  });

  it('mật khẩu hiện tại sai thì hiện lỗi của backend, có liên kết lấy lại qua email', async () => {
    authApi.changePassword.mockRejectedValue(badRequest('Mật khẩu hiện tại không đúng'));
    renderPage();
    await userEvent.type(screen.getByLabelText('Mật khẩu hiện tại'), 'sai12345');
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), 'matkhaumoi2');
    await userEvent.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), 'matkhaumoi2');
    await userEvent.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));
    expect(await screen.findByText('Mật khẩu hiện tại không đúng')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Lấy lại qua email' })).toHaveAttribute(
      'href',
      '/forgot-password'
    );
  });
});
