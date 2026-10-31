package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.CampaignTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Tặng mã giảm giá riêng cho một nhóm khách (US-26).
 *
 * @param target cả nhóm hoặc chỉ khách cần chăm sóc
 * @param voucher điều kiện của mã tặng, như khi tạo mã thường
 */
public record CampaignRequest(
    @NotNull(message = "Chưa chọn đối tượng") CampaignTarget target,
    @NotNull(message = "Chưa nhập mã") @Valid VoucherRequest voucher) {}
