package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.manhdn.fashionshop.dto.CampaignSuggestion;
import com.manhdn.fashionshop.entity.CampaignTarget;
import com.manhdn.fashionshop.entity.DiscountType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SegmentPlaybookTest {

  @ParameterizedTest
  @ValueSource(strings = {"VIP", "LOYAL", "SAVER", "NEW", "AT_RISK", "LOST", "OTHER"})
  void everySegmentHasGoalAndOfferWithinVoucherRules(String code) {
    CampaignSuggestion s = SegmentPlaybook.suggest(code, 200_000);

    assertThat(s.goal()).isEqualTo(SegmentPlaybook.goalOf(code)).isNotBlank();
    assertThat(s.reason()).isNotBlank();
    assertThat(s.discountValue()).isPositive();
    assertThat(s.validDays()).isBetween(7, 14);
    if (s.discountType() == DiscountType.PERCENT) {
      assertThat(s.discountValue()).isLessThanOrEqualTo(100);
      assertThat(s.maxDiscountAmount()).isPositive();
    } else {
      assertThat(s.maxDiscountAmount()).isNull();
    }
  }

  @Test
  void onlyAtRiskTargetsCareCustomers() {
    assertThat(SegmentPlaybook.suggest("AT_RISK", 0).target()).isEqualTo(CampaignTarget.CARE);
    assertThat(SegmentPlaybook.suggest("LOST", 0).target()).isEqualTo(CampaignTarget.ALL);
  }

  @Test
  void minimumOrderRoundsUpFromGroupsAverage() {
    assertThat(SegmentPlaybook.suggest("SAVER", 120_000).minOrderAmount()).isEqualTo(200_000L);
    assertThat(SegmentPlaybook.aboveAverage(100_000, 1.5)).isEqualTo(150_000L);
    assertThat(SegmentPlaybook.aboveAverage(0, 1.3)).isNull();
  }
}
