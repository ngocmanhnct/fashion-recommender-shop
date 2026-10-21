import { useState } from 'react';

/**
 * Ô số lượng có nút − và +, giá trị từ 1 đến max. Gõ tay thì chỉ áp dụng khi rời ô hoặc nhấn
 * Enter, giá trị ngoài khoảng được kéo về 1 hoặc max.
 */
function QuantityStepper({ value, max, onChange, disabled = false, label }) {
  const [draft, setDraft] = useState(null);

  const commit = (raw) => {
    setDraft(null);
    const parsed = Number.parseInt(raw, 10);
    const next = Number.isNaN(parsed) ? value : Math.min(Math.max(parsed, 1), max);
    if (next !== value) onChange(next);
  };

  return (
    <div className="quantity-stepper" role="group" aria-label={label}>
      <button
        type="button"
        className="btn btn-ghost"
        aria-label="Giảm số lượng"
        disabled={disabled || value <= 1}
        onClick={() => onChange(value - 1)}
      >
        −
      </button>
      <input
        type="number"
        inputMode="numeric"
        min={1}
        max={max}
        aria-label={label}
        value={draft ?? value}
        disabled={disabled}
        onChange={(e) => setDraft(e.target.value)}
        onBlur={(e) => commit(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') commit(e.currentTarget.value);
        }}
      />
      <button
        type="button"
        className="btn btn-ghost"
        aria-label="Tăng số lượng"
        disabled={disabled || value >= max}
        onClick={() => onChange(value + 1)}
      >
        +
      </button>
    </div>
  );
}

export default QuantityStepper;
