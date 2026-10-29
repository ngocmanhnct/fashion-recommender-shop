import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import BrandAdminPage from '../../src/pages/admin/BrandAdminPage';
import CategoryAdminPage from '../../src/pages/admin/CategoryAdminPage';
import { brandAdminApi, categoryAdminApi } from '../../src/services/adminCatalogApi';
import * as catalogApi from '../../src/services/catalogApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/adminCatalogApi');
vi.mock('../../src/services/catalogApi');

const renderPage = (ui) => renderWithContext(ui, { auth: authValue({ user: users.ADMIN }) });

describe('Trang quản lý danh mục, thương hiệu', () => {
  beforeEach(() => {
    vi.mocked(catalogApi.getCategories).mockResolvedValue([{ id: 1, name: 'Balo' }]);
    vi.mocked(catalogApi.getBrands).mockResolvedValue([]);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
  });

  it('thêm, đổi tên, xóa danh mục rồi tải lại danh sách', async () => {
    renderPage(<CategoryAdminPage />);
    const row = (await screen.findByRole('cell', { name: 'Balo' })).closest('tr');

    await userEvent.type(screen.getByPlaceholderText('Tên danh mục mới'), '  Ví nam ');
    await userEvent.click(screen.getByRole('button', { name: 'Thêm' }));
    expect(categoryAdminApi.create).toHaveBeenCalledWith('Ví nam');
    expect(await screen.findByText('Đã thêm danh mục "Ví nam"')).toBeInTheDocument();
    expect(screen.getByPlaceholderText('Tên danh mục mới')).toHaveValue('');

    await userEvent.click(within(row).getByRole('button', { name: 'Đổi tên' }));
    const input = within(row).getByRole('textbox');
    expect(input).toHaveFocus();
    await userEvent.clear(input);
    await userEvent.type(input, 'Balo nữ');
    await userEvent.click(within(row).getByRole('button', { name: 'Lưu' }));
    expect(categoryAdminApi.rename).toHaveBeenCalledWith(1, 'Balo nữ');

    await userEvent.click(within(row).getByRole('button', { name: 'Xóa' }));
    expect(window.confirm).toHaveBeenCalledWith('Xóa danh mục "Balo"?');
    expect(categoryAdminApi.remove).toHaveBeenCalledWith(1);
    await waitFor(() => expect(catalogApi.getCategories).toHaveBeenCalledTimes(4));
  });

  it('lỗi từ backend hiện trên trang; tên rỗng không gửi', async () => {
    vi.mocked(brandAdminApi.remove).mockRejectedValue({
      response: { data: { detail: 'Thương hiệu còn sản phẩm' } },
    });
    vi.mocked(catalogApi.getBrands).mockResolvedValue([{ id: 4, name: 'Adidas' }]);
    renderPage(<BrandAdminPage />);
    await userEvent.click(await screen.findByRole('button', { name: 'Xóa' }));
    expect(await screen.findByText('Thương hiệu còn sản phẩm')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Thêm' }));
    expect(brandAdminApi.create).not.toHaveBeenCalled();
  });
});
