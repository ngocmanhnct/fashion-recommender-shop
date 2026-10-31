import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import RequireAdmin from '../../src/components/RequireAdmin';
import RequireCustomer from '../../src/components/RequireCustomer';
import RequireLogin from '../../src/components/RequireLogin';
import { authValue, renderWithContext, users } from '../renderWithContext';

const CASES = [
  { name: 'RequireAdmin', Guard: RequireAdmin, path: '/admin', allowed: 'ADMIN', other: '/' },
  {
    name: 'RequireCustomer',
    Guard: RequireCustomer,
    path: '/checkout',
    allowed: 'CUSTOMER',
    other: '/admin',
  },
];

describe.each(CASES)('$name', ({ Guard, path, allowed, other }) => {
  const renderGuard = (auth) =>
    renderWithContext(
      <Guard>
        <p>Nội dung được bảo vệ</p>
      </Guard>,
      { auth, path }
    );

  it('đang kiểm tra đăng nhập thì hiện đang tải', () => {
    renderGuard(authValue({ loading: true }));
    expect(screen.getByText('Đang tải...')).toBeInTheDocument();
  });

  it('chưa đăng nhập thì tới /login, nhớ trang đang muốn vào', () => {
    renderGuard(authValue());
    expect(screen.getByTestId('location')).toHaveTextContent(`/login {"from":"${path}"}`);
  });

  it('sai vai trò thì chuyển trang', () => {
    const wrong = allowed === 'ADMIN' ? users.CUSTOMER : users.ADMIN;
    renderGuard(authValue({ user: wrong }));
    expect(screen.getByTestId('location')).toHaveTextContent(other);
    expect(screen.queryByText('Nội dung được bảo vệ')).not.toBeInTheDocument();
  });

  it('đúng vai trò thì hiện nội dung', () => {
    renderGuard(authValue({ user: users[allowed] }));
    expect(screen.getByText('Nội dung được bảo vệ')).toBeInTheDocument();
  });
});

describe('RequireLogin', () => {
  const renderGuard = (auth) =>
    renderWithContext(
      <RequireLogin>
        <p>Hồ sơ</p>
      </RequireLogin>,
      { auth, path: '/profile' }
    );

  it('đang tải thì chờ, chưa đăng nhập thì tới /login', () => {
    renderGuard(authValue({ loading: true }));
    expect(screen.getByText('Đang tải...')).toBeInTheDocument();
  });

  it('chưa đăng nhập thì tới /login, nhớ trang đang muốn vào', () => {
    renderGuard(authValue());
    expect(screen.getByTestId('location')).toHaveTextContent('/login {"from":"/profile"}');
  });

  it.each(['CUSTOMER', 'ADMIN'])('vai trò %s đều xem được', (role) => {
    renderGuard(authValue({ user: users[role] }));
    expect(screen.getByText('Hồ sơ')).toBeInTheDocument();
  });
});
