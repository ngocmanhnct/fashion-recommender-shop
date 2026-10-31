import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import Reveal from '../../src/components/Reveal';

describe('Reveal', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('không có IntersectionObserver thì hiện ngay', () => {
    render(<Reveal as="section">Nội dung</Reveal>);
    const block = screen.getByText('Nội dung');
    expect(block.tagName).toBe('SECTION');
    expect(block).toHaveClass('reveal', 'is-visible');
  });

  it('chỉ hiện khi lần đầu cuộn tới, rồi thôi theo dõi', () => {
    let callback;
    const disconnect = vi.fn();
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(cb) {
          callback = cb;
        }
        observe() {}
        disconnect() {
          disconnect();
        }
      }
    );
    render(<Reveal className="khoi">Nội dung</Reveal>);
    const block = screen.getByText('Nội dung');
    expect(block).not.toHaveClass('is-visible');
    expect(block).toHaveClass('khoi');

    act(() => callback([{ isIntersecting: false }]));
    expect(block).not.toHaveClass('is-visible');
    act(() => callback([{ isIntersecting: true }]));
    expect(block).toHaveClass('is-visible');
    expect(disconnect).toHaveBeenCalled();
  });
});
