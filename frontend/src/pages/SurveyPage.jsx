import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import SurveyProfile from '../components/SurveyProfile';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { useSurveyState } from '../hooks/useSurveyState';
import { getErrorMessage } from '../services/errorMessage';
import * as surveyApi from '../services/surveyApi';
import { readLocalSurvey, writeLocalSurvey } from '../services/surveyStorage';

const NO_BRAND = 'NO_BRAND';

/** Câu tiếp theo sau khi chọn: câu chọn thương hiệu đi theo câu hỏi, còn lại theo đáp án đầu. */
function nextCodeOf(question, selected) {
  if (question.answerType === 'BRAND') return question.nextQuestionCode;
  const first = question.options.find((option) => option.code === selected[0]);
  return first?.nextQuestionCode ?? null;
}

/** Số câu nhiều nhất còn phải trả lời tính từ câu code (kể cả câu đó), để vẽ thanh tiến độ. */
function remainingDepth(byCode, code, seen = new Set()) {
  const question = byCode.get(code);
  if (!question || seen.has(code)) return 0;
  seen.add(code);
  const nextCodes = [question.nextQuestionCode, ...question.options.map((o) => o.nextQuestionCode)];
  const depths = nextCodes.filter(Boolean).map((next) => remainingDepth(byCode, next, seen));
  seen.delete(code);
  return 1 + Math.max(0, ...depths);
}

function toAnswer(question, selected) {
  if (question.answerType !== 'BRAND') {
    return { questionCode: question.code, optionCodes: selected };
  }
  const brandId = selected[0] === NO_BRAND ? null : Number(selected[0]);
  return { questionCode: question.code, brandId };
}

function selectionOf(answer) {
  if (answer.optionCodes) return answer.optionCodes;
  return [answer.brandId == null ? NO_BRAND : String(answer.brandId)];
}

/**
 * Khảo sát lần đầu (US-23, BR-37, BR-38): cây câu hỏi rẽ nhánh lấy từ backend, mỗi lần 1 câu, có
 * thanh tiến độ, nút quay lại và bỏ qua. Làm xong thì hiện kiểu người mua và sản phẩm gợi ý.
 * - Khách hàng: kết quả lưu vào tài khoản (trừ khi tìm quà tặng).
 * - Chưa đăng nhập: kết quả lưu ở trình duyệt, gửi lên tài khoản ngay sau khi đăng nhập.
 * - Quản trị viên: chỉ xem thử, không lưu.
 */
function SurveyPage() {
  const { data, error, loading, reload } = useApiData('survey', surveyApi.getSurvey);

  if (loading) return <p className="muted">Đang tải khảo sát...</p>;
  if (error) {
    return (
      <div className="survey-card">
        <p className="alert alert-error">{getErrorMessage(error)}</p>
        <button type="button" className="btn" onClick={reload}>
          Thử lại
        </button>
      </div>
    );
  }
  return <SurveyRunner survey={data} />;
}

function SurveyRunner({ survey }) {
  const { user } = useAuth();
  const { skip } = useSurveyState();
  const navigate = useNavigate();
  const byCode = new Map(survey.questions.map((question) => [question.code, question]));
  const [answers, setAnswers] = useState([]);
  const [currentCode, setCurrentCode] = useState(survey.startQuestionCode);
  const [selected, setSelected] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  if (result) return <SurveyDone result={result} onRestart={restart} />;

  const question = byCode.get(currentCode);
  const remaining = remainingDepth(byCode, currentCode);
  const progress = Math.round((answers.length / (answers.length + remaining)) * 100);

  function restart() {
    setAnswers([]);
    setCurrentCode(survey.startQuestionCode);
    setSelected([]);
    setResult(null);
    setError(null);
  }

  function toggle(code) {
    setError(null);
    if (question.answerType !== 'MULTIPLE') {
      setSelected([code]);
      return;
    }
    if (selected.includes(code)) {
      setSelected(selected.filter((c) => c !== code));
    } else if (selected.length < question.maxChoices) {
      setSelected([...selected, code]);
    }
  }

  function back() {
    const previous = answers.at(-1);
    setAnswers(answers.slice(0, -1));
    setCurrentCode(previous.questionCode);
    setSelected(selectionOf(previous));
    setError(null);
  }

  async function next() {
    const nextAnswers = [...answers, toAnswer(question, selected)];
    const nextCode = nextCodeOf(question, selected);
    if (nextCode) {
      setAnswers(nextAnswers);
      setCurrentCode(nextCode);
      setSelected([]);
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      setResult(await finish(nextAnswers, user));
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function skipSurvey() {
    try {
      await skip();
    } catch {
      /* Không ghi nhận được thì lần sau khách vẫn được mời làm khảo sát. */
    }
    navigate('/');
  }

  return (
    <div className="survey-card">
      <div
        className="survey-progress"
        role="progressbar"
        aria-label="Tiến độ khảo sát"
        aria-valuenow={progress}
        aria-valuemin={0}
        aria-valuemax={100}
      >
        <span style={{ width: `${progress}%` }} />
      </div>
      <p className="muted small">Câu {answers.length + 1}</p>
      <h1 className="survey-question">{question.content}</h1>
      {question.hint && <p className="muted small">{question.hint}</p>}

      {question.answerType === 'BRAND' ? (
        <BrandChoices answers={answers} selected={selected} onToggle={toggle} />
      ) : (
        <ChoiceList
          choices={question.options.map((o) => ({ value: o.code, label: o.label }))}
          selected={selected}
          multiple={question.answerType === 'MULTIPLE'}
          onToggle={toggle}
        />
      )}

      {error && <p className="alert alert-error">{error}</p>}
      <div className="survey-actions">
        {answers.length > 0 && (
          <button type="button" className="btn btn-ghost" onClick={back} disabled={submitting}>
            Quay lại
          </button>
        )}
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => next()}
          disabled={selected.length === 0 || submitting}
        >
          {submitting ? 'Đang xử lý...' : 'Tiếp tục'}
        </button>
        {user?.role !== 'ADMIN' && (
          <button type="button" className="btn-link small" onClick={() => skipSurvey()}>
            Bỏ qua khảo sát
          </button>
        )}
      </div>
    </div>
  );
}

/** Xem kết quả, rồi lưu lại theo người dùng (quà tặng không lưu vào sở thích, BR-38). */
async function finish(answers, user) {
  const evaluation = await surveyApi.evaluateSurvey(answers);
  const gift = evaluation.profile.purpose === 'GIFT';
  if (user?.role === 'CUSTOMER') {
    if (!gift) await surveyApi.saveMySurvey(answers);
  } else if (!user) {
    if (!gift) writeLocalSurvey('COMPLETED', answers);
    else if (!readLocalSurvey()) writeLocalSurvey('GIFT');
  }
  return { evaluation, gift, saved: !gift && user?.role !== 'ADMIN', guest: !user };
}

function SurveyDone({ result, onRestart }) {
  return (
    <div className="survey-result">
      <p className="muted small">Kết quả khảo sát</p>
      <SurveyProfile evaluation={result.evaluation} heading="h1" placement="SURVEY_RESULT" />
      {result.gift && (
        <p className="muted small">Kết quả tìm quà chỉ để gợi ý, không lưu vào sở thích của bạn.</p>
      )}
      {result.saved && result.guest && (
        <p className="muted small">
          Kết quả đang lưu trên trình duyệt này. <Link to="/register">Đăng ký</Link> hoặc{' '}
          <Link to="/login">đăng nhập</Link> để lưu vào tài khoản.
        </p>
      )}
      <div className="survey-actions">
        <Link to="/" className="btn btn-primary">
          Về trang chủ
        </Link>
        <button type="button" className="btn btn-ghost" onClick={onRestart}>
          Làm lại
        </button>
      </div>
    </div>
  );
}

/** Thương hiệu trong các danh mục khách quan tâm, kèm lựa chọn "không có thương hiệu ưa thích". */
function BrandChoices({ answers, selected, onToggle }) {
  const requestKey = JSON.stringify(answers);
  const { data, error, loading, reload } = useApiData(requestKey, (key) =>
    surveyApi.getSurveyBrands(JSON.parse(key))
  );
  if (loading) return <p className="muted">Đang tải thương hiệu...</p>;
  if (error) {
    return (
      <p className="alert alert-error">
        {getErrorMessage(error)}{' '}
        <button type="button" className="btn-link" onClick={reload}>
          Thử lại
        </button>
      </p>
    );
  }
  const choices = [
    ...data.map((brand) => ({ value: String(brand.id), label: brand.name })),
    { value: NO_BRAND, label: 'Không có thương hiệu ưa thích' },
  ];
  return <ChoiceList choices={choices} selected={selected} multiple={false} onToggle={onToggle} />;
}

function ChoiceList({ choices, selected, multiple, onToggle }) {
  return (
    <ul className="survey-options">
      {choices.map((choice) => (
        <li key={choice.value}>
          <button
            type="button"
            className="survey-option"
            aria-pressed={selected.includes(choice.value)}
            onClick={() => onToggle(choice.value)}
          >
            {multiple && (
              <span aria-hidden="true">{selected.includes(choice.value) ? '☑' : '☐'} </span>
            )}
            {choice.label}
          </button>
        </li>
      ))}
    </ul>
  );
}

export default SurveyPage;
