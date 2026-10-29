import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SurveyPage from '../../src/pages/SurveyPage';
import * as surveyApi from '../../src/services/surveyApi';
import { readLocalSurvey } from '../../src/services/surveyStorage';
import { authValue, renderWithContext, users } from '../renderWithContext';

vi.mock('../../src/services/surveyApi');
vi.mock('../../src/services/recommendationApi');

const option = (code, label, nextQuestionCode = null) => ({ code, label, nextQuestionCode });

// Cây câu hỏi nhỏ: mua cho mình → chọn tối đa 2 phong cách → chọn thương hiệu; tìm quà → 1 câu.
const SURVEY = {
  startQuestionCode: 'Q1',
  questions: [
    {
      code: 'Q1',
      content: 'Bạn mua cho ai?',
      answerType: 'SINGLE',
      options: [option('SELF', 'Cho bản thân', 'Q2'), option('GIFT', 'Tìm quà', 'Q3')],
    },
    {
      code: 'Q2',
      content: 'Bạn thích phong cách nào?',
      hint: 'Chọn tối đa 2',
      answerType: 'MULTIPLE',
      maxChoices: 2,
      options: [
        option('SPORT', 'Thể thao', 'QB'),
        option('OFFICE', 'Công sở', 'QB'),
        option('STREET', 'Đường phố', 'QB'),
      ],
    },
    {
      code: 'QB',
      content: 'Thương hiệu yêu thích?',
      answerType: 'BRAND',
      nextQuestionCode: null,
      options: [],
    },
    {
      code: 'Q3',
      content: 'Người nhận quà là ai?',
      answerType: 'SINGLE',
      options: [option('FRIEND', 'Bạn bè')],
    },
  ],
};

const EVALUATION = {
  profile: { purpose: 'SELF', styleName: 'Người mua năng động', summary: 'Thích đồ thể thao' },
  products: [],
};

function renderSurvey(user = null) {
  return renderWithContext(<SurveyPage />, { auth: authValue({ user }), path: '/survey' });
}

const click = (name) => userEvent.click(screen.getByRole('button', { name }));

async function answerSelfBranch() {
  await userEvent.click(await screen.findByRole('button', { name: 'Cho bản thân' }));
  await click('Tiếp tục');
  await click(/Thể thao/);
  await click(/Công sở/);
  await click(/Đường phố/);
  await click('Tiếp tục');
  await userEvent.click(await screen.findByRole('button', { name: 'Adidas' }));
  await click('Tiếp tục');
}

const SELF_ANSWERS = [
  { questionCode: 'Q1', optionCodes: ['SELF'] },
  { questionCode: 'Q2', optionCodes: ['SPORT', 'OFFICE'] },
  { questionCode: 'QB', brandId: 4 },
];

describe('SurveyPage', () => {
  beforeEach(() => {
    vi.mocked(surveyApi.getSurvey).mockResolvedValue(SURVEY);
    vi.mocked(surveyApi.getSurveyBrands).mockResolvedValue([{ id: 4, name: 'Adidas' }]);
    vi.mocked(surveyApi.evaluateSurvey).mockResolvedValue(EVALUATION);
    vi.mocked(surveyApi.getMySurvey).mockResolvedValue({ status: 'NONE', answers: [] });
  });

  it('đi theo nhánh, chọn tối đa số đáp án, rồi lưu ở trình duyệt khi chưa đăng nhập', async () => {
    renderSurvey();
    expect(await screen.findByRole('heading', { name: 'Bạn mua cho ai?' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tiếp tục' })).toBeDisabled();
    await answerSelfBranch();

    expect(surveyApi.getSurveyBrands).toHaveBeenCalledWith(SELF_ANSWERS.slice(0, 2));
    expect(surveyApi.evaluateSurvey).toHaveBeenCalledWith(SELF_ANSWERS);
    expect(await screen.findByRole('heading', { name: 'Người mua năng động' })).toBeTruthy();
    expect(screen.getByText(/đang lưu trên trình duyệt này/)).toBeInTheDocument();
    expect(readLocalSurvey()).toEqual({ status: 'COMPLETED', answers: SELF_ANSWERS });
  });

  it('khách hàng làm xong thì lưu vào tài khoản', async () => {
    renderSurvey(users.CUSTOMER);
    await answerSelfBranch();
    await screen.findByRole('heading', { name: 'Người mua năng động' });
    expect(surveyApi.saveMySurvey).toHaveBeenCalledWith(SELF_ANSWERS);
  });

  it('tìm quà thì không lưu sở thích; quay lại giữ đáp án cũ; làm lại về câu đầu', async () => {
    vi.mocked(surveyApi.evaluateSurvey).mockResolvedValue({
      ...EVALUATION,
      profile: { ...EVALUATION.profile, purpose: 'GIFT' },
    });
    renderSurvey();
    await userEvent.click(await screen.findByRole('button', { name: 'Tìm quà' }));
    await click('Tiếp tục');
    await click('Quay lại');
    expect(screen.getByRole('button', { name: 'Tìm quà' })).toHaveAttribute('aria-pressed', 'true');
    await click('Tiếp tục');
    await click('Bạn bè');
    await click('Tiếp tục');

    expect(await screen.findByText(/không lưu vào sở thích/)).toBeInTheDocument();
    expect(readLocalSurvey()).toEqual({ status: 'GIFT', answers: [] });
    await click('Làm lại');
    expect(screen.getByRole('heading', { name: 'Bạn mua cho ai?' })).toBeInTheDocument();
  });

  it('chọn không có thương hiệu ưa thích thì gửi brandId null', async () => {
    renderSurvey();
    await userEvent.click(await screen.findByRole('button', { name: 'Cho bản thân' }));
    await click('Tiếp tục');
    await click(/Thể thao/);
    await click('Tiếp tục');
    await userEvent.click(await screen.findByRole('button', { name: /Không có thương hiệu/ }));
    await click('Tiếp tục');
    await screen.findByRole('heading', { name: 'Người mua năng động' });
    expect(surveyApi.evaluateSurvey.mock.calls[0][0].at(-1)).toEqual({
      questionCode: 'QB',
      brandId: null,
    });
  });

  it('bỏ qua khảo sát thì ghi nhận rồi về trang chủ; quản trị viên không có nút bỏ qua', async () => {
    const { unmount } = renderSurvey(users.ADMIN);
    await screen.findByRole('heading', { name: 'Bạn mua cho ai?' });
    expect(screen.queryByRole('button', { name: 'Bỏ qua khảo sát' })).not.toBeInTheDocument();
    unmount();

    renderSurvey();
    await userEvent.click(await screen.findByRole('button', { name: 'Bỏ qua khảo sát' }));
    expect(readLocalSurvey()).toEqual({ status: 'SKIPPED', answers: [] });
    expect(await screen.findByTestId('location')).toHaveTextContent('/');
  });

  it('lỗi tải câu hỏi, lỗi đánh giá thì hiện lỗi', async () => {
    vi.mocked(surveyApi.getSurvey).mockRejectedValueOnce({
      response: { data: { detail: 'Máy chủ lỗi' } },
    });
    renderSurvey();
    expect(await screen.findByText('Máy chủ lỗi')).toBeInTheDocument();
    await click('Thử lại');

    vi.mocked(surveyApi.evaluateSurvey).mockRejectedValue({
      response: { data: { detail: 'Câu trả lời không hợp lệ' } },
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Tìm quà' }));
    await click('Tiếp tục');
    await click('Bạn bè');
    await click('Tiếp tục');
    expect(await screen.findByText('Câu trả lời không hợp lệ')).toBeInTheDocument();
  });
});
