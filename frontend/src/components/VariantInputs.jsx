/** Các ô nhập của 1 biến thể (1 dòng trong bảng): size, màu, giá, tồn kho, mã SKU. */
function VariantInputs({ variant, onChange, disabled }) {
  const update = (field) => (e) => onChange({ ...variant, [field]: e.target.value });
  return (
    <>
      <td>
        <input value={variant.size} onChange={update('size')} maxLength={20} disabled={disabled} />
      </td>
      <td>
        <input
          value={variant.color}
          onChange={update('color')}
          maxLength={50}
          disabled={disabled}
        />
      </td>
      <td>
        <input
          type="number"
          min={1}
          value={variant.price}
          onChange={update('price')}
          disabled={disabled}
        />
      </td>
      <td>
        <input
          type="number"
          min={0}
          value={variant.stock}
          onChange={update('stock')}
          disabled={disabled}
        />
      </td>
      <td>
        <input
          value={variant.skuCode}
          onChange={update('skuCode')}
          maxLength={50}
          disabled={disabled}
        />
      </td>
    </>
  );
}

export default VariantInputs;
