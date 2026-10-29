import { expect, test } from '@playwright/test';
import {
  ADMIN,
  createCustomer,
  fillLoginForm,
  findSimpleProduct,
  getOrder,
  placeCodOrder,
} from './helpers';

test.describe('Quản trị đơn hàng', () => {
  // Trên máy thiếu biến thì bỏ qua; trên CI không bỏ qua để thiếu cấu hình là đỏ.
  test.skip(
    !process.env.CI && (!ADMIN.email || !ADMIN.password),
    'Cần E2E_ADMIN_EMAIL, E2E_ADMIN_PASSWORD (hoặc ADMIN_EMAIL, ADMIN_PASSWORD) như .env'
  );

  test('admin tìm đơn của khách và chuyển sang trạng thái kế tiếp', async ({ page, request }) => {
    const product = await findSimpleProduct(request);
    const customer = await createCustomer(request);
    const order = await placeCodOrder(request, customer, product);

    await page.goto('/login');
    await fillLoginForm(page, ADMIN.email, ADMIN.password);
    await expect(page.getByText('Xin chào, Quản trị viên')).toBeVisible();
    await expect(page).toHaveURL(/\/admin\//);

    await page.getByRole('link', { name: 'Đơn hàng' }).click();
    const search = page.getByPlaceholder('Mã đơn, email khách hoặc số điện thoại');
    await search.fill(customer.email);
    await search.press('Enter');
    await page.getByRole('link', { name: `#${order.id}`, exact: true }).click();

    await expect(page.getByRole('heading', { name: `Đơn #${order.id}` })).toBeVisible();
    await expect(page.locator('.order-card-head .status')).toHaveText('Chờ xử lý');

    // Trang hỏi lại bằng hộp xác nhận của trình duyệt trước khi đổi trạng thái.
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Chuyển sang “Đang xử lý”' }).click();

    await expect(page.locator('.order-card-head .status')).toHaveText('Đang xử lý');
    await expect(page.getByRole('button', { name: 'Chuyển sang “Đang giao”' })).toBeVisible();

    const updated = await getOrder(request, customer, order.id);
    expect(updated.status).toBe('PROCESSING');
  });
});
