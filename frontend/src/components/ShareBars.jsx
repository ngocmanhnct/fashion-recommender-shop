import { formatNumber } from '../services/format';

const DECIMAL = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });

/**
 * Thanh tỷ lệ nằm ngang: nhãn, thanh dài theo %, số lượng kèm %. Mỗi dòng của rows là
 * { key, label, count, unit, share } với share tính theo phần trăm (0–100).
 */
function ShareBars({ label, rows }) {
  return (
    <ul className="share-bars" aria-label={label}>
      {rows.map((row) => (
        <li key={row.key} className={row.count === 0 ? 'is-empty' : undefined}>
          <span className="share-bar-label">{row.label}</span>
          <span className="share-bar-track" aria-hidden="true">
            <span className="share-bar-fill" style={{ width: `${row.share}%` }} />
          </span>
          <span className="share-bar-value">
            {formatNumber(row.count)} {row.unit} · {DECIMAL.format(row.share)}%
          </span>
        </li>
      ))}
    </ul>
  );
}

export default ShareBars;
