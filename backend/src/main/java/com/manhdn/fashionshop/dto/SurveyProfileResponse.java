package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.BrandLoyalty;
import com.manhdn.fashionshop.entity.ShoppingStyle;
import com.manhdn.fashionshop.entity.SurveyPurpose;
import java.math.BigDecimal;
import java.util.List;

/**
 * Hồ sơ mua sắm dựng từ câu trả lời khảo sát (US-23, BR-42).
 *
 * @param purpose mua cho bản thân hay tìm quà
 * @param style phong cách mua sắm
 * @param styleName tên phong cách để hiển thị, vd "Người mua tiết kiệm"
 * @param summary một câu mô tả kết quả cho khách
 * @param categories các danh mục quan tâm
 * @param brand thương hiệu tin dùng, có thể null
 * @param brandLoyalty mức ưu tiên thương hiệu, có thể null
 * @param priceMin giá thấp nhất, VNĐ
 * @param priceMax giá cao nhất, VNĐ (đã nới 30% nếu khách chấp nhận vượt ngân sách); null = không
 *     giới hạn
 * @param minRating ngưỡng điểm đánh giá, có thể null
 */
public record SurveyProfileResponse(
    SurveyPurpose purpose,
    ShoppingStyle style,
    String styleName,
    String summary,
    List<CategoryResponse> categories,
    BrandResponse brand,
    BrandLoyalty brandLoyalty,
    long priceMin,
    Long priceMax,
    BigDecimal minRating) {}
