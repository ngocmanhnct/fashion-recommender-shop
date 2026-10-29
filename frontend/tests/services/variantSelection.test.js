import { describe, expect, it } from 'vitest';
import {
  emptySelection,
  isValueAvailable,
  missingTierNames,
  selectedVariant,
  toggleValue,
  variantTiers,
} from '../../src/services/variantSelection';

const product = {
  variationName1: 'Màu sắc',
  variationName2: 'Kích cỡ',
  variants: [
    { id: 1, option1Value: 'Đen', option2Value: '40', stock: 3 },
    { id: 2, option1Value: 'Đen', option2Value: '41', stock: 0 },
    { id: 3, option1Value: 'Trắng', option2Value: '41', stock: 2 },
  ],
};

describe('variantSelection', () => {
  const tiers = variantTiers(product);

  it('tách tầng phân loại và giá trị không trùng theo thứ tự biến thể', () => {
    expect(tiers).toEqual([
      { name: 'Màu sắc', field: 'option1Value', values: ['Đen', 'Trắng'] },
      { name: 'Kích cỡ', field: 'option2Value', values: ['40', '41'] },
    ]);
    expect(emptySelection(tiers)).toEqual([null, null]);
  });

  it('chỉ cho chọn giá trị còn hàng cùng giá trị đang chọn ở tầng kia', () => {
    expect(isValueAvailable(product, tiers, [null, null], 1, '41')).toBe(true);
    expect(isValueAvailable(product, tiers, ['Đen', null], 1, '41')).toBe(false);
    expect(isValueAvailable(product, tiers, ['Đen', null], 1, '40')).toBe(true);
    expect(isValueAvailable(product, tiers, [null, '40'], 0, 'Trắng')).toBe(false);
  });

  it('bấm lại giá trị đang chọn là bỏ chọn', () => {
    expect(toggleValue([null, null], 0, 'Đen')).toEqual(['Đen', null]);
    expect(toggleValue(['Đen', '40'], 0, 'Đen')).toEqual([null, '40']);
    expect(toggleValue(['Đen', '40'], 1, '41')).toEqual(['Đen', '41']);
  });

  it('tìm biến thể khi đã chọn đủ tầng, còn thiếu thì báo tên tầng', () => {
    expect(selectedVariant(product, tiers, ['Đen', null])).toBeNull();
    expect(missingTierNames(tiers, ['Đen', null])).toEqual(['Kích cỡ']);
    expect(selectedVariant(product, tiers, ['Trắng', '41'])?.id).toBe(3);
    expect(selectedVariant(product, tiers, ['Trắng', '40'])).toBeNull();
  });

  it('sản phẩm không có phân loại luôn chọn biến thể duy nhất', () => {
    const single = { variationName1: null, variants: [{ id: 9, stock: 1 }] };
    const none = variantTiers(single);
    expect(none).toEqual([]);
    expect(selectedVariant(single, none, [])?.id).toBe(9);
    expect(selectedVariant({ variants: [] }, [], [])).toBeNull();
  });
});
