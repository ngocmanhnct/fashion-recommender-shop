import { useState } from 'react';
import { formatNumber, formatPrice, formatPriceRange } from '../services/format';
import { variantLabel } from '../services/variantForm';
import {
  emptySelection,
  isValueAvailable,
  missingTierNames,
  selectedVariant,
  toggleValue,
  variantTiers,
} from '../services/variantSelection';

/**
 * Khối giá và chọn phân loại ở trang chi tiết (BR-05). Mỗi tầng là 1 nhóm nút; giá trị không còn
 * hàng (với lựa chọn ở tầng kia) bị gạch và ghi "hết hàng". Bấm lại giá trị đang chọn để bỏ
 * chọn. Chọn đủ các tầng thì hiện giá và tồn kho của đúng biến thể đó. Trang chi tiết tạo lại
 * component này theo mã sản phẩm (key), nên lựa chọn cũ không bao giờ áp sang sản phẩm khác.
 */
function VariantPicker({ product }) {
  const tiers = variantTiers(product);
  const [selection, setSelection] = useState(() => emptySelection(tiers));
  const variant = selectedVariant(product, tiers, selection);
  const hasSelection = selection.some((value) => value !== null);

  return (
    <div className="buy-box">
      <p className="product-price" aria-live="polite">
        {variant
          ? formatPrice(variant.price)
          : formatPriceRange(product.minPrice, product.maxPrice)}
      </p>
      {!product.inStock && (
        <p className="alert alert-warning soldout-banner">
          <strong>Hết hàng.</strong> Sản phẩm này hiện đã bán hết.
        </p>
      )}

      {tiers.map((tier, tierIndex) => (
        <fieldset key={tier.field} className="variant-tier">
          <legend>
            {tier.name}
            {selection[tierIndex] !== null && (
              <span className="muted">: {selection[tierIndex]}</span>
            )}
          </legend>
          <div className="variant-options">
            {tier.values.map((value) => {
              const selected = selection[tierIndex] === value;
              const available =
                selected || isValueAvailable(product, tiers, selection, tierIndex, value);
              return (
                <button
                  key={value}
                  type="button"
                  className={available ? 'variant-option' : 'variant-option is-unavailable'}
                  aria-pressed={selected}
                  disabled={!available}
                  aria-label={available ? undefined : `${value}, hết hàng`}
                  title={available ? undefined : 'Hết hàng'}
                  onClick={() => setSelection(toggleValue(selection, tierIndex, value))}
                >
                  {value}
                </button>
              );
            })}
          </div>
        </fieldset>
      ))}

      {hasSelection && (
        <button
          type="button"
          className="btn-link small"
          onClick={() => setSelection(emptySelection(tiers))}
        >
          Bỏ chọn phân loại
        </button>
      )}
      <StockStatus product={product} tiers={tiers} selection={selection} variant={variant} />
    </div>
  );
}

function StockStatus({ product, tiers, selection, variant }) {
  if (!product.inStock) return null;
  if (variant) {
    const label = variantLabel(variant);
    return (
      <p className="stock-status" aria-live="polite">
        {label && <span className="muted">{label} · </span>}
        {variant.stock > 0 ? `Còn ${formatNumber(variant.stock)} sản phẩm` : 'Hết hàng'}
      </p>
    );
  }
  return (
    <p className="stock-status muted" aria-live="polite">
      Chọn {missingTierNames(tiers, selection).join(' và ').toLowerCase()} để xem giá và số lượng
      còn lại.
    </p>
  );
}

export default VariantPicker;
