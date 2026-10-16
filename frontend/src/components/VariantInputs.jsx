/**
 * Các ô nhập của 1 biến thể (1 dòng trong bảng): một ô cho mỗi tầng phân loại của sản phẩm, rồi
 * giá, tồn kho, mã SKU.
 */
function VariantInputs({ variant, variationCount, onChange, disabled }) {
  const update = (field) => (e) => onChange({ ...variant, [field]: e.target.value });
  const optionFields = ['option1Value', 'option2Value'].slice(0, variationCount);
  return (
    <>
      {optionFields.map((field) => (
        <td key={field}>
          <input
            value={variant[field]}
            onChange={update(field)}
            maxLength={100}
            disabled={disabled}
          />
        </td>
      ))}
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
