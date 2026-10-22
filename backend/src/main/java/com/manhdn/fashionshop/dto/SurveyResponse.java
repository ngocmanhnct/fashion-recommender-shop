package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Toàn bộ cây câu hỏi khảo sát lần đầu (US-23, BR-41).
 *
 * @param startQuestionCode mã câu hỏi đầu tiên
 * @param questions mọi câu hỏi
 */
public record SurveyResponse(String startQuestionCode, List<SurveyQuestionResponse> questions) {}
