import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import ProductInfoFields from '../../components/ProductInfoFields';
import VariantInputs from '../../components/VariantInputs';
import { getBrands, getCategories } from '../../services/catalogApi';
import { getErrorMessage, getFieldErrors } from '../../services/errorMessage';
import { createProduct } from '../../services/productAdminApi';
import { EMPTY_PRODUCT_INFO, toProductInfoPayload } from '../../services/productForm';
import { EMPTY_VARIANT, toVariantPayload } from '../../services/variantForm';

function ProductCreatePage() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [info, setInfo] = useState(EMPTY_PRODUCT_INFO);
  const [variants, setVariants] = useState([EMPTY_VARIANT]);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    Promise.all([getCategories(), getBrands()])
      .then(([c, b]) => {
        setCategories(c);
        setBrands(b);
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  const updateVariantAt = (index, value) =>
    setVariants(variants.map((v, i) => (i === index ? value : v)));

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setFieldErrors({});
    setSubmitting(true);
    try {
      const created = await createProduct({
        ...toProductInfoPayload(info),
        variants: variants.map(toVariantPayload),
      });
      navigate(`/admin/products/${created.id}`, { replace: true });
    } catch (err) {
      const errors = getFieldErrors(err);
      setFieldErrors(errors);
      // Lỗi của biến thể có tên dạng "variants[0].price": gộp lên dòng báo lỗi chung.
      const variantErrors = Object.entries(errors)
        .filter(([field]) => field.startsWith('variants'))
        .map(([field, msg]) => `Biến thể ${Number(field.match(/\d+/)?.[0] ?? 0) + 1}: ${msg}`);
      setError([getErrorMessage(err), ...variantErrors].join('. '));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <p>
        <Link to="/admin/products">‹ Danh sách sản phẩm</Link>
      </p>
      <h1>Thêm sản phẩm</h1>
      <form onSubmit={handleSubmit} noValidate className="form-wide">
        <ProductInfoFields
          info={info}
          onChange={setInfo}
          categories={categories}
          brands={brands}
          fieldErrors={fieldErrors}
        />

        <h2>Biến thể (ít nhất 1)</h2>
        <table className="table table-inputs">
          <thead>
            <tr>
              <th>Size</th>
              <th>Màu</th>
              <th>Giá (VNĐ)</th>
              <th>Tồn kho</th>
              <th>Mã SKU (không bắt buộc)</th>
              <th className="actions" />
            </tr>
          </thead>
          <tbody>
            {variants.map((variant, index) => (
              <tr key={index}>
                <VariantInputs
                  variant={variant}
                  onChange={(value) => updateVariantAt(index, value)}
                />
                <td className="actions">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={variants.length === 1}
                    onClick={() => setVariants(variants.filter((_, i) => i !== index))}
                  >
                    Bỏ
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => setVariants([...variants, EMPTY_VARIANT])}
        >
          + Thêm dòng biến thể
        </button>

        {error && <p className="alert alert-error">{error}</p>}
        <div className="form-actions">
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Đang lưu...' : 'Tạo sản phẩm'}
          </button>
        </div>
      </form>
    </div>
  );
}

export default ProductCreatePage;
