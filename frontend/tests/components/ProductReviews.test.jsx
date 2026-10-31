import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ProductReviews from '../../src/components/ProductReviews';
import { getMyReview, getProductReviews, saveMyReview } from '../../src/services/reviewApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/reviewApi');

const review = (id, rating, extra = {}) => ({
  id,
  reviewerName: `Khách ${id}`,
  rating,
  comment: null,
  variantLabel: null,
  createdAt: '2026-10-30T08:00:00',
  updatedAt: '2026-10-30T08:00:00',
  ...extra,
});

function summary(content, totalElements = content.length) {
  return {
    ratingAverage: 4.5,
    reviewCount: totalElements,
    starCounts: [0, 0, 0, 1, totalElements - 1],
    reviews: { content, page: 0, size: content.length, totalElements, totalPages: 1 },
  };
}

function renderReviews(user = null) {
  return renderWithContext(<ProductReviews productId={3} />, {
    auth: authValue({ user }),
    path: '/products/3',
  });
}

describe('ProductReviews', () => {
  beforeEach(() => {
    getProductReviews.mockResolvedValue(
      summary([review(1, 5, { comment: 'Rất đẹp', variantLabel: 'Đen' }), review(2, 4)], 6)
    );
    getMyReview.mockResolvedValue({ canReview: false, review: null });
    saveMyReview.mockResolvedValue(review(9, 4));
  });

  it('hiện điểm trung bình, số đánh giá theo sao, danh sách và tải thêm', async () => {
    renderReviews();
    expect(await screen.findByText('Rất đẹp')).toBeInTheDocument();
    expect(screen.getByText('4,5')).toBeInTheDocument();
    expect(screen.getByText(/6 đánh giá/)).toBeInTheDocument();
    expect(screen.getByLabelText('5 trên 5 sao')).toBeInTheDocument();
    expect(screen.getByText(/Phân loại: Đen/)).toBeInTheDocument();
    expect(getMyReview).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole('button', { name: 'Xem thêm đánh giá' }));
    await waitFor(() => expect(getProductReviews).toHaveBeenLastCalledWith(3, 0, 10));
  });

  it('chưa có đánh giá thì báo; khách chưa nhận hàng thì không có ô đánh giá', async () => {
    getProductReviews.mockResolvedValue({ ...summary([]), ratingAverage: null });
    renderReviews(users.CUSTOMER);
    expect(await screen.findByText('Chưa có đánh giá nào.')).toBeInTheDocument();
    expect(
      await screen.findByText('Bạn có thể đánh giá sau khi nhận được sản phẩm này.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Gửi đánh giá' })).not.toBeInTheDocument();
  });

  it('khách đã nhận hàng chọn sao, viết nhận xét rồi gửi; danh sách tải lại', async () => {
    getMyReview.mockResolvedValue({ canReview: true, review: null });
    renderReviews(users.CUSTOMER);
    const submit = await screen.findByRole('button', { name: 'Gửi đánh giá' });

    await userEvent.click(submit);
    expect(await screen.findByRole('alert')).toHaveTextContent('Hãy chọn số sao');
    expect(saveMyReview).not.toHaveBeenCalled();

    await userEvent.click(screen.getByLabelText('4 sao'));
    await userEvent.type(screen.getByLabelText('Nhận xét (không bắt buộc)'), 'Ổn');
    await userEvent.click(submit);
    expect(saveMyReview).toHaveBeenCalledWith(3, { rating: 4, comment: 'Ổn' });
    expect(await screen.findByRole('status')).toHaveTextContent('Đã lưu đánh giá của bạn.');
    expect(screen.getByRole('button', { name: 'Cập nhật đánh giá' })).toBeInTheDocument();
    await waitFor(() => expect(getProductReviews).toHaveBeenCalledTimes(2));
  });

  it('đã đánh giá thì hiện sẵn để sửa; lỗi từ máy chủ hiện dưới ô', async () => {
    getMyReview.mockResolvedValue({ canReview: true, review: review(9, 3, { comment: 'Tạm' }) });
    saveMyReview.mockRejectedValue({
      response: { data: { detail: 'Dữ liệu không hợp lệ', errors: { comment: 'Quá dài' } } },
    });
    renderReviews(users.CUSTOMER);
    expect(await screen.findByLabelText('3 sao')).toBeChecked();
    expect(screen.getByLabelText('Nhận xét (không bắt buộc)')).toHaveValue('Tạm');
    await userEvent.click(screen.getByRole('button', { name: 'Cập nhật đánh giá' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Quá dài');
  });

  it('tải lỗi thì báo lỗi; quản trị viên không có ô đánh giá', async () => {
    getProductReviews.mockRejectedValue({ response: { data: { detail: 'Lỗi máy chủ' } } });
    renderReviews(users.ADMIN);
    expect(await screen.findByText('Lỗi máy chủ')).toBeInTheDocument();
    expect(getMyReview).not.toHaveBeenCalled();
  });
});
