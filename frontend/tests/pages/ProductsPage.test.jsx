import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductsPage from '../../src/pages/ProductsPage';
import * as catalogApi from '../../src/services/catalogApi';
import * as productApi from '../../src/services/productApi';
import { renderWithContext } from '../renderWithContext';

vi.mock('../../src/services/productApi');
vi.mock('../../src/services/catalogApi');

const product = (id, name) => ({ id, name, minPrice: 100000, maxPrice: 100000, inStock: true });
const page = (content, extra = {}) => ({
  content,
  page: 0,
  totalPages: 1,
  totalElements: content.length,
  ...extra,
});

function renderPage(path = '/products') {
  return renderWithContext(<ProductsPage />, { path });
}

/** Tham số của lần gọi searchProducts gần nhất. */
const lastSearch = () => vi.mocked(productApi.searchProducts).mock.lastCall[0];

describe('ProductsPage', () => {
  beforeEach(() => {
    vi.mocked(productApi.searchProducts).mockResolvedValue(
      page([product(1, 'Balo nữ'), product(2, 'Ví nam')], { totalPages: 3, totalElements: 50 })
    );
    vi.mocked(productApi.getProductSuggestions).mockResolvedValue([product(9, 'Gợi ý 1')]);
    vi.mocked(catalogApi.getCategories).mockResolvedValue([{ id: 2, name: 'Balo' }]);
    vi.mocked(catalogApi.getBrands).mockRejectedValue(new Error('lỗi'));
  });

  it('tìm theo điều kiện trên URL và hiện kết quả', async () => {
    renderPage('/products?keyword=balo&sort=NEWEST');
    expect(screen.getByRole('heading', { name: 'Kết quả tìm kiếm cho “balo”' })).toBeTruthy();
    expect(await screen.findByText('50 sản phẩm')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Balo nữ/ })).toBeInTheDocument();
    expect(lastSearch()).toMatchObject({ keyword: 'balo', sort: 'NEWEST' });
    expect(screen.getByText(/Không tải được danh sách thương hiệu/)).toBeInTheDocument();
  });

  it('đổi bộ lọc, khoảng giá, đánh giá, trang thì ghi lên URL và tải lại', async () => {
    renderPage();
    await screen.findByText('50 sản phẩm');
    await screen.findByRole('option', { name: 'Balo' });

    await userEvent.selectOptions(screen.getByLabelText('Danh mục'), '2');
    await waitFor(() => expect(lastSearch()).toMatchObject({ categoryId: '2' }));
    expect(screen.getByRole('button', { name: 'Bộ lọc (1)' })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Giá thấp nhất'), '300');
    await userEvent.type(screen.getByLabelText('Giá cao nhất'), '200');
    await userEvent.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(screen.getByText(/Giá thấp nhất phải nhỏ hơn/)).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Giá cao nhất'));
    await userEvent.type(screen.getByLabelText('Giá cao nhất'), '900');
    await userEvent.click(screen.getByRole('button', { name: 'Áp dụng' }));
    await waitFor(() => expect(lastSearch()).toMatchObject({ minPrice: '300', maxPrice: '900' }));

    await userEvent.click(screen.getByRole('radio', { name: 'Từ 4 sao' }));
    await waitFor(() => expect(lastSearch()).toMatchObject({ minRating: '4' }));

    await userEvent.selectOptions(screen.getByLabelText('Sắp xếp'), 'PRICE_ASC');
    await waitFor(() => expect(lastSearch()).toMatchObject({ sort: 'PRICE_ASC' }));

    await userEvent.click(await screen.findByRole('button', { name: 'Sau ›' }));
    await waitFor(() => expect(lastSearch()).toMatchObject({ page: '1' }));
  });

  it('không có kết quả thì gợi ý cách thoát và sản phẩm liên quan', async () => {
    vi.mocked(productApi.searchProducts).mockResolvedValue(page([]));
    renderPage('/products?keyword=xyz&categoryId=2');
    expect(await screen.findByText('Không tìm thấy kết quả cho “xyz”')).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: 'Xóa bộ lọc' })[0]).toHaveAttribute(
      'href',
      '/products?keyword=xyz'
    );
    expect(await screen.findByRole('link', { name: /Gợi ý 1/ })).toBeInTheDocument();
    expect(productApi.getProductSuggestions).toHaveBeenCalledWith('xyz');
  });

  it('trang vượt quá số trang, tải lỗi thì cho thử lại', async () => {
    vi.mocked(productApi.searchProducts).mockResolvedValueOnce(page([], { totalElements: 5 }));
    const { unmount } = renderPage('/products?page=7');
    expect(await screen.findByRole('link', { name: 'Về trang đầu' })).toBeInTheDocument();
    unmount();

    vi.mocked(productApi.searchProducts).mockRejectedValueOnce({
      response: { data: { detail: 'Máy chủ lỗi' } },
    });
    renderPage();
    expect(await screen.findByText('Máy chủ lỗi')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('50 sản phẩm')).toBeInTheDocument();
  });
});
