import { variationNamesOf } from './productForm';

// Logic chọn phân loại ở trang chi tiết sản phẩm (BR-05: tối đa 2 tầng), tách khỏi component
// để dễ đọc và kiểm tra. selection là mảng giá trị đang chọn theo từng tầng, null = chưa chọn.
//
// Quy tắc: một giá trị chọn được khi có biến thể còn hàng mang giá trị đó cùng với giá trị đang
// chọn ở tầng kia (tầng kia chưa chọn thì giá trị nào của tầng đó cũng được). Bấm lại giá trị
// đang chọn là bỏ chọn, nên người dùng luôn đi được tới mọi tổ hợp còn hàng, không bị kẹt.

const OPTION_FIELDS = ['option1Value', 'option2Value'];

/** Các tầng phân loại: [{ name, field, values }], values là giá trị khác nhau theo thứ tự biến thể. */
export function variantTiers(product) {
  return variationNamesOf(product).map((name, index) => {
    const field = OPTION_FIELDS[index];
    const values = [];
    for (const variant of product.variants) {
      const value = variant[field];
      if (value != null && !values.includes(value)) values.push(value);
    }
    return { name, field, values };
  });
}

/** Lựa chọn ban đầu: chưa chọn gì ở mọi tầng. */
export function emptySelection(tiers) {
  return tiers.map(() => null);
}

function matchesOtherTiers(variant, tiers, selection, tierIndex) {
  return tiers.every(
    (tier, index) =>
      index === tierIndex || selection[index] === null || variant[tier.field] === selection[index]
  );
}

/** Giá trị value ở tầng tierIndex có chọn được với lựa chọn hiện tại không. */
export function isValueAvailable(product, tiers, selection, tierIndex, value) {
  const field = tiers[tierIndex].field;
  return product.variants.some(
    (variant) =>
      variant.stock > 0 &&
      variant[field] === value &&
      matchesOtherTiers(variant, tiers, selection, tierIndex)
  );
}

/** Lựa chọn mới sau khi bấm value ở tầng tierIndex: bấm lại giá trị đang chọn là bỏ chọn. */
export function toggleValue(selection, tierIndex, value) {
  return selection.map((current, index) => {
    if (index !== tierIndex) return current;
    return current === value ? null : value;
  });
}

/**
 * Biến thể ứng với lựa chọn, null nếu còn tầng chưa chọn. Sản phẩm không có phân loại có đúng
 * 1 biến thể, coi như luôn được chọn.
 */
export function selectedVariant(product, tiers, selection) {
  if (tiers.length === 0) return product.variants[0] ?? null;
  if (selection.some((value) => value === null)) return null;
  return (
    product.variants.find((variant) =>
      tiers.every((tier, index) => variant[tier.field] === selection[index])
    ) ?? null
  );
}

/** Tên các tầng chưa chọn, vd ["Màu sắc", "Kích cỡ"]. */
export function missingTierNames(tiers, selection) {
  return tiers.filter((_, index) => selection[index] === null).map((tier) => tier.name);
}
