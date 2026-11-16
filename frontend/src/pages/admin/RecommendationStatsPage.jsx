import { useState } from 'react';
import { useApiData } from '../../hooks/useApiData';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber } from '../../services/format';
import { getRecommendationCtr } from '../../services/recommendationAdminApi';
import { PLACEMENT_LABELS, SOURCE_LABELS } from '../../services/recommendationLabels';

const PERCENT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });

const CTR_RANGES = [7, 30, 90];

const percent = (value) => (value == null ? '–' : `${PERCENT.format(value)}%`);

function isoDate(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Trang quản trị /admin/recommendations (US-13): tỷ lệ bấm gợi ý theo vị trí, nguồn gợi ý. */
function RecommendationStatsPage() {
  return (
    <div>
      <div className="page-header">
        <h1>Hiệu quả gợi ý</h1>
      </div>
      <CtrSection />
    </div>
  );
}

function CtrSection() {
  const [days, setDays] = useState(30);
  const { data, lastData, error, reload } = useApiData(String(days), (key) => {
    const to = new Date();
    const from = new Date(to);
    from.setDate(to.getDate() - (Number(key) - 1));
    return getRecommendationCtr({ from: isoDate(from), to: isoDate(to) });
  });
  const result = data ?? lastData;

  return (
    <section className="checkout-section">
      <div className="section-header ctr-header">
        <h2>Tỷ lệ bấm theo vị trí</h2>
        <label className="ctr-picker">
          Khoảng thời gian
          <select value={days} onChange={(event) => setDays(Number(event.target.value))}>
            {CTR_RANGES.map((n) => (
              <option key={n} value={n}>
                {n} ngày gần nhất
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="muted small">
        Mọi khách, kể cả khách chưa đăng nhập. Mỗi lần một khối gợi ý hiện trên màn hình, mỗi sản
        phẩm trong khối tính 1 lượt hiện.
      </p>
      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được tỷ lệ bấm')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {result && (
        <div className="table-scroll">
          <table className="table ctr-table">
            <thead>
              <tr>
                <th>Vị trí</th>
                <th>Nguồn</th>
                <th>Lượt hiện</th>
                <th>Lượt bấm</th>
                <th>Tỷ lệ bấm</th>
              </tr>
            </thead>
            <tbody>
              {result.rows.map((row) => (
                <tr key={`${row.placement}-${row.source}`}>
                  <td>{PLACEMENT_LABELS[row.placement] ?? row.placement}</td>
                  <td>{SOURCE_LABELS[row.source] ?? row.source}</td>
                  <td>{formatNumber(row.shown)}</td>
                  <td>{formatNumber(row.clicked)}</td>
                  <td>{percent(row.ctr)}</td>
                </tr>
              ))}
              {result.rows.length === 0 && (
                <tr>
                  <td colSpan={5} className="muted">
                    Chưa có lượt hiện gợi ý nào trong khoảng này.
                  </td>
                </tr>
              )}
            </tbody>
            {result.rows.length > 0 && (
              <tfoot>
                <tr>
                  <th colSpan={2}>Tổng</th>
                  <th>{formatNumber(result.total.shown)}</th>
                  <th>{formatNumber(result.total.clicked)}</th>
                  <th>{percent(result.total.ctr)}</th>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </section>
  );
}

export default RecommendationStatsPage;
