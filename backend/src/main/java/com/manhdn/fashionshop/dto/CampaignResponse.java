package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import java.time.LocalDateTime;

/**
 * Một chiến dịch ưu đãi theo nhóm và kết quả của nó (US-26). Kết quả chỉ tính đơn chưa hủy dùng mã
 * của chiến dịch.
 *
 * @param id mã số chiến dịch
 * @param segmentCode mã nhóm lúc tặng
 * @param segmentName tên nhóm lúc tặng
 * @param target cả nhóm hoặc khách cần chăm sóc
 * @param createdAt lúc tặng
 * @param recipientCount số khách được tặng
 * @param voucher mã đã tặng
 * @param buyers số khách đã đặt đơn bằng mã
 * @param conversionRate tỷ lệ khách được tặng đã đặt đơn (%), làm tròn 1 chữ số
 * @param orders số đơn dùng mã
 * @param revenue tổng thanh toán của các đơn dùng mã (VNĐ)
 * @param discount tổng tiền đã giảm (VNĐ)
 * @param revenuePerDiscount doanh thu mang về trên mỗi đồng giảm; null khi chưa giảm đồng nào
 */
public record CampaignResponse(
    long id,
    String segmentCode,
    String segmentName,
    CampaignTarget target,
    LocalDateTime createdAt,
    int recipientCount,
    VoucherResponse voucher,
    long buyers,
    double conversionRate,
    long orders,
    long revenue,
    long discount,
    Double revenuePerDiscount) {}
