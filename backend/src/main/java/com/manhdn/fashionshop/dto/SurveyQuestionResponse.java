package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.SurveyAnswerType;
import java.util.List;

/**
 * Một câu hỏi khảo sát lần đầu kèm đáp án.
 *
 * @param code mã câu hỏi
 * @param content nội dung câu hỏi
 * @param hint chú thích nhỏ dưới câu hỏi, có thể null
 * @param answerType cách trả lời
 * @param maxChoices số đáp án được chọn tối đa
 * @param nextQuestionCode câu tiếp theo của câu chọn thương hiệu (các câu khác đi theo đáp án)
 * @param options các đáp án; rỗng ở câu chọn thương hiệu (lấy qua {@code POST /api/survey/brands})
 */
public record SurveyQuestionResponse(
    String code,
    String content,
    String hint,
    SurveyAnswerType answerType,
    int maxChoices,
    String nextQuestionCode,
    List<SurveyOptionResponse> options) {}
