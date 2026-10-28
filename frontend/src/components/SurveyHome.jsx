import { Link } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { useSurveyState } from '../hooks/useSurveyState';
import { evaluateSurvey } from '../services/surveyApi';
import SurveyProfile from './SurveyProfile';

/**
 * Phần khảo sát ở trang chủ (US-23): chưa làm thì mời làm (có nút bỏ qua, không hỏi lại), đã làm
 * thì hiện khối "Gợi ý cho bạn" theo câu trả lời. Quản trị viên không thấy gì.
 */
function SurveyHome() {
  const { status, answers, loading, skip } = useSurveyState();
  if (loading || status === null) return null;
  if (status === 'NONE') return <SurveyInvite onSkip={skip} />;
  if (status === 'COMPLETED' && answers.length > 0) return <ForYou answers={answers} />;
  return null;
}

function SurveyInvite({ onSkip }) {
  return (
    <section className="survey-invite">
      <div>
        <h2>Bạn đang tìm gì?</h2>
        <p className="muted">
          Trả lời vài câu hỏi ngắn (khoảng 1 phút) để nhận gợi ý sản phẩm hợp với bạn.
        </p>
      </div>
      <div className="survey-actions">
        <Link to="/survey" className="btn btn-primary">
          Làm khảo sát
        </Link>
        <button
          type="button"
          className="btn btn-ghost"
          onClick={() => {
            onSkip().catch(() => {
              /* Không ghi nhận được thì lần sau vẫn mời làm khảo sát. */
            });
          }}
        >
          Bỏ qua
        </button>
      </div>
    </section>
  );
}

function ForYou({ answers }) {
  const { data, error, loading } = useApiData(JSON.stringify(answers), (key) =>
    evaluateSurvey(JSON.parse(key))
  );
  // Câu trả lời cũ không còn hợp lệ (cây câu hỏi đã đổi) thì mời làm lại thay vì báo lỗi.
  if (error) {
    return (
      <section className="product-section">
        <p className="muted">
          Khảo sát đã thay đổi. <Link to="/survey">Làm lại khảo sát</Link> để nhận gợi ý mới.
        </p>
      </section>
    );
  }
  return (
    <section className="product-section">
      <div className="section-header">
        <h2>Gợi ý cho bạn</h2>
        <Link to="/survey">Làm lại khảo sát ›</Link>
      </div>
      {loading ? (
        <p className="muted">Đang tải...</p>
      ) : (
        <SurveyProfile evaluation={data} heading="h3" placement="HOME" />
      )}
    </section>
  );
}

export default SurveyHome;
