import FormField from './FormField';

/**
 * Ô nhập thông tin chung của sản phẩm và tên các tầng phân loại (tối đa 2, BR-05), dùng cho cả
 * trang thêm và trang sửa. Trang sửa truyền fixedVariationCount: chỉ đổi được tên tầng, không đổi
 * được số tầng (backend chặn), nên chỉ hiện ô cho các tầng đang có.
 */
function ProductInfoFields({
  info,
  onChange,
  categories,
  brands,
  fieldErrors,
  disabled,
  fixedVariationCount,
}) {
  const update = (field) => (e) => onChange({ ...info, [field]: e.target.value });
  return (
    <>
      <FormField label="Tên sản phẩm" error={fieldErrors.name}>
        <input value={info.name} onChange={update('name')} maxLength={255} disabled={disabled} />
      </FormField>
      <div className="field-row">
        <FormField label="Danh mục" error={fieldErrors.categoryId}>
          <select value={info.categoryId} onChange={update('categoryId')} disabled={disabled}>
            <option value="">Chọn danh mục</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label="Thương hiệu (không bắt buộc)" error={fieldErrors.brandId}>
          <select value={info.brandId} onChange={update('brandId')} disabled={disabled}>
            <option value="">Không có</option>
            {brands.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </FormField>
      </div>
      <VariationNameFields
        info={info}
        update={update}
        fieldErrors={fieldErrors}
        disabled={disabled}
        fixedCount={fixedVariationCount}
      />
      <FormField label="Mô tả" error={fieldErrors.description}>
        <textarea
          rows={4}
          value={info.description}
          onChange={update('description')}
          disabled={disabled}
        />
      </FormField>
      <FormField label="Đường dẫn ảnh (mỗi dòng 1 ảnh, ảnh đầu là ảnh đại diện)">
        <textarea
          rows={3}
          value={info.imageUrls}
          onChange={update('imageUrls')}
          placeholder="https://..."
          disabled={disabled}
        />
      </FormField>
    </>
  );
}

function VariationNameFields({ info, update, fieldErrors, disabled, fixedCount }) {
  if (fixedCount === 0) {
    return <p className="muted small">Sản phẩm không có phân loại (chỉ có 1 biến thể).</p>;
  }
  const tiers = [
    { field: 'variationName1', label: 'Tên tầng phân loại 1', placeholder: 'Ví dụ: Màu sắc' },
    { field: 'variationName2', label: 'Tên tầng phân loại 2', placeholder: 'Ví dụ: Kích cỡ' },
  ].slice(0, fixedCount ?? 2);
  return (
    <>
      <div className="field-row">
        {tiers.map((tier) => (
          <FormField
            key={tier.field}
            label={fixedCount === undefined ? `${tier.label} (không bắt buộc)` : tier.label}
            error={fieldErrors[tier.field]}
          >
            <input
              value={info[tier.field]}
              onChange={update(tier.field)}
              placeholder={tier.placeholder}
              maxLength={50}
              disabled={disabled}
            />
          </FormField>
        ))}
      </div>
      {fixedCount === undefined && (
        <p className="muted small">
          Để trống cả 2 nếu sản phẩm không có phân loại (khi đó có đúng 1 biến thể). Số tầng không
          đổi được sau khi tạo sản phẩm.
        </p>
      )}
    </>
  );
}

export default ProductInfoFields;
