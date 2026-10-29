import { describe, expect, it, vi } from 'vitest';
import {
  EMPTY_CRITERIA,
  countActiveFilters,
  loadProductPage,
  parseProductQuery,
  productsPath,
  toSearchParams,
} from '../../src/services/productQuery';
import { searchProducts } from '../../src/services/productApi';

vi.mock('../../src/services/productApi', () => ({ searchProducts: vi.fn() }));

const parse = (query) => parseProductQuery(new URLSearchParams(query));

describe('productQuery', () => {
  it('URL trống cho điều kiện mặc định', () => {
    expect(parse('')).toEqual(EMPTY_CRITERIA);
  });

  it('đọc đủ các điều kiện hợp lệ', () => {
    expect(
      parse(
        'keyword=%20giay%20&categoryId=3&brandId=7&minPrice=100000&maxPrice=500000&minRating=4&sort=PRICE_ASC&page=2'
      )
    ).toEqual({
      keyword: 'giay',
      categoryId: '3',
      brandId: '7',
      minPrice: 100000,
      maxPrice: 500000,
      minRating: '4',
      sort: 'PRICE_ASC',
      page: 2,
    });
  });

  it('bỏ giá trị sửa tay không hợp lệ thay vì báo lỗi', () => {
    expect(
      parse(
        `keyword=${'a'.repeat(101)}&categoryId=0&brandId=abc&minPrice=-1&maxPrice=1.5&minRating=5&sort=RANDOM&page=-1`
      )
    ).toEqual(EMPTY_CRITERIA);
    expect(parse('page=10001').page).toBe(0);
    expect(parse('page=1.5').page).toBe(0);
  });

  it('bỏ cả 2 mức giá khi giá thấp nhất lớn hơn giá cao nhất', () => {
    const criteria = parse('minPrice=500&maxPrice=100');
    expect(criteria.minPrice).toBeNull();
    expect(criteria.maxPrice).toBeNull();
  });

  it('chuyển ngược ra URL, bỏ giá trị mặc định', () => {
    expect(toSearchParams(EMPTY_CRITERIA).toString()).toBe('');
    const criteria = parse('keyword=giay&minPrice=0&sort=NEWEST&page=1&minRating=3&brandId=2');
    expect(parseProductQuery(toSearchParams(criteria))).toEqual(criteria);
    expect(productsPath({})).toBe('/products');
    expect(productsPath({ keyword: 'túi', categoryId: '5' })).toBe(
      '/products?keyword=t%C3%BAi&categoryId=5'
    );
  });

  it('đếm bộ lọc đang bật, không tính từ khóa và sắp xếp', () => {
    expect(countActiveFilters(EMPTY_CRITERIA)).toBe(0);
    expect(countActiveFilters({ ...EMPTY_CRITERIA, keyword: 'giày', sort: 'NEWEST' })).toBe(0);
    expect(
      countActiveFilters({ ...EMPTY_CRITERIA, categoryId: '1', minPrice: 0, minRating: '4' })
    ).toBe(3);
  });

  it('tải trang sản phẩm với cỡ trang mặc định 20', () => {
    loadProductPage('keyword=giay&page=1');
    expect(searchProducts).toHaveBeenCalledWith({ keyword: 'giay', page: '1', size: 20 });
  });
});
