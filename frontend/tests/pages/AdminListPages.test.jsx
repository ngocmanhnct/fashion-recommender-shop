import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import OrderListPage from '../../src/pages/admin/OrderListPage';
import ProductListPage from '../../src/pages/admin/ProductListPage';
import * as catalogApi from '../../src/services/catalogApi';
import * as orderAdminApi from '../../src/services/orderAdminApi';
import * as productAdminApi from '../../src/services/productAdminApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/orderAdminApi');
vi.mock('../../src/services/productAdminApi');
vi.mock('../../src/services/catalogApi');

const emptyPage = { content: [], page: 0, totalPages: 1, totalElements: 0 };
const product = (id, name) => ({
  id,
  name,
  categoryName: 'Giày',
  brandName: null,
  minPrice: 100000,
  maxPrice: 100000,
  totalStock: 3,
  deletedAt: null,
});

describe('trang danh sách quản trị (issue #104)', () => {
  beforeEach(() => {
    vi.mocked(orderAdminApi.searchOrders).mockResolvedValue(emptyPage);
    vi.mocked(catalogApi.getCategories).mockResolvedValue([]);
  });

  it('tab Hoàn tiền thất bại giữ số trang trên URL và không chọn kèm tab Tất cả', async () => {
    renderWithContext(<OrderListPage />, {
      auth: authValue({ user: users.ADMIN }),
      path: '/admin/orders?status=REFUND_FAILED&page=2',
    });
    await waitFor(() =>
      expect(orderAdminApi.searchOrders).toHaveBeenCalledWith({
        status: '',
        refundStatus: 'FAILED',
        keyword: '',
        page: 1,
      })
    );
    expect(screen.getByRole('tab', { name: 'Hoàn tiền thất bại' })).toHaveAttribute(
      'aria-selected',
      'true'
    );
    expect(screen.getByRole('tab', { name: 'Tất cả' })).toHaveAttribute('aria-selected', 'false');
  });

  it('danh sách sản phẩm chỉ nhận kết quả lần lọc mới nhất và xóa lỗi cũ', async () => {
    let resolveSlow;
    vi.mocked(productAdminApi.searchProducts)
      .mockRejectedValueOnce({ response: { data: { detail: 'Lỗi tải lần đầu' } } })
      .mockImplementationOnce(() => new Promise((resolve) => (resolveSlow = resolve)))
      .mockResolvedValueOnce({ ...emptyPage, content: [product(2, 'Giày mới')], totalElements: 1 });
    renderWithContext(<ProductListPage />, { auth: authValue({ user: users.ADMIN }) });
    expect(await screen.findByText('Lỗi tải lần đầu')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Tìm sản phẩm theo tên'), 'a');
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));
    await userEvent.clear(screen.getByLabelText('Tìm sản phẩm theo tên'));
    await userEvent.type(screen.getByLabelText('Tìm sản phẩm theo tên'), 'giày');
    await userEvent.click(screen.getByRole('button', { name: 'Lọc' }));
    expect(await screen.findByText('Giày mới')).toBeInTheDocument();
    expect(screen.queryByText('Lỗi tải lần đầu')).not.toBeInTheDocument();

    resolveSlow({ ...emptyPage, content: [product(1, 'Kết quả cũ')], totalElements: 1 });
    await new Promise((r) => setTimeout(r, 0));
    expect(screen.queryByText('Kết quả cũ')).not.toBeInTheDocument();
    expect(screen.getByText('Giày mới')).toBeInTheDocument();
  });
});
