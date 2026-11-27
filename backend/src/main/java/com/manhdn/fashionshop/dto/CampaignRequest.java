package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tặng mã giảm giá riêng cho một nhóm khách (US-24) hoặc bật tự tặng cho khách mới vào nhóm
 * (US-24).
 *
 * @param target cả nhóm, chỉ khách cần chăm sóc, hoặc tự tặng (AUTO)
 * @param voucher điều kiện của mã tặng, như khi tạo mã thường; với AUTO, thời hạn của mã là thời
 *     gian chạy chương trình
 * @param validDays chỉ dùng với AUTO: số ngày mỗi khách được dùng mã kể từ lúc nhận
 * @param holdoutPercent phần trăm khách đủ điều kiện giữ lại ngẫu nhiên làm nhóm đối chứng (không
 *     nhận mã), 0 đến 50; bỏ trống là 0
 * @param maxRecipients chỉ dùng với AUTO: trần số khách của chương trình (kể cả khách bị giữ lại);
 *     bỏ trống là không giới hạn
 * @param grossMarginPercent lãi gộp trên tiền hàng (%), để tính lãi tăng thêm; bỏ trống là không
 *     tính lãi
 */
public record CampaignRequest(
    @NotNull(message = "Chưa chọn đối tượng") CampaignTarget target,
    @NotNull(message = "Chưa nhập mã") @Valid VoucherRequest voucher,
    @Min(value = 1, message = "Số ngày dùng mã từ 1 đến 90")
        @Max(value = 90, message = "Số ngày dùng mã từ 1 đến 90")
        Integer validDays,
    @Min(value = 0, message = "Tỷ lệ giữ lại từ 0 đến 50%")
        @Max(value = 50, message = "Tỷ lệ giữ lại từ 0 đến 50%")
        Integer holdoutPercent,
    @Min(value = 1, message = "Số khách tối đa từ 1 đến 100.000")
        @Max(value = 100_000, message = "Số khách tối đa từ 1 đến 100.000")
        Integer maxRecipients,
    @Min(value = 1, message = "Lãi gộp từ 1 đến 100%")
        @Max(value = 100, message = "Lãi gộp từ 1 đến 100%")
        Integer grossMarginPercent) {}
