import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import Pagination from '../../components/Pagination';
import { getCategories } from '../../services/catalogApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatPriceRange } from '../../services/format';
import { deleteProduct, searchProducts } from '../../services/productAdminApi';

function ProductListPage() {
  const [categories, setCategories] = useState([]);
  const [filters, setFilters] = useState({ keyword: '', categoryId: '', includeDeleted: false });
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    getCategories()
      .then(setCategories)
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  const load = useCallback(() => {
    searchProducts({ ...filters, page })
      .then(setResult)
      .catch((err) => setError(getErrorMessage(err)));
  }, [filters, page]);

  useEffect(load, [load]);

  const handleSearch = (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setPage(0);
    setFilters({
      keyword: form.get('keyword').trim(),
      categoryId: form.get('categoryId'),
      includeDeleted: form.get('includeDeleted') === 'on',
    });
  };

  const handleDelete = async (product) => {
    if (
      !window.confirm(`Ngừng bán sản phẩm "${product.name}"? Mã SKU của nó sẽ được giải phóng.`)
    ) {
      return;
    }
    setError('');
    try {
      await deleteProduct(product.id);
      load();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  };

  return (
    <div>
      <div className="page-header">
        <h1>Sản phẩm</h1>
        <Link to="/admin/products/new" className="btn btn-primary">
          + Thêm sản phẩm
        </Link>
      </div>

      <form className="inline-form" onSubmit={handleSearch}>
        <input name="keyword" placeholder="Tìm theo tên" defaultValue={filters.keyword} />
        <select name="categoryId" defaultValue={filters.categoryId}>
          <option value="">Tất cả danh mục</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <label className="checkbox">
          <input type="checkbox" name="includeDeleted" defaultChecked={filters.includeDeleted} />
          Gồm cả sản phẩm ngừng bán
        </label>
        <button type="submit" className="btn btn-ghost">
          Lọc
        </button>
      </form>

      {error && <p className="alert alert-error">{error}</p>}

      {result && (
        <>
          <p className="muted small">{result.totalElements} sản phẩm</p>
          <table className="table">
            <thead>
              <tr>
                <th>Ảnh</th>
                <th>Tên</th>
                <th>Danh mục</th>
                <th>Thương hiệu</th>
                <th>Giá</th>
                <th>Tồn kho</th>
                <th className="actions">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((p) => (
                <tr key={p.id} className={p.deleted ? 'row-deleted' : undefined}>
                  <td>
                    {p.thumbnailUrl ? (
                      <img src={p.thumbnailUrl} alt="" className="thumb" />
                    ) : (
                      <span className="thumb thumb-empty" />
                    )}
                  </td>
                  <td>
                    <Link to={`/admin/products/${p.id}`}>{p.name}</Link>
                    {p.deleted && <span className="badge">Ngừng bán</span>}
                  </td>
                  <td>{p.categoryName}</td>
                  <td>{p.brandName || '—'}</td>
                  <td>{formatPriceRange(p.minPrice, p.maxPrice)}</td>
                  <td>
                    {p.totalStock} ({p.variantCount} biến thể)
                  </td>
                  <td className="actions">
                    <Link to={`/admin/products/${p.id}`} className="btn btn-ghost">
                      {p.deleted ? 'Xem' : 'Sửa'}
                    </Link>
                    {!p.deleted && (
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => handleDelete(p)}
                      >
                        Xóa
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {result.content.length === 0 && (
                <tr>
                  <td colSpan={7} className="muted">
                    Không có sản phẩm nào.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          <Pagination page={result.page} totalPages={result.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}

export default ProductListPage;
