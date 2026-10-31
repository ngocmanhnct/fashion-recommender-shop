import { useState } from 'react';
import { Link, useParams } from 'react-router';
import ShareBars from '../../components/ShareBars';
import StatTile from '../../components/StatTile';
import VoucherForm from '../../components/VoucherForm';
import { useApiData } from '../../hooks/useApiData';
import {
  createCampaign,
  getCampaigns,
  getSegmentProfile,
  stopCampaign,
} from '../../services/customerAdminApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber, formatPrice } from '../../services/format';
import { formatDateTime } from '../../services/orderLabels';
import { autoVoucherForm, suggestedVoucherForm } from '../../services/voucherForm';
import { describeDiscount, VOUCHER_STATUS_LABELS } from '../../services/voucherLabels';

const DECIMAL = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const SEGMENT_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,29}$/;

const GENDER_LABELS = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác', UNKNOWN: 'Chưa khai' };
const AGE_LABELS = {
  UNDER_18: 'Dưới 18',
  '18_24': '18–24',
  '25_34': '25–34',
  '35_44': '35–44',
  '45_54': '45–54',
  '55_PLUS': 'Từ 55',
  UNKNOWN: 'Chưa khai',
};

const TARGET_LABELS = {
  ALL: 'Cả nhóm',
  CARE: 'Khách cần chăm sóc',
  AUTO: 'Khách mới vào nhóm (tự động)',
};

/**
 * Trang quản trị /admin/segments/:code (US-25): hồ sơ 1 nhóm khách hàng. Tóm tắt nhóm bằng doanh
 * thu, giá trị đơn, nhịp mua, thời gian gắn bó, danh mục mua nhiều, giới tính và độ tuổi; cuối
 * trang là khách cần chăm sóc, tức khách đã lâu không mua so với nhịp mua của chính họ (BR-43).
 * Đầu trang là việc nên làm với nhóm (US-26): mục tiêu, ưu đãi gợi ý theo thói quen của nhóm và
 * nút tặng mã riêng cho cả nhóm hoặc cho khách cần chăm sóc; cuối trang là kết quả các lần tặng.
 */
function SegmentDetailPage() {
  const { code } = useParams();
  if (!SEGMENT_CODE_PATTERN.test(code)) return <SegmentNotFound />;
  return <SegmentProfile key={code} code={code} />;
}

function SegmentProfile({ code }) {
  const { data: profile, error, loading, reload } = useApiData(code, getSegmentProfile);
  const campaigns = useApiData(code, getCampaigns);

  if (loading) return <p className="muted">Đang tải hồ sơ nhóm...</p>;
  if (error) {
    if (error.response?.status === 404) return <SegmentNotFound />;
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được hồ sơ nhóm')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="segment-profile">
      <p className="small">
        <Link to="/admin/segments">‹ Các nhóm khách hàng</Link>
      </p>
      <div className="page-header">
        <div>
          <p className="eyebrow">Hồ sơ nhóm</p>
          <h1>{profile.name}</h1>
        </div>
        <Link to={`/admin/customers?segment=${profile.code}`} className="btn btn-ghost">
          Xem khách trong nhóm
        </Link>
      </div>
      {profile.description && <p className="muted">{profile.description}</p>}

      <NextAction profile={profile} campaigns={campaigns} />

      <h2 className="visually-hidden">Số liệu chính</h2>
      <dl className="stat-tiles">
        <StatTile
          label="Khách hàng"
          value={formatNumber(profile.customerCount)}
          note={`${DECIMAL.format(profile.customerShare)}% khách đã phân nhóm`}
        />
        <StatTile
          label="Doanh thu"
          value={formatPrice(profile.revenue)}
          note={`${DECIMAL.format(profile.revenueShare)}% doanh thu các nhóm`}
        />
        <StatTile
          label="Giá trị đơn trung bình"
          value={formatPrice(profile.avgOrderValue)}
          note={`${formatNumber(profile.deliveredOrders)} đơn Đã giao`}
        />
        <StatTile
          label="Nhịp mua"
          value={
            profile.avgPurchaseIntervalDays == null
              ? 'Chưa có'
              : `${DECIMAL.format(profile.avgPurchaseIntervalDays)} ngày`
          }
          note={
            profile.repeatCustomers > 0
              ? `Giữa 2 đơn liên tiếp, tính trên ${formatNumber(profile.repeatCustomers)} khách mua từ 2 đơn`
              : 'Chưa khách nào mua từ 2 đơn'
          }
        />
        <StatTile
          label="Gắn bó trung bình"
          value={`${DECIMAL.format(profile.avgTenureDays)} ngày`}
          note="Kể từ đơn Đã giao đầu tiên"
        />
      </dl>

      <div className="profile-panels">
        <section className="checkout-section">
          <h2>Danh mục mua nhiều</h2>
          {profile.topCategories.length === 0 ? (
            <p className="muted small">Khách trong nhóm chưa mua sản phẩm nào.</p>
          ) : (
            <ShareBars
              label="Danh mục mua nhiều"
              rows={profile.topCategories.map((c) => ({
                key: c.name,
                label: c.name,
                count: c.quantity,
                share: c.share,
                unit: 'sản phẩm',
              }))}
            />
          )}
        </section>
        <section className="checkout-section">
          <h2>Giới tính</h2>
          <ShareBars
            label="Giới tính"
            rows={profile.genders.map((g) => ({
              key: g.key,
              label: GENDER_LABELS[g.key] ?? g.key,
              count: g.count,
              share: g.share,
              unit: 'khách',
            }))}
          />
        </section>
        <section className="checkout-section">
          <h2>Độ tuổi</h2>
          <ShareBars
            label="Độ tuổi"
            rows={profile.ageGroups.map((a) => ({
              key: a.key,
              label: AGE_LABELS[a.key] ?? a.key,
              count: a.count,
              share: a.share,
              unit: 'khách',
            }))}
          />
        </section>
      </div>

      <CareList profile={profile} />
      <CampaignResults campaigns={campaigns} />
    </div>
  );
}

/**
 * Việc nên làm với nhóm (US-26): mục tiêu và ưu đãi gợi ý; bấm nút tặng mở biểu mẫu mã điền sẵn
 * ưu đãi đó, admin sửa được trước khi tặng. Nút của đối tượng được gợi ý là nút chính.
 */
function NextAction({ profile, campaigns }) {
  const { suggestion, customerCount, careCount } = profile;
  const [target, setTarget] = useState(null);
  const [message, setMessage] = useState('');
  const [autoDays, setAutoDays] = useState(String(suggestion.validDays));
  const [stopError, setStopError] = useState('');
  const recipients = target === 'CARE' ? careCount : customerCount;
  const latest = campaigns.data?.find((c) => c.target !== 'AUTO');
  const running = campaigns.data?.find((c) => c.target === 'AUTO' && c.autoEnabled);

  const open = (next) => {
    setMessage('');
    setStopError('');
    setTarget(next);
  };

  const handleSubmit = async (voucher) => {
    const auto = target === 'AUTO';
    const campaign = await createCampaign(profile.code, {
      target,
      voucher,
      validDays: auto ? Number(autoDays) : undefined,
    });
    setTarget(null);
    setMessage(
      auto
        ? `Đã bật tự tặng mã ${campaign.voucher.code}: khách vào nhóm từ bây giờ nhận mã trong ` +
            'vòng 1 phút, thấy ở mục "Ưu đãi của bạn" và khi thanh toán.'
        : `Đã tặng mã ${campaign.voucher.code} cho ${formatNumber(campaign.recipientCount)} ` +
            'khách. Khách thấy mã ở mục "Ưu đãi của bạn" và khi thanh toán.'
    );
    campaigns.reload();
  };

  const handleStop = async () => {
    if (!window.confirm(`Dừng tự tặng mã ${running.voucher.code}? Khách đã nhận vẫn dùng được.`)) {
      return;
    }
    setMessage('');
    setStopError('');
    try {
      await stopCampaign(running.id);
      setMessage(`Đã dừng tự tặng mã ${running.voucher.code}`);
      campaigns.reload();
    } catch (err) {
      setStopError(getErrorMessage(err, 'Không dừng được chương trình'));
    }
  };

  const giftButton = (value, label, count) => (
    <button
      type="button"
      className={suggestion.target === value ? 'btn btn-primary' : 'btn btn-ghost'}
      disabled={count === 0 || target !== null}
      onClick={() => open(value)}
    >
      {label} ({formatNumber(count)})
    </button>
  );

  return (
    <section className="checkout-section next-action" aria-labelledby="next-action-title">
      <p className="eyebrow">Việc nên làm với nhóm này</p>
      <h2 id="next-action-title">{suggestion.goal}</h2>
      <p className="small">{suggestion.reason}</p>
      <p className="next-action-offer">
        Ưu đãi gợi ý: <strong>{describeDiscount(suggestion)}</strong>
        {suggestion.minOrderAmount > 0 && <> cho đơn từ {formatPrice(suggestion.minOrderAmount)}</>}
        , dùng trong {suggestion.validDays} ngày, gửi cho{' '}
        {suggestion.target === 'CARE' ? 'khách cần chăm sóc' : 'cả nhóm'}.
      </p>
      <div className="next-action-buttons">
        {giftButton('CARE', 'Tặng mã cho khách cần chăm sóc', careCount)}
        {giftButton('ALL', 'Tặng mã cho cả nhóm', customerCount)}
      </div>
      <div className="next-action-auto">
        <h3>Tự tặng cho khách mới vào nhóm</h3>
        {running ? (
          <>
            <p className="small">
              <span className="status status-delivered">Đang chạy</span> Mã{' '}
              <strong className="voucher-code">{running.voucher.code}</strong> (
              {describeDiscount(running.voucher)}), mỗi khách dùng trong {running.validDays} ngày kể
              từ khi nhận. Đã tặng {formatNumber(running.recipientCount)} khách, hết chương trình
              lúc {formatDateTime(running.voucher.validUntil)}.
            </p>
            <button type="button" className="btn btn-ghost" onClick={handleStop}>
              Dừng tự tặng
            </button>
          </>
        ) : (
          <>
            <p className="muted small">
              Khách vừa được xếp vào nhóm (sau đơn Đã giao hoặc lần xếp nhóm mỗi đêm) tự nhận mã
              riêng, không cần admin bấm mỗi lần. Khách đang ở trong nhóm lúc bật không nhận.
            </p>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={target !== null}
              onClick={() => open('AUTO')}
            >
              Bật tự tặng mã
            </button>
          </>
        )}
        {stopError && (
          <p className="alert alert-error" role="alert">
            {stopError}
          </p>
        )}
      </div>
      {latest && target === null && (
        <p className="muted small">
          Lần tặng gần nhất: {formatDateTime(latest.createdAt)}, {formatNumber(latest.buyers)}/
          {formatNumber(latest.recipientCount)} khách đã quay lại mua.{' '}
          <a href="#campaign-results">Xem kết quả</a>
        </p>
      )}
      {message && (
        <p className="alert alert-success" role="status">
          {message}
        </p>
      )}
      {target === 'AUTO' && (
        <VoucherForm
          key="AUTO"
          title="Bật tự tặng mã cho khách mới vào nhóm"
          submitLabel="Bật tự tặng"
          initialForm={autoVoucherForm(profile.code, suggestion)}
          onSubmit={handleSubmit}
          onCancel={() => setTarget(null)}
        >
          <p className="muted small">
            Thời hạn của mã là thời gian chạy chương trình. Mỗi khách nhận mã khi vào nhóm và dùng
            trong số ngày dưới đây kể từ lúc nhận (không quá hạn của mã).
          </p>
          <div className="field">
            <label className="field-label" htmlFor="auto-valid-days">
              Mỗi khách dùng trong (ngày)
            </label>
            <input
              id="auto-valid-days"
              type="number"
              min={1}
              max={90}
              required
              value={autoDays}
              onChange={(event) => setAutoDays(event.target.value)}
            />
          </div>
        </VoucherForm>
      )}
      {(target === 'ALL' || target === 'CARE') && (
        <VoucherForm
          key={target}
          title={
            target === 'CARE'
              ? `Tặng mã cho ${formatNumber(recipients)} khách cần chăm sóc`
              : `Tặng mã cho cả nhóm (${formatNumber(recipients)} khách)`
          }
          submitLabel="Tặng mã"
          initialForm={suggestedVoucherForm(profile.code, suggestion)}
          onSubmit={handleSubmit}
          onCancel={() => setTarget(null)}
        >
          <p className="muted small">
            Đã điền sẵn ưu đãi gợi ý, sửa được trước khi tặng. Mã chỉ dành cho những khách này
            (khách khác nhập mã sẽ bị từ chối); khách đang trong nhóm lúc tặng mới nhận được.
          </p>
        </VoucherForm>
      )}
    </section>
  );
}

/** Kết quả các lần tặng mã cho nhóm: bao nhiêu khách quay lại mua, doanh thu so với tiền giảm. */
function CampaignResults({ campaigns }) {
  const { data, error, loading, reload } = campaigns;
  return (
    <section id="campaign-results" className="checkout-section">
      <h2>Kết quả các lần tặng mã</h2>
      <p className="muted small">
        Tính trên các đơn chưa hủy có dùng mã. "Quay lại mua" là số khách được tặng đã đặt đơn với
        mã; doanh thu là tổng tiền các đơn đó (sau giảm).
      </p>
      {loading && <p className="muted">Đang tải...</p>}
      {error && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được kết quả tặng mã')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {data?.length === 0 && <p className="muted">Chưa tặng mã nào cho nhóm này.</p>}
      {data?.length > 0 && (
        <div className="table-scroll">
          <table className="table">
            <thead>
              <tr>
                <th>Ngày tặng</th>
                <th>Mã</th>
                <th>Đã tặng</th>
                <th>Quay lại mua</th>
                <th>Doanh thu</th>
                <th>Đã giảm</th>
                <th>Doanh thu / 1đ giảm</th>
              </tr>
            </thead>
            <tbody>
              {data.map((c) => (
                <tr key={c.id}>
                  <td className="small">{formatDateTime(c.createdAt)}</td>
                  <td>
                    <strong className="voucher-code">{c.voucher.code}</strong>
                    <div className="small">{describeDiscount(c.voucher)}</div>
                    <div className="muted small">
                      {VOUCHER_STATUS_LABELS[c.voucher.status] ?? c.voucher.status}
                    </div>
                  </td>
                  <td>
                    {formatNumber(c.recipientCount)} khách
                    <div className="muted small">{TARGET_LABELS[c.target] ?? c.target}</div>
                    {c.target === 'AUTO' && (
                      <div className="muted small">
                        {c.autoEnabled ? 'Đang chạy' : 'Đã dừng'}, mỗi khách {c.validDays} ngày
                      </div>
                    )}
                  </td>
                  <td>
                    <strong>{formatNumber(c.buyers)} khách</strong>
                    <div className="muted small">{DECIMAL.format(c.conversionRate)}%</div>
                  </td>
                  <td>
                    {formatPrice(c.revenue)}
                    <div className="muted small">{formatNumber(c.orders)} đơn</div>
                  </td>
                  <td>{formatPrice(c.discount)}</td>
                  <td>
                    {c.revenuePerDiscount == null ? (
                      <span className="muted">Chưa có</span>
                    ) : (
                      `${DECIMAL.format(c.revenuePerDiscount)}đ`
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

/** Khách cần chăm sóc (BR-43): mua từ 2 đơn, đã quá 1,5 lần nhịp mua riêng và quá 30 ngày. */
function CareList({ profile }) {
  const { careCount, careList } = profile;
  return (
    <section className="checkout-section care-list">
      <h2>Khách cần chăm sóc ({formatNumber(careCount)})</h2>
      <p className="muted small">
        Khách mua từ 2 đơn trở lên nhưng đã lâu chưa mua lại: số ngày chưa mua vượt 1,5 lần nhịp mua
        của chính khách đó và quá 30 ngày. Quá nhịp càng lâu càng đứng trước.
        {careCount > careList.length &&
          ` Đang hiện ${formatNumber(careList.length)} khách đầu tiên.`}
      </p>
      {careList.length === 0 ? (
        <p className="muted">Không có khách nào quá nhịp mua.</p>
      ) : (
        <table className="table">
          <thead>
            <tr>
              <th>Khách hàng</th>
              <th>Số đơn</th>
              <th>Đơn gần nhất</th>
              <th>Nhịp mua</th>
              <th>Chưa mua</th>
            </tr>
          </thead>
          <tbody>
            {careList.map((c) => (
              <tr key={c.accountId}>
                <td>
                  <Link to={`/admin/customers/${c.accountId}`}>{c.fullName}</Link>
                  <div className="muted small">{c.email}</div>
                </td>
                <td>{formatNumber(c.orders)}</td>
                <td>{formatDateTime(c.lastOrderAt)}</td>
                <td>{DECIMAL.format(c.purchaseIntervalDays)} ngày</td>
                <td>
                  <strong>{formatNumber(c.daysSinceLastOrder)} ngày</strong>
                  <div className="muted small">
                    gấp {DECIMAL.format(c.daysSinceLastOrder / Math.max(c.purchaseIntervalDays, 1))}{' '}
                    lần nhịp
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function SegmentNotFound() {
  return (
    <div>
      <h1>Không tìm thấy nhóm khách hàng</h1>
      <p className="muted">
        Nhóm này không có trong lần phân nhóm gần nhất (mô hình có thể vừa huấn luyện lại).
      </p>
      <Link to="/admin/segments" className="btn btn-ghost">
        Các nhóm khách hàng
      </Link>
    </div>
  );
}

export default SegmentDetailPage;
