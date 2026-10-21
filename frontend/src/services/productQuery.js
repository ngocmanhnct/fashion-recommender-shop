import { searchProducts } from './productApi';

// Trang danh sách sản phẩm lưu toàn bộ điều kiện lọc trên URL (chia sẻ, tải lại, quay lại đều
// giữ nguyên). URL có thể bị sửa tay, nên mọi giá trị đều được kiểm tra trước khi gửi backend:
// giá trị sai bị bỏ qua thay vì làm cả trang báo lỗi 400.

export const DEFAULT_SORT = 'POPULAR';
export const PAGE_SIZE = 20;
export const MAX_KEYWORD_LENGTH = 100;
const MAX_PAGE = 10000;

export const SORT_OPTIONS = [
  { value: 'POPULAR', label: 'Phổ biến' },
  { value: 'NEWEST', label: 'Mới nhất' },
  { value: 'PRICE_ASC', label: 'Giá tăng dần' },
  { value: 'PRICE_DESC', label: 'Giá giảm dần' },
];

// value rỗng = không lọc theo đánh giá.
export const RATING_OPTIONS = [
  { value: '', label: 'Tất cả' },
  { value: '4', label: 'Từ 4 sao' },
  { value: '3', label: 'Từ 3 sao' },
  { value: '2', label: 'Từ 2 sao' },
  { value: '1', label: 'Từ 1 sao' },
];

// Mã danh mục, thương hiệu giữ ở dạng chuỗi: tối đa 18 chữ số luôn vừa kiểu Long của backend.
const ID_PATTERN = /^[1-9]\d{0,17}$/;
// Giá là số nguyên VNĐ không âm; 15 chữ số vẫn nằm trong phạm vi số nguyên an toàn của JS.
const PRICE_PATTERN = /^\d{1,15}$/;
// Chỉ nhận các mức có trong RATING_OPTIONS (1 đến 4 sao), để mức đang lọc luôn hiện trên bảng lọc.
// Backend vẫn nhận 1 đến 5; URL gõ tay minRating=5 thì bị bỏ qua.
const RATING_PATTERN = /^[1-4]$/;

/** Điều kiện lọc khi URL không có tham số nào. */
export const EMPTY_CRITERIA = {
  keyword: '',
  categoryId: '',
  brandId: '',
  minPrice: null,
  maxPrice: null,
  minRating: '',
  sort: DEFAULT_SORT,
  page: 0,
};

function parseId(value) {
  return value !== null && ID_PATTERN.test(value) ? value : '';
}

function parsePrice(value) {
  return value !== null && PRICE_PATTERN.test(value) ? Number(value) : null;
}

/** Đọc điều kiện lọc từ URLSearchParams; giá trị không hợp lệ bị bỏ (về mặc định). */
export function parseProductQuery(searchParams) {
  const keyword = (searchParams.get('keyword') ?? '').trim();
  const minRating = searchParams.get('minRating') ?? '';
  const sort = searchParams.get('sort');
  const page = Number(searchParams.get('page') ?? '0');
  let minPrice = parsePrice(searchParams.get('minPrice'));
  let maxPrice = parsePrice(searchParams.get('maxPrice'));
  if (minPrice !== null && maxPrice !== null && minPrice > maxPrice) {
    minPrice = null;
    maxPrice = null;
  }
  return {
    keyword: keyword.length <= MAX_KEYWORD_LENGTH ? keyword : '',
    categoryId: parseId(searchParams.get('categoryId')),
    brandId: parseId(searchParams.get('brandId')),
    minPrice,
    maxPrice,
    minRating: RATING_PATTERN.test(minRating) ? minRating : '',
    sort: SORT_OPTIONS.some((o) => o.value === sort) ? sort : DEFAULT_SORT,
    page: Number.isInteger(page) && page >= 0 && page <= MAX_PAGE ? page : 0,
  };
}

/** Chuyển điều kiện lọc thành URLSearchParams, bỏ giá trị rỗng và mặc định (sort POPULAR, page 0). */
export function toSearchParams(criteria) {
  const params = new URLSearchParams();
  if (criteria.keyword) params.set('keyword', criteria.keyword);
  if (criteria.categoryId) params.set('categoryId', criteria.categoryId);
  if (criteria.brandId) params.set('brandId', criteria.brandId);
  if (criteria.minPrice != null) params.set('minPrice', String(criteria.minPrice));
  if (criteria.maxPrice != null) params.set('maxPrice', String(criteria.maxPrice));
  if (criteria.minRating) params.set('minRating', criteria.minRating);
  if (criteria.sort && criteria.sort !== DEFAULT_SORT) params.set('sort', criteria.sort);
  if (criteria.page > 0) params.set('page', String(criteria.page));
  return params;
}

/** Đường dẫn trang danh sách cho điều kiện lọc, vd "/products?keyword=giay&page=2". */
export function productsPath(criteria) {
  const query = toSearchParams({ ...EMPTY_CRITERIA, ...criteria }).toString();
  return query ? `/products?${query}` : '/products';
}

/** Số bộ lọc đang bật, không tính từ khóa và cách sắp xếp. */
export function countActiveFilters(criteria) {
  const priceActive = criteria.minPrice != null || criteria.maxPrice != null;
  return [criteria.categoryId, criteria.brandId, criteria.minRating, priceActive].filter(Boolean)
    .length;
}

/**
 * Tải 1 trang sản phẩm từ chuỗi truy vấn đã chuẩn hóa bằng toSearchParams (dùng làm khóa cho
 * useApiData: cùng điều kiện lọc thì cùng chuỗi).
 */
export function loadProductPage(queryString) {
  const params = Object.fromEntries(new URLSearchParams(queryString));
  return searchProducts({ ...params, size: params.size ?? PAGE_SIZE });
}
