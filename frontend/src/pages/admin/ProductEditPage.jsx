import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import ProductInfoFields from '../../components/ProductInfoFields';
import VariantManager from '../../components/VariantManager';
import { getBrands, getCategories } from '../../services/catalogApi';
import { getErrorMessage, getFieldErrors } from '../../services/errorMessage';
import { getProduct, updateProduct } from '../../services/productAdminApi';
import { toProductInfoForm, toProductInfoPayload } from '../../services/productForm';

function ProductEditPage() {
  const { id } = useParams();
  const [categories, setCategories] = useState([]);
  const [brands, setBrands] = useState([]);
  const [product, setProduct] = useState(null);
  const [info, setInfo] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    Promise.all([getCategories(), getBrands()])
      .then(([c, b]) => {
        setCategories(c);
        setBrands(b);
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  const loadProduct = useCallback(() => {
    getProduct(id)
      .then((p) => {
        setProduct(p);
        setInfo(toProductInfoForm(p));
      })
      .catch((err) => setError(getErrorMessage(err, 'Không tải được sản phẩm')));
  }, [id]);

  useEffect(loadProduct, [loadProduct]);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setMessage('');
    setFieldErrors({});
    setSaving(true);
    try {
      const updated = await updateProduct(id, toProductInfoPayload(info));
      setProduct(updated);
      setInfo(toProductInfoForm(updated));
      setMessage('Đã lưu thông tin sản phẩm');
    } catch (err) {
      setError(getErrorMessage(err));
      setFieldErrors(getFieldErrors(err));
    } finally {
      setSaving(false);
    }
  };

  if (!product || !info) {
    return error ? (
      <p className="alert alert-error">{error}</p>
    ) : (
      <p className="muted">Đang tải...</p>
    );
  }

  const deleted = product.deletedAt !== null;

  return (
    <div>
      <p>
        <Link to="/admin/products">‹ Danh sách sản phẩm</Link>
      </p>
      <h1>
        {product.name} <span className="muted small">#{product.id}</span>
      </h1>
      {deleted && (
        <p className="alert alert-warning">Sản phẩm đã ngừng bán, chỉ xem được, không sửa được.</p>
      )}

      <form onSubmit={handleSubmit} noValidate className="form-wide">
        <ProductInfoFields
          info={info}
          onChange={setInfo}
          categories={categories}
          brands={brands}
          fieldErrors={fieldErrors}
          disabled={deleted}
        />
        {error && <p className="alert alert-error">{error}</p>}
        {message && <p className="alert alert-success">{message}</p>}
        {!deleted && (
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={saving}>
              {saving ? 'Đang lưu...' : 'Lưu thông tin'}
            </button>
          </div>
        )}
      </form>

      <VariantManager
        productId={product.id}
        variants={product.variants}
        readOnly={deleted}
        onChanged={loadProduct}
      />
    </div>
  );
}

export default ProductEditPage;
