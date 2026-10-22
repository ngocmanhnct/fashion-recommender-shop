package com.manhdn.fashionshop.dto;

/**
 * Một đáp án của câu hỏi khảo sát.
 *
 * @param code mã đáp án
 * @param label nội dung hiển thị
 * @param nextQuestionCode mã câu hỏi tiếp theo khi chọn đáp án này; null = kết thúc khảo sát
 */
public record SurveyOptionResponse(String code, String label, String nextQuestionCode) {}
