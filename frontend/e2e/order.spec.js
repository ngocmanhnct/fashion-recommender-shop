import { expect, test } from '@playwright/test';
import { createCustomer, findSimpleProduct, getOrder, login } from './helpers';

test.describe('Đặt hàng', () => {
  test('khách thêm vào giỏ, nhập địa chỉ và đặt đơn COD', async ({ page, request }) => {
    const product = await findSimpleProduct(request);
    const customer = await createCustomer(request);
    await login(page, customer);

    await page.goto(`/products/${product.id}`);
    await page.getByRole('button', { name: 'Thêm vào giỏ hàng' }).click();
    await expect(page.getByRole('status')).toContainText('Đã thêm vào giỏ hàng.');

    await page.getByRole('link', { name: /^Giỏ hàng/ }).click();
    await page.getByRole('button', { name: 'Mua hàng' }).click();
    await expect(page).toHaveURL('/checkout');

    // Tài khoản mới chưa có địa chỉ nên form thêm địa chỉ mở sẵn.
    await page.getByLabel('Tên người nhận').fill('Nguyễn Văn E2E');
    await page.getByLabel('Số điện thoại').fill('0901234567');
    await page
      .getByLabel('Địa chỉ (số nhà, đường, phường/xã, tỉnh/thành)')
      .fill('1 Võ Văn Ngân, Thủ Đức, TP.HCM');
    await page.getByRole('button', { name: 'Lưu địa chỉ' }).click();
    await expect(page.getByRole('radio', { name: /Nguyễn Văn E2E/ })).toBeChecked();
    await expect(page.getByRole('radio', { name: /Thanh toán khi nhận hàng/ })).toBeChecked();

    await page.getByRole('button', { name: 'Đặt hàng', exact: true }).click();

    await expect(page.getByRole('status')).toContainText('Đặt hàng thành công!');
    await expect(page).toHaveURL(/\/orders\/\d+$/);
    const orderId = Number(page.url().split('/').pop());
    await expect(page.getByRole('heading', { name: `Đơn #${orderId}` })).toBeVisible();
    await expect(page.locator('.order-card-head .status')).toHaveText('Chờ xử lý');

    // Đơn có trong "Đơn hàng của tôi", giỏ hàng đã trống.
    await page.getByRole('link', { name: 'Đơn hàng', exact: true }).click();
    await expect(page.getByRole('link', { name: new RegExp(`^Đơn #${orderId}\\b`) })).toBeVisible();
    await expect(page.locator('.cart-badge')).toHaveCount(0);

    const order = await getOrder(request, customer, orderId);
    expect(order).toMatchObject({ status: 'PENDING', paymentMethod: 'COD' });
  });
});
