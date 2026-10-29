import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import QuantityStepper from '../../src/components/QuantityStepper';

function renderStepper(props) {
  const onChange = vi.fn();
  render(<QuantityStepper value={2} max={5} onChange={onChange} label="Số lượng" {...props} />);
  return { onChange, input: screen.getByRole('spinbutton', { name: 'Số lượng' }) };
}

describe('QuantityStepper', () => {
  it('nút − và + đổi số lượng 1 đơn vị', async () => {
    const { onChange } = renderStepper();
    await userEvent.click(screen.getByRole('button', { name: 'Giảm số lượng' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tăng số lượng' }));
    expect(onChange.mock.calls).toEqual([[1], [3]]);
  });

  it('khóa nút ở 2 đầu khoảng và khi disabled', () => {
    const { unmount } = render(
      <QuantityStepper value={1} max={1} onChange={() => {}} label="Số lượng" />
    );
    expect(screen.getByRole('button', { name: 'Giảm số lượng' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Tăng số lượng' })).toBeDisabled();
    unmount();
    const { input } = renderStepper({ disabled: true });
    expect(input).toBeDisabled();
  });

  it('gõ tay chỉ áp dụng khi nhấn Enter hoặc rời ô, kéo về trong khoảng', async () => {
    const { onChange, input } = renderStepper();
    await userEvent.clear(input);
    await userEvent.type(input, '9');
    expect(onChange).not.toHaveBeenCalled();
    await userEvent.keyboard('{Enter}');
    expect(onChange).toHaveBeenLastCalledWith(5);

    fireEvent.change(input, { target: { value: '0' } });
    fireEvent.blur(input);
    expect(onChange).toHaveBeenLastCalledWith(1);
  });

  it('gõ giá trị không phải số hoặc bằng giá trị cũ thì không đổi', () => {
    const { onChange, input } = renderStepper();
    fireEvent.change(input, { target: { value: '' } });
    fireEvent.blur(input);
    fireEvent.change(input, { target: { value: '2' } });
    fireEvent.blur(input);
    expect(onChange).not.toHaveBeenCalled();
    expect(input).toHaveValue(2);
  });
});
