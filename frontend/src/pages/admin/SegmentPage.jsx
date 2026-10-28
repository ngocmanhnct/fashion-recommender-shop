import { Link } from 'react-router';
import SegmentBadge from '../../components/SegmentBadge';
import { useApiData } from '../../hooks/useApiData';
import { getSegments, UNSEGMENTED } from '../../services/customerAdminApi';
import { getErrorMessage } from '../../services/errorMessage';
import { formatNumber, formatPrice } from '../../services/format';
import { formatDateTime } from '../../services/orderLabels';

const DECIMAL = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 });
const SILHOUETTE = new Intl.NumberFormat('vi-VN', {
  minimumFractionDigits: 3,
  maximumFractionDigits: 3,
});

/**
 * Trang quản trị /admin/segments (US-19): các nhóm khách hàng do K-Means trên R, F, M tạo ra,
 * số khách và số liệu trung bình của từng nhóm, cùng thông tin lần huấn luyện để giải thích kết quả.
 */
function SegmentPage() {
  const { data, error, loading, reload } = useApiData('segments', getSegments);

  if (loading) return <p className="muted">Đang tải kết quả phân nhóm...</p>;
  if (error) {
    return (
      <div className="alert alert-error">
        <p>{getErrorMessage(error, 'Không tải được kết quả phân nhóm')}</p>
        <button type="button" className="btn btn-ghost" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }

  const { model, segments, segmentedCount, unsegmentedCount } = data;

  return (
    <div>
      <div className="page-header">
        <h1>Phân nhóm khách hàng</h1>
        <Link to="/admin/customers" className="btn btn-ghost">
          Danh sách khách hàng
        </Link>
      </div>

      {model ? (
        <p className="muted small">
          Huấn luyện lúc {formatDateTime(model.trainedAt)} trên {formatNumber(model.customerCount)}{' '}
          khách · {model.clusterCount} nhóm (điểm gãy Elbow ở {model.elbowClusterCount} nhóm) ·
          Silhouette {SILHOUETTE.format(model.silhouette)} · {formatNumber(segmentedCount)} khách đã
          phân nhóm
        </p>
      ) : (
        <div className="alert alert-warning">
          <p>
            Chưa có mô hình phân nhóm. Mô hình cần ít nhất 20 khách có đơn Đã giao; khi đủ, hệ thống
            tự huấn luyện ở lần chạy đêm (02:00) hoặc ngay khi có đơn chuyển sang Đã giao.
          </p>
        </div>
      )}

      <div className="segment-cards">
        {segments.map((s) => (
          <article key={s.code} className="segment-card">
            <div className="segment-card-head">
              <SegmentBadge segment={s} />
              <span className="muted small">{DECIMAL.format(s.share)}%</span>
            </div>
            <p className="segment-count">{formatNumber(s.customerCount)} khách</p>
            <p className="muted small">Trung bình mỗi khách trong nhóm:</p>
            <div className="rfm-grid">
              <div className="rfm-cell">
                <span className="muted small">R · gần nhất</span>
                <strong>{DECIMAL.format(s.avgRecency)} ngày</strong>
              </div>
              <div className="rfm-cell">
                <span className="muted small">F · số đơn</span>
                <strong>{DECIMAL.format(s.avgFrequency)} đơn</strong>
              </div>
              <div className="rfm-cell">
                <span className="muted small">M · chi tiêu</span>
                <strong>{formatPrice(s.avgMonetary)}</strong>
              </div>
            </div>
            {s.description && <p className="small">{s.description}</p>}
            <Link to={`/admin/customers?segment=${s.code}`} className="small">
              Xem khách trong nhóm ›
            </Link>
          </article>
        ))}

        <article className="segment-card">
          <div className="segment-card-head">
            <SegmentBadge segment={null} />
          </div>
          <p className="segment-count">{formatNumber(unsegmentedCount)} khách</p>
          <p className="small">
            Khách chưa có đơn Đã giao nào không được đưa vào phân cụm, để không kéo lệch tâm các
            nhóm (BR-24).
          </p>
          <Link to={`/admin/customers?segment=${UNSEGMENTED}`} className="small">
            Xem danh sách ›
          </Link>
        </article>
      </div>

      <section className="checkout-section segment-explain">
        <h2>Cách hệ thống phân nhóm</h2>
        <ol>
          <li>
            Với mỗi khách có đơn Đã giao, tính 3 chỉ số: <strong>R</strong> (số ngày từ đơn Đã giao
            gần nhất), <strong>F</strong> (số đơn Đã giao), <strong>M</strong> (tổng tiền các đơn Đã
            giao).
          </li>
          <li>
            Lấy ln(1 + x) rồi chuẩn hóa từng chỉ số về trung bình 0, độ lệch chuẩn 1, để vài khách
            chi tiêu rất lớn không lấn át và 3 chỉ số có trọng lượng ngang nhau.
          </li>
          <li>
            Chạy K-Means với K từ 3 đến 6; chọn K theo điểm gãy Elbow và chỉ số Silhouette (càng gần
            1 các nhóm càng tách bạch, mục tiêu từ 0,3).
          </li>
          <li>
            Đặt tên nhóm bằng cách ghép tâm mỗi nhóm với kiểu khách mẫu gần nhất (VIP, thân thiết,
            tiết kiệm, mới, nguy cơ rời bỏ, lâu không mua).
          </li>
          <li>
            Mô hình huấn luyện lại hằng tuần; giữa 2 lần, khách có đơn mới Đã giao được xếp ngay vào
            nhóm có tâm gần nhất (BR-23).
          </li>
        </ol>
      </section>
    </div>
  );
}

export default SegmentPage;
