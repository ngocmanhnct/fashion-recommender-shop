import { expect, test } from '@playwright/test';
import { fillLoginForm, login, newPassword, uniqueEmail } from './helpers';

test.describe('Đăng ký và đăng nhập', () => {
  test('đăng ký tài khoản mới, đăng xuất rồi đăng nhập lại', async ({ page }) => {
    const account = {
      email: uniqueEmail('dangky'),
      password: newPassword(),
      fullName: 'Khách Mới',
    };

    await page.goto('/');
    await page.getByRole('link', { name: 'Đăng ký' }).click();
    await page.getByLabel('Họ và tên').fill(account.fullName);
    await page.getByLabel('Email').fill(account.email);
    await page.getByLabel('Mật khẩu (8 ký tự trở lên, có chữ và số)').fill(account.password);
    await page.getByLabel('Nhập lại mật khẩu').fill(account.password);
    await page.getByRole('button', { name: 'Đăng ký' }).click();

    // Đăng ký xong là đăng nhập luôn và về trang chủ.
    await expect(page.getByText(`Xin chào, ${account.fullName}`)).toBeVisible();
    await expect(page).toHaveURL('/');

    await page.getByRole('button', { name: 'Đăng xuất' }).click();
    await expect(page.getByRole('link', { name: 'Đăng nhập' })).toBeVisible();

    await login(page, account);
    await expect(page.getByRole('link', { name: 'Đơn hàng' })).toBeVisible();

    // Token lưu ở trình duyệt: tải lại trang vẫn còn đăng nhập.
    await page.reload();
    await expect(page.getByText(`Xin chào, ${account.fullName}`)).toBeVisible();
  });

  test('sai mật khẩu thì báo lỗi và vẫn ở trang đăng nhập', async ({ page }) => {
    await page.goto('/login');
    await fillLoginForm(page, uniqueEmail('khongco'), newPassword());

    await expect(page.getByText('Email hoặc mật khẩu không đúng')).toBeVisible();
    await expect(page).toHaveURL('/login');
    await expect(page.getByRole('button', { name: 'Đăng xuất' })).toHaveCount(0);
  });

  test('khách chưa đăng nhập vào trang đơn hàng thì bị chuyển tới đăng nhập', async ({ page }) => {
    await page.goto('/orders');
    await expect(page).toHaveURL('/login');
  });
});
