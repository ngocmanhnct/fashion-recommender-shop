import FormField from './FormField';

/** Ô nhập thông tin chung của sản phẩm, dùng cho cả trang thêm và trang sửa. */
function ProductInfoFields({ info, onChange, categories, brands, fieldErrors, disabled }) {
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

export default ProductInfoFields;
