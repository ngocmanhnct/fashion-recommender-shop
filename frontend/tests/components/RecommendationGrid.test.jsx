import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import RecommendationGrid from '../../src/components/RecommendationGrid';
import { logRecommendationEvent } from '../../src/services/recommendationApi';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/recommendationApi');

function product(id, extra = {}) {
  return {
    id,
    name: `Sản phẩm ${id}`,
    minPrice: 100000,
    maxPrice: 100000,
    inStock: true,
    ...extra,
  };
}

const ITEMS = [
  { product: product(1), reason: 'Hay mua cùng', source: 'BEHAVIOR' },
  { product: product(2), reason: 'Bán chạy', source: 'POPULAR' },
];

function renderGrid(auth = authValue({ user: users.CUSTOMER }), items = ITEMS) {
  return renderWithContext(<RecommendationGrid items={items} placement="HOME" />, { auth });
}

describe('RecommendationGrid', () => {
  afterEach(() => vi.unstubAllGlobals());

  describe('trình duyệt không có IntersectionObserver', () => {
    it('ghi lượt hiện ngay, mỗi danh sách 1 lần, rồi ghi lượt bấm', async () => {
      const { rerender } = renderGrid();
      expect(logRecommendationEvent).toHaveBeenCalledWith('SHOWN', 'HOME', [
        { productId: 1, source: 'BEHAVIOR', rank: 1 },
        { productId: 2, source: 'POPULAR', rank: 2 },
      ]);
      expect(screen.getByText('Hay mua cùng')).toBeInTheDocument();

      rerender(<RecommendationGrid items={[...ITEMS]} placement="HOME" />);
      await userEvent.click(screen.getByRole('link', { name: /Sản phẩm 2/ }));
      expect(logRecommendationEvent).toHaveBeenCalledTimes(2);
      expect(logRecommendationEvent).toHaveBeenLastCalledWith('CLICKED', 'HOME', [
        { productId: 2, source: 'POPULAR', rank: 2 },
      ]);
    });

    it('quản trị viên và danh sách rỗng không được ghi', async () => {
      renderGrid(authValue({ user: users.ADMIN }));
      await userEvent.click(screen.getByRole('link', { name: /Sản phẩm 1/ }));
      renderGrid(undefined, []);
      expect(logRecommendationEvent).not.toHaveBeenCalled();
    });
  });

  describe('có IntersectionObserver', () => {
    let observers;

    beforeEach(() => {
      observers = [];
      vi.stubGlobal(
        'IntersectionObserver',
        class {
          constructor(callback, options) {
            this.callback = callback;
            this.options = options;
            this.disconnect = vi.fn();
            observers.push(this);
          }
          observe() {}
        }
      );
    });

    it('chỉ ghi lượt hiện khi khối lọt vào màn hình', () => {
      renderGrid();
      expect(observers[0].options).toEqual({ threshold: 0.3 });
      act(() => observers[0].callback([{ isIntersecting: false }]));
      expect(logRecommendationEvent).not.toHaveBeenCalled();
      act(() => observers[0].callback([{ isIntersecting: true }]));
      expect(logRecommendationEvent).toHaveBeenCalledWith('SHOWN', 'HOME', expect.any(Array));
      expect(observers[0].disconnect).toHaveBeenCalled();
    });

    it('bấm trước khi kịp ghi lượt hiện thì ghi lượt hiện trước', async () => {
      renderGrid();
      await userEvent.click(screen.getByRole('link', { name: /Sản phẩm 1/ }));
      expect(vi.mocked(logRecommendationEvent).mock.calls.map((c) => c[0])).toEqual([
        'SHOWN',
        'CLICKED',
      ]);
    });
  });
});
