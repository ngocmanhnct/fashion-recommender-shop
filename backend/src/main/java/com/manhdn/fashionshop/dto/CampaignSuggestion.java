package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import com.manhdn.fashionshop.entity.DiscountType;

/**
 * Việc nên làm với một nhóm khách hàng và ưu đãi gợi ý để làm việc đó (US-26). Quản trị viên sửa
 * được mọi con số trước khi tặng.
 *
 * @param goal mục tiêu, vd "Có đơn thứ hai"
 * @param reason vì sao nhóm này cần làm vậy
 * @param target nên tặng cho cả nhóm hay chỉ khách cần chăm sóc
 * @param discountType kiểu giảm gợi ý
 * @param discountValue phần trăm hoặc số tiền giảm gợi ý
 * @param maxDiscountAmount trần giảm gợi ý (chỉ mã phần trăm), null nếu không trần
 * @param minOrderAmount đơn tối thiểu gợi ý, tính từ giá trị đơn trung bình của nhóm; null nếu
 *     không yêu cầu
 * @param validDays số ngày mã còn hạn tính từ lúc tặng
 */
public record CampaignSuggestion(
    String goal,
    String reason,
    CampaignTarget target,
    DiscountType discountType,
    long discountValue,
    Long maxDiscountAmount,
    Long minOrderAmount,
    int validDays) {}
