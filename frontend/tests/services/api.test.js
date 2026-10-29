import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import * as addressApi from '../../src/services/addressApi';
import { brandAdminApi, categoryAdminApi } from '../../src/services/adminCatalogApi';
import * as authApi from '../../src/services/authApi';
import * as cartApi from '../../src/services/cartApi';
import * as catalogApi from '../../src/services/catalogApi';
import * as customerAdminApi from '../../src/services/customerAdminApi';
import * as healthApi from '../../src/services/healthApi';
import * as orderAdminApi from '../../src/services/orderAdminApi';
import * as orderApi from '../../src/services/orderApi';
import * as productAdminApi from '../../src/services/productAdminApi';
import * as productApi from '../../src/services/productApi';
import * as recommendationApi from '../../src/services/recommendationApi';
import * as surveyApi from '../../src/services/surveyApi';
import { mockApi } from '../apiMock';

// Mỗi hàm bọc API gửi đúng phương thức, đường dẫn, tham số tới backend và trả về phần data.
const CALLS = [
  [() => authApi.register({ email: 'a' }), 'post', '/api/auth/register', { email: 'a' }],
  [
    () => authApi.login('a@b.vn', 'x'),
    'post',
    '/api/auth/login',
    { email: 'a@b.vn', password: 'x' },
  ],
  [() => authApi.getMe(), 'get', '/api/auth/me'],
  [() => addressApi.listAddresses(), 'get', '/api/addresses'],
  [() => addressApi.createAddress({ a: 1 }), 'post', '/api/addresses', { a: 1 }],
  [() => addressApi.updateAddress(2, { a: 1 }), 'put', '/api/addresses/2', { a: 1 }],
  [() => addressApi.deleteAddress(2), 'delete', '/api/addresses/2'],
  [() => categoryAdminApi.create('Giày'), 'post', '/api/admin/categories', { name: 'Giày' }],
  [() => brandAdminApi.rename(3, 'Nike'), 'put', '/api/admin/brands/3', { name: 'Nike' }],
  [() => brandAdminApi.remove(3), 'delete', '/api/admin/brands/3'],
  [() => cartApi.getCart(), 'get', '/api/cart'],
  [() => cartApi.addCartItem(5, 2), 'post', '/api/cart/items', { variantId: 5, quantity: 2 }],
  [() => cartApi.updateCartItem(5, 3), 'put', '/api/cart/items/5', { quantity: 3 }],
  [() => cartApi.removeCartItem(5), 'delete', '/api/cart/items/5'],
  [() => cartApi.mergeCart([]), 'post', '/api/cart/merge', { items: [] }],
  [() => cartApi.previewCart([]), 'post', '/api/cart/preview', { items: [] }],
  [() => catalogApi.getCategories(), 'get', '/api/categories'],
  [() => catalogApi.getBrands(), 'get', '/api/brands'],
  [() => customerAdminApi.getCustomer(7), 'get', '/api/admin/customers/7'],
  [() => customerAdminApi.getSegments(), 'get', '/api/admin/segments'],
  [() => healthApi.getHealth(), 'get', '/actuator/health'],
  [() => orderAdminApi.getAdminOrder(9), 'get', '/api/admin/orders/9'],
  [
    () => orderAdminApi.changeOrderStatus(9, 'SHIPPING'),
    'put',
    '/api/admin/orders/9/status',
    { status: 'SHIPPING' },
  ],
  [() => orderAdminApi.completeRefundManually(9), 'put', '/api/admin/orders/9/refund/complete'],
  [() => orderApi.getCheckoutPreview(), 'get', '/api/orders/preview'],
  [() => orderApi.getOrder(4), 'get', '/api/orders/4'],
  [() => orderApi.cancelOrder(4), 'post', '/api/orders/4/cancel'],
  [() => orderApi.createPaymentUrl(4), 'post', '/api/orders/4/payment-url'],
  [
    () => orderApi.confirmVnPayReturn('?vnp_TxnRef=1'),
    'get',
    '/api/payments/vnpay/return?vnp_TxnRef=1',
  ],
  [() => productAdminApi.getProduct(1), 'get', '/api/admin/products/1'],
  [() => productAdminApi.createProduct({ n: 1 }), 'post', '/api/admin/products', { n: 1 }],
  [() => productAdminApi.updateProduct(1, { n: 1 }), 'put', '/api/admin/products/1', { n: 1 }],
  [() => productAdminApi.deleteProduct(1), 'delete', '/api/admin/products/1'],
  [
    () => productAdminApi.addVariant(1, { p: 1 }),
    'post',
    '/api/admin/products/1/variants',
    { p: 1 },
  ],
  [
    () => productAdminApi.updateVariant(1, 2, { p: 1 }),
    'put',
    '/api/admin/products/1/variants/2',
    { p: 1 },
  ],
  [() => productAdminApi.deleteVariant(1, 2), 'delete', '/api/admin/products/1/variants/2'],
  [() => productApi.getProduct(1), 'get', '/api/products/1'],
  [() => productApi.getRelatedProducts(1), 'get', '/api/products/1/related'],
  [() => productApi.recordProductView(1), 'post', '/api/products/1/views'],
  [() => recommendationApi.getMyRecommendations(), 'get', '/api/recommendations/me'],
  [() => surveyApi.getSurvey(), 'get', '/api/survey'],
  [() => surveyApi.getSurveyBrands([]), 'post', '/api/survey/brands', { answers: [] }],
  [() => surveyApi.evaluateSurvey([]), 'post', '/api/survey/evaluate', { answers: [] }],
  [() => surveyApi.getMySurvey(), 'get', '/api/survey/me'],
  [() => surveyApi.saveMySurvey([]), 'put', '/api/survey/me', { answers: [] }],
  [() => surveyApi.skipMySurvey(), 'post', '/api/survey/me/skip'],
];

// Các hàm có tham số truy vấn: kiểm tra tham số rỗng bị bỏ.
const QUERIES = [
  [
    () => customerAdminApi.searchCustomers({ keyword: '', segment: 'VIP' }),
    '/api/admin/customers',
    { segment: 'VIP', page: 0, size: 20 },
  ],
  [
    () => customerAdminApi.getCustomerOrders(7),
    '/api/admin/customers/7/orders',
    { page: 0, size: 10 },
  ],
  [
    () => orderAdminApi.searchOrders({ status: 'PENDING', keyword: '', refundStatus: '' }),
    '/api/admin/orders',
    { status: 'PENDING', page: 0, size: 20 },
  ],
  [() => orderApi.listOrders(), '/api/orders', { page: 0, size: 10 }],
  [
    () =>
      productAdminApi.searchProducts({
        keyword: 'giày',
        categoryId: '',
        includeDeleted: false,
        page: 1,
      }),
    '/api/admin/products',
    { keyword: 'giày', includeDeleted: false, page: 1, size: 20 },
  ],
  [() => productApi.searchProducts({ page: 0 }), '/api/products', { page: 0 }],
  [() => productApi.getProductSuggestions(''), '/api/products/suggestions', {}],
  [() => productApi.getProductSuggestions('túi'), '/api/products/suggestions', { keyword: 'túi' }],
];

const withoutUndefined = (params = {}) =>
  Object.fromEntries(Object.entries(params).filter(([, value]) => value !== undefined));

describe('API wrappers', () => {
  let api;
  beforeEach(() => {
    api = mockApi(() => ({ data: { ok: true } }));
  });
  afterEach(() => api.restore());

  it.each(CALLS)('%#: %s gửi đúng request', async (call, method, url, body) => {
    const result = await call();
    const [request] = api.requests;
    expect(request.method).toBe(method);
    expect(request.url).toBe(url);
    if (body !== undefined) expect(JSON.parse(request.data)).toEqual(body);
    if (method !== 'delete' && url !== '/api/products/1/views') {
      expect(result).toEqual({ ok: true });
    }
  });

  it.each(QUERIES)('%#: tham số truy vấn đúng', async (call, url, params) => {
    await call();
    expect(api.requests[0].url).toBe(url);
    expect(withoutUndefined(api.requests[0].params)).toEqual(params);
  });

  it('đặt hàng gửi đủ 4 trường', async () => {
    await orderApi.placeOrder({
      idempotencyKey: 'k',
      paymentMethod: 'COD',
      addressId: 1,
      expectedTotal: 100,
    });
    expect(JSON.parse(api.requests[0].data)).toEqual({
      idempotencyKey: 'k',
      paymentMethod: 'COD',
      addressId: 1,
      expectedTotal: 100,
    });
  });

  it('ghi sự kiện gợi ý lỗi thì bỏ qua, không ném lỗi', async () => {
    api.restore();
    api = mockApi(() => ({ status: 500 }));
    await expect(
      recommendationApi.logRecommendationEvent('SHOWN', 'HOME', [])
    ).resolves.toBeUndefined();
    expect(JSON.parse(api.requests[0].data)).toEqual({
      type: 'SHOWN',
      placement: 'HOME',
      items: [],
    });
  });
});
