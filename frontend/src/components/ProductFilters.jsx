import { useId, useRef, useState } from 'react';
import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getBrands, getCategories } from '../services/catalogApi';
import {
  RATING_OPTIONS,
  SORT_OPTIONS,
  countActiveFilters,
  productsPath,
} from '../services/productQuery';

const PRICE_PATTERN = /^\d{1,15}$/;

/**
 * Bảng lọc của trang danh sách sản phẩm (US-01): sắp xếp, danh mục, thương hiệu, khoảng giá,
 * đánh giá. Mỗi thay đổi gọi onChange(changes) đúng 1 lần; trang tự về trang 1. Trên điện thoại
 * phần lọc thu gọn sau nút "Bộ lọc" để danh sách sản phẩm hiện ngay dưới.
 */
function ProductFilters({ criteria, onChange }) {
  const [open, setOpen] = useState(false);
  const bodyId = useId();
  const activeCount = countActiveFilters(criteria);
  const categories = useApiData('all', getCategories);
  const brands = useApiData('all', getBrands);
  const toggleRef = useRef(null);

  // Trên điện thoại kết quả nằm dưới bảng lọc: áp dụng xong thì thu gọn bảng để thấy ngay kết quả,
  // đưa focus về nút "Bộ lọc" thay vì để mất theo ô vừa bị ẩn.
  const apply = (changes) => {
    onChange(changes);
    if (open) {
      setOpen(false);
      toggleRef.current?.focus();
    }
  };

  return (
    <div className="filters">
      <div className="filters-top">
        <label className="field filters-sort">
          <span className="field-label">Sắp xếp</span>
          <select value={criteria.sort} onChange={(e) => onChange({ sort: e.target.value })}>
            {SORT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <button
          ref={toggleRef}
          type="button"
          className="btn btn-ghost filters-toggle"
          aria-expanded={open}
          aria-controls={bodyId}
          onClick={() => setOpen(!open)}
        >
          {activeCount > 0 ? `Bộ lọc (${activeCount})` : 'Bộ lọc'}
        </button>
      </div>

      <div id={bodyId} className={open ? 'filters-body is-open' : 'filters-body'}>
        <CatalogSelect
          label="Danh mục"
          noun="danh mục"
          resource={categories}
          value={criteria.categoryId}
          onChange={(categoryId) => apply({ categoryId })}
        />
        <CatalogSelect
          label="Thương hiệu"
          noun="thương hiệu"
          resource={brands}
          value={criteria.brandId}
          onChange={(brandId) => apply({ brandId })}
        />
        <PriceRangeForm
          key={`${criteria.minPrice ?? ''}-${criteria.maxPrice ?? ''}`}
          minPrice={criteria.minPrice}
          maxPrice={criteria.maxPrice}
          onApply={(minPrice, maxPrice) => apply({ minPrice, maxPrice })}
        />
        <fieldset className="filter-group">
          <legend className="field-label">Đánh giá</legend>
          <div className="radio-list">
            {RATING_OPTIONS.map((o) => (
              <label key={o.value || 'all'} className="radio">
                <input
                  type="radio"
                  name="minRating"
                  value={o.value}
                  checked={criteria.minRating === o.value}
                  onChange={() => apply({ minRating: o.value })}
                />
                {o.label}
              </label>
            ))}
          </div>
        </fieldset>
        {activeCount > 0 && (
          <Link
            to={productsPath({ keyword: criteria.keyword, sort: criteria.sort })}
            className="btn btn-ghost btn-block"
            onClick={() => setOpen(false)}
          >
            Xóa bộ lọc
          </Link>
        )}
      </div>
    </div>
  );
}

/**
 * Ô chọn danh mục hoặc thương hiệu. Tải danh sách lỗi thì chỉ còn "Tất cả" kèm 1 dòng báo lỗi.
 * Mã trên URL không có trong danh sách (vd đã bị xóa) vẫn hiện là đang chọn, không âm thầm hiện
 * "Tất cả" trong khi bộ lọc vẫn đang áp dụng.
 */
function CatalogSelect({ label, noun, resource, value, onChange }) {
  const items = resource.data ?? [];
  const missing = value !== '' && !items.some((item) => String(item.id) === value);
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">Tất cả</option>
        {missing && (
          <option value={value}>{resource.loading ? 'Đang tải...' : `Mã ${value}`}</option>
        )}
        {items.map((item) => (
          <option key={item.id} value={String(item.id)}>
            {item.name}
          </option>
        ))}
      </select>
      {resource.error && (
        <span className="muted small">Không tải được danh sách {noun}, thử tải lại trang.</span>
      )}
    </label>
  );
}

/**
 * Khoảng giá (VNĐ, số nguyên không âm), chỉ áp dụng khi bấm "Áp dụng" và giá thấp nhất không lớn
 * hơn giá cao nhất. Ô nhập tạo lại theo giá trị trên URL (key) thay cho đồng bộ bằng effect.
 */
function PriceRangeForm({ minPrice, maxPrice, onApply }) {
  const [min, setMin] = useState(minPrice == null ? '' : String(minPrice));
  const [max, setMax] = useState(maxPrice == null ? '' : String(maxPrice));
  const [error, setError] = useState('');

  const handleSubmit = (event) => {
    event.preventDefault();
    const minText = min.trim();
    const maxText = max.trim();
    if ((minText && !PRICE_PATTERN.test(minText)) || (maxText && !PRICE_PATTERN.test(maxText))) {
      setError('Giá phải là số nguyên không âm (VNĐ).');
      return;
    }
    const nextMin = minText ? Number(minText) : null;
    const nextMax = maxText ? Number(maxText) : null;
    if (nextMin !== null && nextMax !== null && nextMin > nextMax) {
      setError('Giá thấp nhất phải nhỏ hơn hoặc bằng giá cao nhất.');
      return;
    }
    setError('');
    onApply(nextMin, nextMax);
  };

  return (
    <form className="filter-group" onSubmit={handleSubmit} noValidate>
      <fieldset>
        <legend className="field-label">Khoảng giá (VNĐ)</legend>
        <div className="price-range">
          <input
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            value={min}
            onChange={(e) => setMin(e.target.value)}
            placeholder="Từ"
            aria-label="Giá thấp nhất"
          />
          <span aria-hidden="true">–</span>
          <input
            type="number"
            min={0}
            step={1}
            inputMode="numeric"
            value={max}
            onChange={(e) => setMax(e.target.value)}
            placeholder="Đến"
            aria-label="Giá cao nhất"
          />
        </div>
      </fieldset>
      {error && <span className="field-error">{error}</span>}
      <button type="submit" className="btn btn-ghost btn-block">
        Áp dụng
      </button>
    </form>
  );
}

export default ProductFilters;
