package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.manhdn.fashionshop.repository.VoucherRecipientRepository.GroupBehavior;
import org.junit.jupiter.api.Test;

/** Lãi tăng thêm của chiến dịch so với nhóm đối chứng (rà soát 12/11/2026, P2). */
class CampaignIncrementalProfitTest {

  @Test
  void codeThatMostlyPaysCustomersWhoWouldBuyAnywayLosesMoney() {
    // Ví dụ 1 của bản rà soát: 1.000 khách mỗi nhóm, 38,5% tự mua lại, mã kéo lên 42%, đơn
    // 500.000đ, lãi gộp 40%, mã giảm 10%: báo cáo cũ ghi "gấp 10 lần" nhưng thực tế lỗ 14 triệu.
    GroupBehavior treated = new GroupBehavior(1, false, 1000, 420, 420L * 500_000);
    GroupBehavior held = new GroupBehavior(1, true, 1000, 385, 385L * 500_000);
    assertThat(CampaignService.incrementalProfit(treated, held, 40, 420L * 50_000))
        .isEqualTo(-14_000_000L);
  }

  @Test
  void holdoutOfDifferentSizeIsScaledToTheTreatedGroup() {
    GroupBehavior treated = new GroupBehavior(1, false, 900, 90, 90L * 400_000);
    GroupBehavior held = new GroupBehavior(1, true, 100, 5, 5L * 400_000);
    // Đối chứng quy về 900 khách: 45 đơn; tăng thêm 45 đơn × 400.000đ × 50% − 4,5 triệu tiền giảm.
    assertThat(CampaignService.incrementalProfit(treated, held, 50, 4_500_000))
        .isEqualTo(4_500_000L);
  }

  @Test
  void noHoldoutOrNoMarginMeansNoProfitFigure() {
    GroupBehavior treated = new GroupBehavior(1, false, 10, 1, 100_000);
    GroupBehavior empty = new GroupBehavior(1, true, 0, 0, 0);
    assertThat(CampaignService.incrementalProfit(treated, null, 40, 0)).isNull();
    assertThat(CampaignService.incrementalProfit(treated, empty, 40, 0)).isNull();
    assertThat(CampaignService.incrementalProfit(treated, empty, null, 0)).isNull();
    assertThat(CampaignService.incrementalProfit(null, empty, 40, 0)).isNull();
  }
}
