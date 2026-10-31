package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Tặng mã giảm giá riêng cho một nhóm khách (US-26) hoặc bật tự tặng cho khách mới vào nhóm
 * (US-24).
 *
 * @param target cả nhóm, chỉ khách cần chăm sóc, hoặc tự tặng (AUTO)
 * @param voucher điều kiện của mã tặng, như khi tạo mã thường; với AUTO, thời hạn của mã là thời
 *     gian chạy chương trình
 * @param validDays chỉ dùng với AUTO: số ngày mỗi khách được dùng mã kể từ lúc nhận
 */
public record CampaignRequest(
    @NotNull(message = "Chưa chọn đối tượng") CampaignTarget target,
    @NotNull(message = "Chưa nhập mã") @Valid VoucherRequest voucher,
    @Min(value = 1, message = "Số ngày dùng mã từ 1 đến 90")
        @Max(value = 90, message = "Số ngày dùng mã từ 1 đến 90")
        Integer validDays) {}
