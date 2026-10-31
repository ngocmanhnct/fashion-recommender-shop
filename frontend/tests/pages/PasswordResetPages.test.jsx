import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ForgotPasswordPage from '../../src/pages/ForgotPasswordPage';
import LoginPage from '../../src/pages/LoginPage';
import ResetPasswordPage from '../../src/pages/ResetPasswordPage';
import * as authApi from '../../src/services/authApi';
import { renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/authApi');

const badRequest = (detail, errors) => ({ response: { status: 400, data: { detail, errors } } });

describe('ForgotPasswordPage', () => {
  beforeEach(() => {
    authApi.requestPasswordReset.mockResolvedValue({});
  });

  it('gửi email rồi báo kiểm tra hộp thư, không nói email có tài khoản hay không', async () => {
    renderWithContext(<ForgotPasswordPage />, { path: '/forgot-password' });
    await userEvent.type(screen.getByLabelText('Email'), ' khach@example.com ');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi liên kết' }));
    expect(authApi.requestPasswordReset).toHaveBeenCalledWith('khach@example.com');
    expect(await screen.findByRole('heading', { name: 'Kiểm tra hộp thư' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      'Nếu khach@example.com có tài khoản, liên kết đặt lại mật khẩu đã được gửi'
    );

    await userEvent.click(screen.getByRole('button', { name: 'Gửi lại' }));
    expect(screen.getByLabelText('Email')).toHaveValue('khach@example.com');
  });

  it('email sai định dạng thì hiện lỗi dưới ô', async () => {
    authApi.requestPasswordReset.mockRejectedValue(
      badRequest('Dữ liệu không hợp lệ', { email: 'Email không đúng định dạng' })
    );
    renderWithContext(<ForgotPasswordPage />, { path: '/forgot-password' });
    await userEvent.type(screen.getByLabelText('Email'), 'abc');
    await userEvent.click(screen.getByRole('button', { name: 'Gửi liên kết' }));
    expect(await screen.findByText('Email không đúng định dạng')).toBeInTheDocument();
  });
});

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    authApi.resetPassword.mockResolvedValue({});
  });

  async function fill(password, confirm = password) {
    await userEvent.type(screen.getByLabelText(/^Mật khẩu mới/), password);
    await userEvent.type(screen.getByLabelText(/^Nhập lại mật khẩu mới/), confirm);
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
  }

  it('đặt mật khẩu mới bằng mã trong liên kết rồi về trang đăng nhập kèm lời báo', async () => {
    renderWithContext(<ResetPasswordPage />, { path: '/reset-password?token=abc_123' });
    await fill('matkhaumoi2', 'khac');
    expect(screen.getByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument();
    expect(authApi.resetPassword).not.toHaveBeenCalled();

    const confirm = screen.getByLabelText(/^Nhập lại mật khẩu mới/);
    await userEvent.clear(confirm);
    await userEvent.type(confirm, 'matkhaumoi2');
    await userEvent.click(screen.getByRole('button', { name: 'Đặt mật khẩu mới' }));
    expect(authApi.resetPassword).toHaveBeenCalledWith('abc_123', 'matkhaumoi2');
    expect(await screen.findByTestId('location')).toHaveTextContent(
      '/login {"notice":"Đã đặt mật khẩu mới. Hãy đăng nhập bằng mật khẩu mới."}'
    );
  });

  it('liên kết hết hạn thì báo lỗi và mời xin liên kết mới', async () => {
    authApi.resetPassword.mockRejectedValue(
      badRequest('Liên kết đặt lại mật khẩu không hợp lệ, đã dùng hoặc đã hết hạn.')
    );
    renderWithContext(<ResetPasswordPage />, { path: '/reset-password?token=cu' });
    await fill('matkhaumoi2');
    expect(await screen.findByText(/đã dùng hoặc đã hết hạn/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Xin liên kết mới' })).toHaveAttribute(
      'href',
      '/forgot-password'
    );
  });

  it('mật khẩu yếu thì lỗi hiện dưới ô, không mời xin liên kết mới', async () => {
    authApi.resetPassword.mockRejectedValue(
      badRequest('Dữ liệu không hợp lệ', { newPassword: 'Mật khẩu phải từ 8 đến 72 ký tự' })
    );
    renderWithContext(<ResetPasswordPage />, { path: '/reset-password?token=abc' });
    await fill('ngan1');
    expect(await screen.findByText('Mật khẩu phải từ 8 đến 72 ký tự')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Xin liên kết mới' })).toBeNull();
  });

  it('thiếu mã trong liên kết thì không hiện ô nhập', () => {
    renderWithContext(<ResetPasswordPage />, { path: '/reset-password' });
    expect(screen.getByText(/Liên kết thiếu mã/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Mật khẩu mới/)).toBeNull();
  });
});

describe('LoginPage sau khi đặt lại mật khẩu', () => {
  it('hiện lời báo và có liên kết quên mật khẩu', () => {
    renderWithContext(<LoginPage />, {
      path: '/login',
      state: { notice: 'Đã đặt mật khẩu mới. Hãy đăng nhập bằng mật khẩu mới.' },
    });
    expect(screen.getByRole('status')).toHaveTextContent('Đã đặt mật khẩu mới');
    expect(screen.getByRole('link', { name: 'Quên mật khẩu?' })).toHaveAttribute(
      'href',
      '/forgot-password'
    );
  });
});
