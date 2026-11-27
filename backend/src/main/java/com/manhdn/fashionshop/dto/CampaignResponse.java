package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import java.time.LocalDateTime;

/**
 * Một chiến dịch ưu đãi theo nhóm và kết quả của nó (US-24). Kết quả chỉ tính đơn chưa hủy dùng mã
 * của chiến dịch.
 *
 * @param id mã số chiến dịch
 * @param segmentCode mã nhóm lúc tặng
 * @param segmentName tên nhóm lúc tặng
 * @param target cả nhóm, khách cần chăm sóc, hoặc tự tặng khách mới vào nhóm (AUTO)
 * @param validDays với AUTO: số ngày mỗi khách được dùng mã kể từ lúc nhận; null với lần tặng tay
 * @param autoEnabled chương trình tự tặng đang chạy
 * @param createdAt lúc tặng
 * @param recipientCount số khách được tặng
 * @param voucher mã đã tặng
 * @param buyers số khách đã đặt đơn bằng mã
 * @param conversionRate tỷ lệ khách được tặng đã đặt đơn (%), làm tròn 1 chữ số
 * @param orders số đơn dùng mã
 * @param revenue tổng thanh toán của các đơn dùng mã (VNĐ)
 * @param discount tổng tiền đã giảm (VNĐ)
 * @param revenuePerDiscount doanh thu các đơn dùng mã trên mỗi đồng giảm; null khi chưa giảm đồng
 *     nào. Chưa trừ những khách vốn sẽ mua dù không có mã, nên không phải hiệu quả tăng thêm
 * @param holdoutPercent phần trăm khách đủ điều kiện giữ lại làm nhóm đối chứng
 * @param holdoutCount số khách bị giữ lại, không nhận mã
 * @param maxRecipients với AUTO: trần số khách (kể cả khách bị giữ lại); null là không giới hạn
 * @param grossMarginPercent lãi gộp trên tiền hàng (%) admin nhập; null là không tính lãi
 * @param purchaseRate tỷ lệ khách nhận mã đã đặt ít nhất 1 đơn chưa hủy trong thời gian dùng mã
 *     (dùng mã hay không, %); null khi chưa ai nhận
 * @param holdoutPurchaseRate cùng tỷ lệ đó của khách bị giữ lại; null khi không giữ lại ai
 * @param liftPoints tỷ lệ mua của khách nhận mã trừ của khách bị giữ lại (điểm %); null khi không
 *     có nhóm đối chứng
 * @param incrementalProfit lãi tăng thêm nhờ mã (VNĐ): (tiền hàng của khách nhận mã − tiền hàng của
 *     khách bị giữ lại quy về cùng số khách) × lãi gộp − tiền đã giảm; âm là lỗ; null khi không có
 *     nhóm đối chứng hoặc chưa nhập lãi gộp
 */
public record CampaignResponse(
    long id,
    String segmentCode,
    String segmentName,
    CampaignTarget target,
    Integer validDays,
    boolean autoEnabled,
    LocalDateTime createdAt,
    int recipientCount,
    VoucherResponse voucher,
    long buyers,
    double conversionRate,
    long orders,
    long revenue,
    long discount,
    Double revenuePerDiscount,
    int holdoutPercent,
    int holdoutCount,
    Integer maxRecipients,
    Integer grossMarginPercent,
    Double purchaseRate,
    Double holdoutPurchaseRate,
    Double liftPoints,
    Long incrementalProfit) {}
