import { useState } from 'react';
import { Link } from 'react-router';
import ShareBars from '../../components/ShareBars';
import StatTile from '../../components/StatTile';
import { useApiData } from '../../hooks/useApiData';
import { getDashboard } from '../../services/dashboardApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber, formatPrice } from '../../services/format';
import { formatDateTime, ORDER_STATUS_LABELS } from '../../services/orderLabels';
import { PLACEMENT_LABELS, SOURCE_LABELS } from '../../services/recommendationLabels';

const PRESETS = [7, 30, 90];
const DECIMAL = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 });
const SHORT = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
/** Biểu đồ có tối đa chừng này cột thì mỗi cột nhận được Tab (dài hơn thì xem dạng bảng). */
const FOCUSABLE_BARS = 92;

const percent = (value) => (value == null ? '–' : `${DECIMAL.format(value)}%`);

/** Ngày theo giờ máy người xem dạng yyyy-MM-dd. */
function isoDate(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Khoảng n ngày gần nhất, tính cả hôm nay. */
function lastDays(n, today = new Date()) {
  const from = new Date(today);
  from.setDate(today.getDate() - (n - 1));
  return { from: isoDate(from), to: isoDate(today) };
}

/** "2026-10-31" thành "31/10/2026" (hoặc "31/10" khi withYear = false). */
function formatDay(iso, withYear = true) {
  const [y, m, d] = iso.split('-');
  return withYear ? `${d}/${m}/${y}` : `${d}/${m}`;
}

/** Số tiền rút gọn cho trục biểu đồ: "1,5 tr", "120 nghìn". */
function shortMoney(value) {
  if (value >= 1e9) return `${SHORT.format(value / 1e9)} tỷ`;
  if (value >= 1e6) return `${SHORT.format(value / 1e6)} tr`;
  if (value >= 1e3) return `${SHORT.format(value / 1e3)} nghìn`;
  return SHORT.format(value);
}

/** Số tròn nhỏ nhất (1, 2, 2,5, 5 nhân lũy thừa 10) không nhỏ hơn value, làm đỉnh trục. */
function niceCeil(value) {
  if (value <= 0) return 0;
  const exp = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((f) => value <= f * exp);
  return step * exp;
}

/**
 * Trang quản trị /admin (US-28): tổng quan trong khoảng ngày chọn (mặc định 30 ngày gần nhất).
 * Doanh thu theo ngày, đơn theo trạng thái, sản phẩm bán chạy, và hiệu quả gợi ý (tỷ lệ bấm theo
 * vị trí, nguồn; doanh thu từ sản phẩm khách bấm trong gợi ý rồi mua, BR-47).
 */
function DashboardPage() {
  const [range, setRange] = useState(() => lastDays(30));
  const [draft, setDraft] = useState(range);
  const [rangeError, setRangeError] = useState('');
  const { data, lastData, error, loading, reload } = useApiData(`${range.from}_${range.to}`, () =>
    getDashboard(range)
  );
  const result = data ?? lastData;

  const applyRange = (next) => {
    setRangeError('');
    setDraft(next);
    setRange(next);
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!draft.from || !draft.to) {
      setRangeError('Chọn đủ ngày bắt đầu và ngày kết thúc');
      return;
    }
    if (draft.from > draft.to) {
      setRangeError('Ngày bắt đầu phải trước hoặc bằng ngày kết thúc');
      return;
    }
    applyRange(draft);
  };

  const activePreset = PRESETS.find((n) => {
    const preset = lastDays(n);
    return preset.from === range.from && preset.to === range.to;
  });

  return (
    <div className="dashboard">
      <div className="page-header">
        <h1>Bảng điều khiển</h1>
      </div>
      <form className="dash-range" onSubmit={handleSubmit} aria-label="Khoảng thời gian">
        <div className="dash-presets" role="group" aria-label="Chọn nhanh">
          {PRESETS.map((n) => (
            <button
              key={n}
              type="button"
              className={`btn btn-ghost${activePreset === n ? ' is-active' : ''}`}
              aria-pressed={activePreset === n}
              onClick={() => applyRange(lastDays(n))}
            >
              {n} ngày
            </button>
          ))}
        </div>
        <label>
          Từ ngày
          <input
            type="date"
            value={draft.from}
            onChange={(event) => setDraft({ ...draft, from: event.target.value })}
          />
        </label>
        <label>
          Đến ngày
          <input
            type="date"
            value={draft.to}
            onChange={(event) => setDraft({ ...draft, to: event.target.value })}
          />
        </label>
        <button type="submit" className="btn btn-primary">
          Xem
        </button>
      </form>
      {rangeError && (
        <p className="alert alert-error" role="alert">
          {rangeError}
        </p>
      )}
      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được bảng điều khiển')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!result && loading && <p className="muted">Đang tải số liệu...</p>}
      {result && <DashboardContent result={result} />}
    </div>
  );
}

function DashboardContent({ result }) {
  const { summary } = result;
  return (
    <>
      <p className="muted small">
        Từ {formatDay(result.from)} đến {formatDay(result.to)} ({result.days.length} ngày, giờ Việt
        Nam). Doanh thu chỉ tính đơn đã giao, theo ngày đặt, gồm phí vận chuyển và đã trừ giảm giá.
      </p>
      <dl className="stat-tiles">
        <StatTile
          label="Doanh thu"
          value={formatPrice(summary.revenue)}
          note={`${formatNumber(summary.deliveredOrders)} đơn đã giao`}
        />
        <StatTile
          label="Giá trị đơn trung bình"
          value={summary.averageOrderValue == null ? '–' : formatPrice(summary.averageOrderValue)}
          note="Doanh thu chia số đơn đã giao"
        />
        <StatTile
          label="Đơn đặt"
          value={formatNumber(summary.orders)}
          note="Mọi trạng thái, kể cả đã hủy"
        />
        <StatTile
          label="Khách hàng mới"
          value={formatNumber(summary.newCustomers)}
          note="Tài khoản đăng ký trong khoảng"
        />
      </dl>
      <section className="checkout-section">
        <h2>Doanh thu theo ngày</h2>
        <RevenueChart days={result.days} />
      </section>
      <div className="profile-panels dash-panels dash-orders">
        <section className="checkout-section">
          <h2>Đơn hàng theo trạng thái</h2>
          <StatusBars rows={result.ordersByStatus} total={summary.orders} />
        </section>
        <section className="checkout-section">
          <h2>Sản phẩm bán chạy</h2>
          <TopProducts products={result.topProducts} />
        </section>
      </div>
      <RecommendationSection
        recommendations={result.recommendations}
        summary={summary}
        abTest={result.abTest}
      />
    </>
  );
}

/** Cột doanh thu mỗi ngày (một màu, đáy chung), rê chuột hoặc Tab vào cột để xem số. */
function RevenueChart({ days }) {
  const [active, setActive] = useState(null);
  const top = niceCeil(Math.max(0, ...days.map((d) => d.revenue)));
  if (top === 0) {
    return <p className="muted">Chưa có đơn đã giao trong khoảng này.</p>;
  }
  const focusable = days.length <= FOCUSABLE_BARS;
  const hovered = active == null ? null : days[active];
  const position = active == null ? 0 : (active + 0.5) / days.length;
  let align = '';
  if (position < 0.2) align = ' is-start';
  else if (position > 0.8) align = ' is-end';
  const middle = days[Math.floor((days.length - 1) / 2)];

  return (
    <figure className="dash-chart">
      <div className="dash-chart-body">
        <div className="dash-chart-axis" aria-hidden="true">
          <span>{shortMoney(top)}</span>
          <span>{shortMoney(top / 2)}</span>
          <span>0</span>
        </div>
        <div className="dash-chart-plot">
          <span className="dash-grid" style={{ top: 0 }} aria-hidden="true" />
          <span className="dash-grid" style={{ top: '50%' }} aria-hidden="true" />
          <ol
            className={`dash-bars${days.length > 45 ? ' is-dense' : ''}`}
            aria-label="Doanh thu theo ngày"
            onMouseLeave={() => setActive(null)}
          >
            {days.map((day, index) => (
              <li
                key={day.date}
                className={index === active ? 'is-active' : undefined}
                tabIndex={focusable ? 0 : undefined}
                aria-label={`${formatDay(day.date)}: ${formatPrice(day.revenue)}`}
                onMouseEnter={() => setActive(index)}
                onFocus={() => setActive(index)}
                onBlur={() => setActive(null)}
              >
                <span className="dash-bar" style={{ height: `${(day.revenue / top) * 100}%` }} />
              </li>
            ))}
          </ol>
          {hovered && (
            <div
              className={`dash-tooltip${align}`}
              style={{ left: `${position * 100}%` }}
              aria-hidden="true"
            >
              <strong>{formatDay(hovered.date)}</strong>
              <span>{formatPrice(hovered.revenue)}</span>
              <span className="muted">
                {formatNumber(hovered.deliveredOrders)} đơn đã giao · {formatNumber(hovered.orders)}{' '}
                đơn đặt
              </span>
            </div>
          )}
        </div>
        <span aria-hidden="true" />
        <div className="dash-chart-x" aria-hidden="true">
          <span>{formatDay(days[0].date, false)}</span>
          {days.length > 2 && <span>{formatDay(middle.date, false)}</span>}
          <span>{formatDay(days[days.length - 1].date, false)}</span>
        </div>
      </div>
      <details className="dash-table-view">
        <summary>Xem dạng bảng</summary>
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Ngày</th>
                <th>Doanh thu</th>
                <th>Đơn đã giao</th>
                <th>Đơn đặt</th>
              </tr>
            </thead>
            <tbody>
              {days.map((day) => (
                <tr key={day.date}>
                  <td>{formatDay(day.date)}</td>
                  <td>{formatPrice(day.revenue)}</td>
                  <td>{formatNumber(day.deliveredOrders)}</td>
                  <td>{formatNumber(day.orders)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}

function StatusBars({ rows, total }) {
  if (total === 0) return <p className="muted">Chưa có đơn đặt trong khoảng này.</p>;
  return (
    <ShareBars
      label="Đơn hàng theo trạng thái"
      rows={rows.map((row) => ({
        key: row.status,
        label: ORDER_STATUS_LABELS[row.status] ?? row.status,
        count: row.count,
        unit: 'đơn',
        share: (row.count / total) * 100,
      }))}
    />
  );
}

function TopProducts({ products }) {
  if (products.length === 0) return <p className="muted">Chưa có đơn đã giao trong khoảng này.</p>;
  return (
    <div className="table-scroll">
      <table className="table dash-top-table">
        <thead>
          <tr>
            <th>#</th>
            <th>Sản phẩm</th>
            <th>Đã bán</th>
            <th>Doanh thu</th>
          </tr>
        </thead>
        <tbody>
          {products.map((product, index) => (
            <tr key={product.productId}>
              <td>{index + 1}</td>
              <td>
                <Link to={`/admin/products/${product.productId}`} title={product.name}>
                  {product.name}
                </Link>
              </td>
              <td>{formatNumber(product.quantity)}</td>
              <td>{formatPrice(product.revenue)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted small">Theo số lượng trong đơn đã giao, tiền hàng chưa trừ giảm giá.</p>
    </div>
  );
}

function RecommendationSection({ recommendations, summary, abTest }) {
  const { total } = recommendations;
  return (
    <section className="checkout-section dash-recommendations">
      <div className="section-header">
        <h2>Hiệu quả gợi ý</h2>
        <Link to="/admin/recommendations">Xem chi tiết</Link>
      </div>
      <dl className="stat-tiles">
        <StatTile label="Lượt hiện gợi ý" value={formatNumber(total.shown)} />
        <StatTile label="Lượt bấm" value={formatNumber(total.clicked)} />
        <StatTile label="Tỷ lệ bấm" value={percent(total.ctr)} note="Lượt bấm chia lượt hiện" />
        <StatTile
          label="Doanh thu từ gợi ý"
          value={formatPrice(summary.recommendedRevenue)}
          note={
            summary.recommendedShare == null
              ? 'Chưa có tiền hàng đã giao'
              : `${percent(summary.recommendedShare)} tiền hàng đã giao`
          }
        />
      </dl>
      <p className="muted small">
        Doanh thu từ gợi ý: tiền hàng của sản phẩm khách đã bấm trong một khối gợi ý trong 7 ngày
        trước khi đặt đơn (chỉ đơn đã giao).
      </p>
      {total.shown === 0 ? (
        <p className="muted">Chưa có lượt hiện gợi ý nào trong khoảng này.</p>
      ) : (
        <div className="profile-panels dash-panels">
          <CtrTable
            title="Theo vị trí"
            column="Vị trí"
            rows={recommendations.byPlacement}
            labelOf={(row) => PLACEMENT_LABELS[row.placement] ?? row.placement}
          />
          <CtrTable
            title="Theo nguồn"
            column="Nguồn"
            rows={recommendations.bySource}
            labelOf={(row) => SOURCE_LABELS[row.source] ?? row.source}
          />
        </div>
      )}
      <AbTestCard abTest={abTest} />
    </section>
  );
}

function CtrTable({ title, column, rows, labelOf }) {
  return (
    <div className="table-scroll">
      <table className="table dash-ctr-table">
        <caption>{title}</caption>
        <thead>
          <tr>
            <th>{column}</th>
            <th>Hiện</th>
            <th>Bấm</th>
            <th>Tỷ lệ</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={labelOf(row)}>
              <td>{labelOf(row)}</td>
              <td>{formatNumber(row.shown)}</td>
              <td>{formatNumber(row.clicked)}</td>
              <td>{percent(row.ctr)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function AbTestCard({ abTest }) {
  if (!abTest) {
    return (
      <p className="dash-ab muted">
        Không có thử nghiệm A/B nào đang chạy.{' '}
        <Link to="/admin/recommendations">Bắt đầu thử nghiệm</Link>
      </p>
    );
  }
  return (
    <div className="dash-ab">
      <p>
        <strong>Thử nghiệm A/B đang chạy: {abTest.name}</strong>{' '}
        <span className="muted small">từ {formatDateTime(abTest.startedAt)}</span>
      </p>
      <ul>
        <li>
          Nhóm gợi ý học máy: {formatNumber(abTest.treatmentUsers)} khách,{' '}
          {percent(abTest.treatmentClickRate)} khách bấm gợi ý
        </li>
        <li>
          Nhóm bán chạy: {formatNumber(abTest.controlUsers)} khách,{' '}
          {percent(abTest.controlClickRate)} khách bấm gợi ý
        </li>
      </ul>
      <Link to="/admin/recommendations">Xem kết quả và kiểm định</Link>
    </div>
  );
}

export default DashboardPage;
