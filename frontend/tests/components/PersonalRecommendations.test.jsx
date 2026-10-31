import { act, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PersonalRecommendations from '../../src/components/PersonalRecommendations';
import { AuthContext } from '../../src/context/authContext';
import { CartContext } from '../../src/context/cartContext';
import { useSurveyState } from '../../src/hooks/useSurveyState';
import { getMyRecommendations } from '../../src/services/recommendationApi';
import { authValue, cartValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/recommendationApi');
vi.mock('../../src/hooks/useSurveyState');

const SEEN_KEY = `goi-y-da-thay:${users.CUSTOMER.id}`;

function item(id, extra = {}) {
  return {
    product: { id, name: `Sản phẩm ${id}`, minPrice: 100000, maxPrice: 100000, inStock: true },
    reason: `Lý do ${id}`,
    source: 'BEHAVIOR',
    ...extra,
  };
}

function response(overrides = {}) {
  return {
    source: 'BEHAVIOR',
    products: [item(1), item(2)],
    recent: null,
    updatedSecondsAgo: 125,
    updating: false,
    ...overrides,
  };
}

function renderHome() {
  return renderWithContext(<PersonalRecommendations />, {
    auth: authValue({ user: users.CUSTOMER }),
  });
}

function card(name) {
  return screen.getByRole('link', { name: new RegExp(name) });
}

describe('PersonalRecommendations', () => {
  beforeEach(() => {
    useSurveyState.mockReturnValue({ status: 'NONE', answers: [], loading: false, skip: vi.fn() });
  });

  afterEach(() => vi.useRealTimers());

  it('hiện hàng "Vì bạn vừa xem", danh sách kèm giờ cập nhật và nhớ sản phẩm đã thấy', async () => {
    getMyRecommendations.mockResolvedValue(
      response({
        recent: {
          kind: 'VIEW',
          product: { id: 9, name: 'Giày vừa xem' },
          products: [item(5, { source: 'RELATED', reason: 'Tương tự' })],
        },
      })
    );
    renderHome();

    const heading = await screen.findByRole('heading', { name: /Vì bạn vừa xem/ });
    expect(within(heading).getByRole('link', { name: 'Giày vừa xem' })).toHaveAttribute(
      'href',
      '/products/9'
    );
    expect(screen.getByText('Tương tự')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Gợi ý cho bạn' })).toBeInTheDocument();
    expect(screen.getByText('Cập nhật 2 phút trước')).toBeInTheDocument();
    // Lần đầu chưa có gì để so nên không có nhãn "Mới".
    expect(screen.queryByText('Mới')).not.toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem(SEEN_KEY))).toEqual([1, 2]);
  });

  it('đánh dấu "Mới" sản phẩm chưa có ở lần trước', async () => {
    localStorage.setItem(SEEN_KEY, JSON.stringify([1, 3]));
    getMyRecommendations.mockResolvedValue(response());
    renderHome();

    await screen.findByText('Sản phẩm 2');
    expect(within(card('Sản phẩm 2')).getByText('Mới')).toBeInTheDocument();
    expect(within(card('Sản phẩm 1')).queryByText('Mới')).not.toBeInTheDocument();
  });

  it('tự tải lại khi gợi ý đang được tính lại rồi hiện danh sách mới', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    localStorage.setItem(SEEN_KEY, JSON.stringify([1, 2]));
    getMyRecommendations
      .mockResolvedValueOnce(response({ updating: true }))
      .mockResolvedValueOnce(response({ products: [item(4), item(1)] }));
    renderHome();

    expect(await screen.findByRole('status')).toHaveTextContent('Đang cập nhật');
    expect(screen.queryByText(/Cập nhật \d|Cập nhật vừa/)).not.toBeInTheDocument();
    await act(() => vi.advanceTimersByTimeAsync(2500));

    expect(await screen.findByText('Sản phẩm 4')).toBeInTheDocument();
    expect(getMyRecommendations).toHaveBeenCalledTimes(2);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(within(card('Sản phẩm 4')).getByText('Mới')).toBeInTheDocument();
    expect(screen.queryByText('Sản phẩm 2')).not.toBeInTheDocument();
  });

  it('ngừng tải lại sau số lần tối đa', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    getMyRecommendations.mockResolvedValue(response({ updating: true }));
    renderHome();
    await screen.findByRole('status');

    for (let i = 0; i < 8; i++) {
      await act(() => vi.advanceTimersByTimeAsync(2500));
    }
    expect(getMyRecommendations).toHaveBeenCalledTimes(7);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    expect(screen.getByText(/Cập nhật \d|Cập nhật vừa/)).toBeInTheDocument();
  });

  it('chưa có danh sách riêng thì hiện hàng vừa thêm giỏ và lời mời khảo sát', async () => {
    getMyRecommendations.mockResolvedValue(
      response({
        source: 'POPULAR',
        products: [item(7, { source: 'POPULAR' })],
        updatedSecondsAgo: null,
        recent: {
          kind: 'CART',
          product: { id: 8, name: 'Balo trong giỏ' },
          products: [item(6, { source: 'RELATED' })],
        },
      })
    );
    renderHome();

    expect(
      await screen.findByRole('heading', { name: /Vì bạn vừa thêm vào giỏ Balo trong giỏ/ })
    ).toBeInTheDocument();
    expect(screen.getByText('Bạn đang tìm gì?')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Gợi ý cho bạn' })).not.toBeInTheDocument();
    expect(localStorage.getItem(SEEN_KEY)).toBeNull();
  });

  it('tài khoản biết sau khi tải xong vẫn so với danh sách lần trước', async () => {
    localStorage.setItem(SEEN_KEY, JSON.stringify([1]));
    getMyRecommendations.mockResolvedValue(response());
    const tree = (auth) => (
      <AuthContext.Provider value={auth}>
        <CartContext.Provider value={cartValue()}>
          <MemoryRouter>
            <PersonalRecommendations />
          </MemoryRouter>
        </CartContext.Provider>
      </AuthContext.Provider>
    );
    const { rerender } = render(tree(authValue({ loading: true })));
    rerender(tree(authValue({ user: users.CUSTOMER })));

    await screen.findByText('Sản phẩm 2');
    expect(within(card('Sản phẩm 2')).getByText('Mới')).toBeInTheDocument();
  });

  it('khách chưa đăng nhập không gọi API, chỉ hiện phần khảo sát', () => {
    renderWithContext(<PersonalRecommendations />);
    expect(getMyRecommendations).not.toHaveBeenCalled();
    expect(screen.getByText('Bạn đang tìm gì?')).toBeInTheDocument();
  });
});
