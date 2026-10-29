import { expect } from '@playwright/test';

// Hàm dùng chung cho test E2E: chuẩn bị dữ liệu qua REST API của backend (nhanh, không phụ thuộc
// giao diện), còn bước đang được kiểm tra thì luôn đi qua giao diện như người dùng thật.

/** Địa chỉ backend nhìn từ máy chạy test (khớp VITE_API_BASE_URL lúc build frontend). */
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

/** Tài khoản quản trị: cùng giá trị ADMIN_EMAIL, ADMIN_PASSWORD trong .env của backend. */
export const ADMIN = {
  email: process.env.E2E_ADMIN_EMAIL ?? process.env.ADMIN_EMAIL,
  password: process.env.E2E_ADMIN_PASSWORD ?? process.env.ADMIN_PASSWORD,
};

/** Email chưa dùng: mỗi lần chạy tạo tài khoản mới nên test không phụ thuộc lần chạy trước. */
export function uniqueEmail(prefix) {
  return `${prefix}-${crypto.randomUUID().slice(0, 8)}@e2e.test`;
}

/** Mật khẩu ngẫu nhiên đúng quy tắc BR-02 (có chữ và số), chỉ dùng trong lần chạy test. */
export function newPassword() {
  return `E2e-${crypto.randomUUID()}`;
}

async function callApi(request, method, path, { token, data } = {}) {
  const response = await request.fetch(`${API_URL}${path}`, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  expect(response.ok(), `${method} ${path}: ${response.status()} ${await response.text()}`).toBe(
    true
  );
  return response.status() === 204 ? null : response.json();
}

/** Đăng ký 1 khách hàng mới qua API, trả về thông tin đăng nhập kèm token. */
export async function createCustomer(request, fullName = 'Khách E2E') {
  const customer = { email: uniqueEmail('khach'), password: newPassword(), fullName };
  const auth = await callApi(request, 'POST', '/api/auth/register', { data: customer });
  return { ...customer, token: auth.accessToken };
}

/**
 * Tìm 1 sản phẩm không có phân loại (đúng 1 biến thể) còn từ 5 sản phẩm trong kho, để test thêm
 * vào giỏ, đặt hàng không phải chọn kích cỡ và chạy lại nhiều lần vẫn còn hàng.
 */
export async function findSimpleProduct(request) {
  const page = await callApi(request, 'GET', '/api/products?size=40');
  for (const card of page.content) {
    if (!card.inStock) continue;
    const product = await callApi(request, 'GET', `/api/products/${card.id}`);
    const [variant] = product.variants;
    if (!product.variationName1 && product.variants.length === 1 && variant.stock >= 5) {
      return { id: product.id, name: product.name, variantId: variant.id };
    }
  }
  throw new Error('Không có sản phẩm không phân loại nào còn hàng trong 40 sản phẩm đầu');
}

/** Đặt 1 đơn COD qua API cho khách: thêm sản phẩm vào giỏ, thêm địa chỉ rồi đặt cả giỏ. */
export async function placeCodOrder(request, customer, product) {
  const { token } = customer;
  await callApi(request, 'POST', '/api/cart/items', {
    token,
    data: { variantId: product.variantId, quantity: 1 },
  });
  const address = await callApi(request, 'POST', '/api/addresses', {
    token,
    data: {
      recipientName: customer.fullName,
      phone: '0901234567',
      addressDetail: '1 Võ Văn Ngân, Thủ Đức, TP.HCM',
      isDefault: true,
    },
  });
  const preview = await callApi(request, 'GET', '/api/orders/preview', { token });
  return callApi(request, 'POST', '/api/orders', {
    token,
    data: {
      idempotencyKey: crypto.randomUUID(),
      paymentMethod: 'COD',
      addressId: address.id,
      expectedTotal: preview.total,
    },
  });
}

/** Lấy đơn hàng của khách qua API (kiểm tra kết quả sau thao tác trên giao diện). */
export function getOrder(request, customer, orderId) {
  return callApi(request, 'GET', `/api/orders/${orderId}`, { token: customer.token });
}

/** Lấy giỏ hàng của khách qua API. */
export function getCart(request, customer) {
  return callApi(request, 'GET', '/api/cart', { token: customer.token });
}

/** Đăng nhập qua trang /login (trang đang ở có thể là /login sau khi bị chuyển tới). */
export async function fillLoginForm(page, email, password) {
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
}

/** Mở trang /login, đăng nhập và chờ lời chào trên thanh trên cùng. */
export async function login(page, account) {
  await page.goto('/login');
  await fillLoginForm(page, account.email, account.password);
  await expect(page.getByText(`Xin chào, ${account.fullName}`)).toBeVisible();
}
