import { expect, test } from '@playwright/test';
import { createCustomer, fillLoginForm, findSimpleProduct, getCart } from './helpers';

test.describe('Giỏ hàng', () => {
  test('khách chưa đăng nhập thêm vào giỏ, đăng nhập lúc mua thì giỏ được gộp vào tài khoản', async ({
    page,
    request,
  }) => {
    const product = await findSimpleProduct(request);
    const customer = await createCustomer(request);

    await page.goto(`/products/${product.id}`);
    await expect(page.getByRole('heading', { name: product.name })).toBeVisible();
    await page.getByRole('button', { name: 'Tăng số lượng' }).click();
    await page.getByRole('button', { name: 'Thêm vào giỏ hàng' }).click();
    await expect(page.getByRole('status')).toContainText('Đã thêm vào giỏ hàng.');
    await expect(page.getByLabel('2 sản phẩm')).toBeVisible();

    await page.getByRole('status').getByRole('link', { name: 'Xem giỏ hàng' }).click();
    await expect(page).toHaveURL('/cart');
    await expect(page.getByRole('link', { name: product.name }).first()).toBeVisible();

    // Chưa đăng nhập: bấm "Mua hàng" thì sang trang đăng nhập, xong quay lại trang thanh toán.
    await page.getByRole('button', { name: 'Mua hàng' }).click();
    await expect(page).toHaveURL('/login');
    await fillLoginForm(page, customer.email, customer.password);
    await expect(page).toHaveURL('/checkout');
    await expect(page.getByText(product.name)).toBeVisible();

    const cart = await getCart(request, customer);
    expect(cart.items).toEqual([
      expect.objectContaining({ variantId: product.variantId, quantity: 2 }),
    ]);
  });
});
