package com.manhdn.fashionshop.dto;

import java.util.List;

/**
 * Kết quả khảo sát và sản phẩm gợi ý theo kết quả đó.
 *
 * @param profile hồ sơ mua sắm
 * @param products sản phẩm gợi ý kèm lý do, tối đa 8
 */
public record SurveyEvaluationResponse(
    SurveyProfileResponse profile, List<RelatedProductResponse> products) {}
