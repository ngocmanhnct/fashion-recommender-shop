import { useState } from 'react';
import { useApiData } from '../../hooks/useApiData';
import {
  getAbTestResult,
  getRecommendationCtr,
  listAbTests,
  startAbTest,
  stopAbTest,
} from '../../services/abTestApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber, formatPrice } from '../../services/format';
import { formatDateTime } from '../../services/orderLabels';
import {
  describeVerdict,
  PLACEMENT_LABELS,
  SOURCE_LABELS,
} from '../../services/recommendationLabels';

const PERCENT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });

const COMPARISONS = [
  { key: 'clickRate', label: 'Khách bấm gợi ý ít nhất 1 lần' },
  { key: 'orderRate', label: 'Khách đặt hàng' },
  { key: 'boughtClickedRate', label: 'Khách mua sản phẩm đã bấm từ gợi ý' },
];

const CTR_RANGES = [7, 30, 90];

const percent = (value) => (value == null ? '–' : `${PERCENT.format(value)}%`);

function isoDate(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * Trang quản trị /admin/recommendations: thử nghiệm A/B gợi ý ở trang chủ (US-27; khách có danh
 * sách gợi ý riêng được chia ngẫu nhiên vào nhóm thấy gợi ý học máy hoặc nhóm thấy sản phẩm bán
 * chạy, so sánh bằng kiểm định z hai tỷ lệ) và tỷ lệ bấm theo vị trí, nguồn gợi ý.
 */
function RecommendationStatsPage() {
  return (
    <div>
      <div className="page-header">
        <h1>Hiệu quả gợi ý</h1>
      </div>
      <AbTestSection />
      <CtrSection />
    </div>
  );
}

function AbTestSection() {
  const { data: tests, error, loading, reload } = useApiData('ab-tests', listAbTests);
  const [selected, setSelected] = useState(null);
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');

  if (loading && !tests) return <p className="muted">Đang tải thử nghiệm...</p>;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được thử nghiệm')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  const running = tests.find((t) => t.running) ?? null;
  const current = tests.find((t) => t.id === selected) ?? tests[0] ?? null;

  const handleStart = async (request) => {
    setMessage('');
    setActionError('');
    try {
      const started = await startAbTest(request);
      setSelected(started.id);
      setMessage(`Đã bắt đầu thử nghiệm "${started.name}"`);
      reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'Không bắt đầu được thử nghiệm'));
    }
  };

  const handleStop = async () => {
    if (!window.confirm(`Dừng thử nghiệm "${running.name}"? Mọi khách sẽ thấy gợi ý như cũ.`)) {
      return;
    }
    setMessage('');
    setActionError('');
    try {
      await stopAbTest(running.id);
      setMessage(`Đã dừng thử nghiệm "${running.name}"`);
      reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'Không dừng được thử nghiệm'));
    }
  };

  return (
    <section className="checkout-section ab-test">
      <h2>Thử nghiệm A/B: gợi ý học máy so với sản phẩm bán chạy</h2>
      <p className="muted small">
        Khi thử nghiệm chạy, khách đã có danh sách gợi ý riêng được chia ngẫu nhiên (cố định theo
        tài khoản) lúc mở trang chủ: nhóm A thấy gợi ý học máy như bình thường, nhóm B thấy sản phẩm
        bán chạy ở cùng chỗ. Chỉ tính hành động của khách sau lúc được chia nhóm. Hai tỷ lệ được so
        bằng kiểm định z, khác biệt có ý nghĩa khi p &lt; 0,05.
      </p>
      {message && (
        <p className="alert alert-success" role="status">
          {message}
        </p>
      )}
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}
      {running ? (
        <div className="ab-running">
          <span className="status status-delivered">Đang chạy</span>
          <span>
            <strong>{running.name}</strong> từ {formatDateTime(running.startedAt)}, nhóm gợi ý{' '}
            {running.treatmentPercent}% khách, nhóm bán chạy {100 - running.treatmentPercent}%.
          </span>
          <button type="button" className="btn btn-danger" onClick={handleStop}>
            Dừng thử nghiệm
          </button>
        </div>
      ) : (
        <StartForm onStart={handleStart} />
      )}

      {current && (
        <>
          {tests.length > 1 && (
            <label className="ab-picker">
              Xem kết quả
              <select
                value={current.id}
                onChange={(event) => setSelected(Number(event.target.value))}
              >
                {tests.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name} ({formatDateTime(t.startedAt)}
                    {t.running ? ', đang chạy' : ''})
                  </option>
                ))}
              </select>
            </label>
          )}
          <AbTestResult key={current.id} id={current.id} />
        </>
      )}
    </section>
  );
}

function StartForm({ onStart }) {
  const [name, setName] = useState('Gợi ý học máy so với bán chạy');
  const [treatmentPercent, setTreatmentPercent] = useState('50');
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setBusy(true);
    await onStart({ name: name.trim(), treatmentPercent: Number(treatmentPercent) });
    setBusy(false);
  };

  return (
    <form className="admin-panel ab-start" onSubmit={handleSubmit}>
      <div className="field">
        <label className="field-label" htmlFor="ab-name">
          Tên thử nghiệm
        </label>
        <input
          id="ab-name"
          value={name}
          maxLength={100}
          required
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <div className="field">
        <label className="field-label" htmlFor="ab-percent">
          Phần trăm khách vào nhóm gợi ý học máy
        </label>
        <input
          id="ab-percent"
          type="number"
          min={10}
          max={90}
          required
          value={treatmentPercent}
          onChange={(event) => setTreatmentPercent(event.target.value)}
        />
        <span className="muted small">Từ 10 đến 90. Chia đều (50) cho kết luận nhanh nhất.</span>
      </div>
      <button type="submit" className="btn btn-primary" disabled={busy}>
        Bắt đầu thử nghiệm
      </button>
    </form>
  );
}

function AbTestResult({ id }) {
  const { data, error, loading, reload } = useApiData(String(id), getAbTestResult);
  if (loading) return <p className="muted">Đang tính kết quả...</p>;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được kết quả thử nghiệm')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }
  const { experiment, treatment: a, control: b, minUsersPerGroup } = data;
  const rows = [
    ['Khách trong nhóm', formatNumber(a.users), formatNumber(b.users)],
    ['Lượt hiện sản phẩm gợi ý', formatNumber(a.shown), formatNumber(b.shown)],
    ['Lượt bấm', formatNumber(a.clicks), formatNumber(b.clicks)],
    ['Tỷ lệ bấm (lượt bấm ÷ lượt hiện)', percent(a.ctr), percent(b.ctr)],
    ['Khách bấm ít nhất 1 lần', percent(a.clickRate), percent(b.clickRate)],
    ['Khách đặt hàng', percent(a.orderRate), percent(b.orderRate)],
    [
      'Khách mua sản phẩm đã bấm từ gợi ý',
      percent(a.boughtClickedRate),
      percent(b.boughtClickedRate),
    ],
    ['Đơn hàng (chưa hủy)', formatNumber(a.orders), formatNumber(b.orders)],
    ['Doanh thu', formatPrice(a.revenue), formatPrice(b.revenue)],
    [
      'Doanh thu trên mỗi khách',
      a.revenuePerUser == null ? '–' : formatPrice(a.revenuePerUser),
      b.revenuePerUser == null ? '–' : formatPrice(b.revenuePerUser),
    ],
  ];
  return (
    <div className="ab-result">
      <p className="muted small">
        {experiment.running
          ? `Số liệu từ ${formatDateTime(experiment.startedAt)} đến bây giờ.`
          : `Số liệu từ ${formatDateTime(experiment.startedAt)} đến ${formatDateTime(experiment.endedAt)} (đã dừng).`}
      </p>
      <div className="table-scroll">
        <table className="table ab-table">
          <thead>
            <tr>
              <th>Chỉ số</th>
              <th>Nhóm A: gợi ý học máy</th>
              <th>Nhóm B: bán chạy</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([label, left, right]) => (
              <tr key={label}>
                <td>{label}</td>
                <td>{left}</td>
                <td>{right}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <h3>Kết luận</h3>
      <ul className="ab-verdicts">
        {COMPARISONS.map(({ key, label }) => {
          const comparison = data[key];
          return (
            <li key={key} className={`ab-verdict ab-verdict-${comparison.verdict.toLowerCase()}`}>
              <strong>{label}</strong>
              {comparison.difference != null && (
                <span>
                  A − B = {comparison.difference > 0 ? '+' : ''}
                  {PERCENT.format(comparison.difference)} điểm phần trăm
                </span>
              )}
              <span>{describeVerdict(comparison, minUsersPerGroup)}</span>
            </li>
          );
        })}
      </ul>
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
      <div className="section-header ab-ctr-header">
        <h2>Tỷ lệ bấm theo vị trí</h2>
        <label className="ab-picker">
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
          <table className="table ab-table">
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
