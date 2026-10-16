import { useState } from 'react';
import { getErrorMessage } from '../services/errorMessage';
import { addVariant, deleteVariant, updateVariant } from '../services/productAdminApi';
import {
  EMPTY_VARIANT,
  toVariantForm,
  toVariantPayload,
  variantLabel,
} from '../services/variantForm';
import VariantInputs from './VariantInputs';

/**
 * Bảng biến thể của sản phẩm đã có: mỗi dòng lưu hoặc xóa riêng (gọi API biến thể), cộng 1 dòng
 * trống ở cuối để thêm biến thể mới. Mỗi tầng phân loại (variationNames) là một cột; sản phẩm
 * không có phân loại có đúng 1 biến thể nên không có dòng thêm. Sau mỗi thao tác thành công thì
 * gọi onChanged để tải lại.
 */
function VariantManager({ productId, variationNames, variants, readOnly, onChanged }) {
  const variationCount = variationNames.length;
  const [drafts, setDrafts] = useState(() =>
    Object.fromEntries(variants.map((v) => [v.id, toVariantForm(v)]))
  );
  const [newVariant, setNewVariant] = useState(EMPTY_VARIANT);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const run = async (action, successMessage) => {
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      onChanged();
      return true;
    } catch (err) {
      const fieldErrors = Object.values(err.response?.data?.errors || {});
      setError([getErrorMessage(err), ...fieldErrors].join('. '));
      return false;
    }
  };

  const handleSave = (variantId) =>
    run(
      () =>
        updateVariant(productId, variantId, toVariantPayload(drafts[variantId], variationCount)),
      'Đã lưu biến thể'
    );

  const handleDelete = async (variant) => {
    if (window.confirm(`Xóa biến thể ${variantLabel(variant)}?`)) {
      await run(() => deleteVariant(productId, variant.id), 'Đã xóa biến thể');
    }
  };

  const handleAdd = async () => {
    const payload = toVariantPayload(newVariant, variationCount);
    if (await run(() => addVariant(productId, payload), 'Đã thêm biến thể')) {
      setNewVariant(EMPTY_VARIANT);
    }
  };

  return (
    <div>
      <h2>Biến thể</h2>
      {error && <p className="alert alert-error">{error}</p>}
      {message && <p className="alert alert-success">{message}</p>}
      <table className="table table-inputs">
        <thead>
          <tr>
            {variationNames.map((name) => (
              <th key={name}>{name}</th>
            ))}
            <th>Giá (VNĐ)</th>
            <th>Tồn kho</th>
            <th>Mã SKU</th>
            {!readOnly && <th className="actions">Thao tác</th>}
          </tr>
        </thead>
        <tbody>
          {variants.map((v) => (
            <tr key={v.id}>
              <VariantInputs
                variant={drafts[v.id] ?? toVariantForm(v)}
                variationCount={variationCount}
                onChange={(draft) => setDrafts({ ...drafts, [v.id]: draft })}
                disabled={readOnly}
              />
              {!readOnly && (
                <td className="actions">
                  <button
                    type="button"
                    className="btn btn-primary"
                    onClick={() => handleSave(v.id)}
                  >
                    Lưu
                  </button>
                  {variationCount > 0 && (
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => handleDelete(v)}
                    >
                      Xóa
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
          {!readOnly && variationCount > 0 && (
            <tr className="row-new">
              <VariantInputs
                variant={newVariant}
                variationCount={variationCount}
                onChange={setNewVariant}
              />
              <td className="actions">
                <button type="button" className="btn btn-ghost" onClick={handleAdd}>
                  + Thêm
                </button>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default VariantManager;
