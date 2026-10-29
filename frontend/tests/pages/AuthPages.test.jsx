import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import LoginPage from '../../src/pages/LoginPage';
import RegisterPage from '../../src/pages/RegisterPage';
import { authValue, renderWithContext, users } from '../renderWithContext';

const badRequest = (detail, errors) => ({ response: { status: 400, data: { detail, errors } } });

describe('LoginPage', () => {
  it('gửi email đã bỏ khoảng trắng và mật khẩu', async () => {
    const auth = authValue({ login: vi.fn(() => Promise.resolve(users.CUSTOMER)) });
    renderWithContext(<LoginPage />, { auth, path: '/login' });
    await userEvent.type(screen.getByLabelText('Email'), '  khach@example.com ');
    await userEvent.type(screen.getByLabelText('Mật khẩu'), 'MatKhau123');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(auth.login).toHaveBeenCalledWith('khach@example.com', 'MatKhau123');
  });

  it('hiện thông báo lỗi của backend và lỗi từng trường', async () => {
    const auth = authValue({
      login: vi.fn(() =>
        Promise.reject(badRequest('Dữ liệu không hợp lệ', { email: 'Email không hợp lệ' }))
      ),
    });
    renderWithContext(<LoginPage />, { auth, path: '/login' });
    await userEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }));
    expect(await screen.findByText('Dữ liệu không hợp lệ')).toBeInTheDocument();
    expect(screen.getByText('Email không hợp lệ')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đăng nhập' })).toBeEnabled();
  });

  it('đã đăng nhập thì về trang đang muốn vào, không có thì theo vai trò', () => {
    const { unmount } = renderWithContext(<LoginPage />, {
      auth: authValue({ user: users.CUSTOMER }),
      path: '/login',
      state: { from: '/checkout' },
    });
    expect(screen.getByTestId('location')).toHaveTextContent('/checkout');
    unmount();
    renderWithContext(<LoginPage />, { auth: authValue({ user: users.ADMIN }), path: '/login' });
    expect(screen.getByTestId('location')).toHaveTextContent('/admin');
  });

  it('liên kết đăng ký giữ trang đang muốn vào', async () => {
    renderWithContext(<LoginPage />, { path: '/login', state: { from: '/checkout' } });
    await userEvent.click(screen.getByRole('link', { name: 'Đăng ký' }));
    expect(screen.getByTestId('location')).toHaveTextContent('/register {"from":"/checkout"}');
  });
});

describe('RegisterPage', () => {
  async function fill(confirm = 'MatKhau123') {
    await userEvent.type(screen.getByLabelText('Họ và tên'), ' Khách Mới ');
    await userEvent.type(screen.getByLabelText('Email'), 'moi@example.com');
    await userEvent.type(screen.getByLabelText(/^Mật khẩu/), 'MatKhau123');
    await userEvent.type(screen.getByLabelText('Nhập lại mật khẩu'), confirm);
  }

  it('mật khẩu nhập lại không khớp thì không gửi', async () => {
    const auth = authValue();
    renderWithContext(<RegisterPage />, { auth, path: '/register' });
    await fill('KhacNhau1');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
    expect(screen.getByText('Mật khẩu nhập lại không khớp')).toBeInTheDocument();
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('gửi thông tin đăng ký, trường không bắt buộc để trống là null', async () => {
    const auth = authValue({ register: vi.fn(() => Promise.resolve(users.CUSTOMER)) });
    renderWithContext(<RegisterPage />, { auth, path: '/register' });
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
    expect(auth.register).toHaveBeenCalledWith({
      fullName: 'Khách Mới',
      email: 'moi@example.com',
      password: 'MatKhau123',
      gender: null,
      birthdate: null,
    });
  });

  it('gửi giới tính đã chọn và hiện lỗi của backend', async () => {
    const auth = authValue({
      register: vi.fn(() => Promise.reject(badRequest('Email đã được sử dụng'))),
    });
    renderWithContext(<RegisterPage />, { auth, path: '/register' });
    await fill();
    await userEvent.selectOptions(screen.getByLabelText('Giới tính (không bắt buộc)'), 'FEMALE');
    await userEvent.click(screen.getByRole('button', { name: 'Đăng ký' }));
    expect(auth.register).toHaveBeenCalledWith(expect.objectContaining({ gender: 'FEMALE' }));
    expect(await screen.findByText('Email đã được sử dụng')).toBeInTheDocument();
  });

  it('đã đăng nhập thì về trang chủ', () => {
    renderWithContext(<RegisterPage />, {
      auth: authValue({ user: users.CUSTOMER }),
      path: '/register',
    });
    expect(screen.getByTestId('location')).toHaveTextContent('/');
  });
});
