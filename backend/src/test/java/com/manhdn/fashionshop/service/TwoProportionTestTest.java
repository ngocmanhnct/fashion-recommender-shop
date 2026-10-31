package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.manhdn.fashionshop.entity.AbVariant;
import org.junit.jupiter.api.Test;

/** Kiểm tra kiểm định z hai tỷ lệ và cách chia nhóm theo băm (US-27, BR-46). */
class TwoProportionTestTest {

  @Test
  void pValueMatchesTextbookValues() {
    // 60/100 so với 40/100: z = 2,828, p ≈ 0,00468.
    assertThat(TwoProportionTest.probabilityOfEqualRates(60, 100, 40, 100))
        .isCloseTo(0.00468, within(1e-4));
    // 52/100 so với 48/100: z = 0,566, p ≈ 0,5716.
    assertThat(TwoProportionTest.probabilityOfEqualRates(52, 100, 48, 100))
        .isCloseTo(0.5716, within(1e-3));
    assertThat(TwoProportionTest.normalCdf(1.96)).isCloseTo(0.975, within(1e-4));
    assertThat(TwoProportionTest.normalCdf(-1.96)).isCloseTo(0.025, within(1e-4));
  }

  @Test
  void equalOrDegenerateRatesGiveOne() {
    assertThat(TwoProportionTest.probabilityOfEqualRates(10, 50, 20, 100))
        .isCloseTo(1.0, within(1e-9));
    assertThat(TwoProportionTest.probabilityOfEqualRates(0, 40, 0, 40)).isEqualTo(1.0);
    assertThat(TwoProportionTest.probabilityOfEqualRates(40, 40, 40, 40)).isEqualTo(1.0);
    assertThatThrownBy(() -> TwoProportionTest.probabilityOfEqualRates(0, 0, 1, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hashSplitIsFixedPerAccountAndFollowsThePercentage() {
    assertThat(AbTestService.variantOf(7, 123, 50)).isEqualTo(AbTestService.variantOf(7, 123, 50));
    int half = 0;
    int tenth = 0;
    for (long account = 1; account <= 10_000; account++) {
      if (AbTestService.variantOf(3, account, 50) == AbVariant.TREATMENT) {
        half++;
      }
      if (AbTestService.variantOf(3, account, 10) == AbVariant.TREATMENT) {
        tenth++;
      }
    }
    assertThat(half).isBetween(4_800, 5_200);
    assertThat(tenth).isBetween(900, 1_100);
  }
}
