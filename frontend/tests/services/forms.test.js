import { describe, expect, it } from 'vitest';
import {
  toProductInfoForm,
  toProductInfoPayload,
  variationNamesOf,
} from '../../src/services/productForm';
import { toVariantForm, toVariantPayload, variantLabel } from '../../src/services/variantForm';

describe('productForm', () => {
  it('chuyển sản phẩm của API thành ô nhập và ngược lại', () => {
    const form = toProductInfoForm({
      name: 'Giày chạy bộ',
      description: null,
      category: { id: 3 },
      brand: null,
      imageUrls: ['https://a/1.jpg', 'https://a/2.jpg'],
      variationName1: 'Kích cỡ',
      variationName2: null,
    });
    expect(form).toEqual({
      name: 'Giày chạy bộ',
      description: '',
      categoryId: '3',
      brandId: '',
      imageUrls: 'https://a/1.jpg\nhttps://a/2.jpg',
      variationName1: 'Kích cỡ',
      variationName2: '',
    });
    expect(
      toProductInfoPayload({ ...form, imageUrls: ' https://a/1.jpg \n\n', brandId: '4' })
    ).toEqual({
      name: 'Giày chạy bộ',
      description: null,
      categoryId: 3,
      brandId: 4,
      imageUrls: ['https://a/1.jpg'],
      variationName1: 'Kích cỡ',
      variationName2: null,
    });
    expect(toProductInfoPayload({ ...form, categoryId: '' }).categoryId).toBeNull();
  });

  it('tầng 2 chỉ tính khi có tầng 1', () => {
    expect(variationNamesOf({ variationName1: ' Màu ', variationName2: 'Cỡ' })).toEqual([
      'Màu',
      'Cỡ',
    ]);
    expect(variationNamesOf({ variationName1: '', variationName2: 'Cỡ' })).toEqual([]);
    expect(variationNamesOf({ variationName1: 'Màu' })).toEqual(['Màu']);
  });
});

describe('variantForm', () => {
  it('chuyển biến thể của API thành ô nhập', () => {
    expect(toVariantForm({ option1Value: 'Đen', price: 100, stock: null, skuCode: null })).toEqual({
      option1Value: 'Đen',
      option2Value: '',
      price: '100',
      stock: '0',
      skuCode: '',
    });
  });

  it('chỉ gửi giá trị của tầng đang dùng, ô trống thành null', () => {
    const form = { option1Value: ' Đen ', option2Value: '42', price: '', stock: '5', skuCode: ' ' };
    expect(toVariantPayload(form, 1)).toEqual({
      option1Value: 'Đen',
      option2Value: null,
      price: null,
      stock: 5,
      skuCode: null,
    });
    expect(toVariantPayload({ ...form, price: '1000', stock: '' }, 2)).toMatchObject({
      option2Value: '42',
      price: 1000,
      stock: null,
    });
    expect(toVariantPayload(form, 0).option1Value).toBeNull();
  });

  it('nhãn phân loại giống nhãn backend lưu ở dòng đơn', () => {
    expect(variantLabel({ option1Value: 'Đen', option2Value: '42' })).toBe('Đen – 42');
    expect(variantLabel({ option1Value: 'Đen', option2Value: null })).toBe('Đen');
    expect(variantLabel({})).toBe('');
  });
});
