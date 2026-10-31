package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.manhdn.fashionshop.dto.AbTestResultResponse;
import com.manhdn.fashionshop.entity.AbExperiment;
import com.manhdn.fashionshop.entity.AbVariant;
import com.manhdn.fashionshop.repository.AbAssignmentRepository;
import com.manhdn.fashionshop.repository.AbAssignmentRepository.VariantStats;
import com.manhdn.fashionshop.repository.AbExperimentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Kiểm tra kết luận của thử nghiệm A/B theo cỡ mẫu và giá trị p (BR-46). */
class AbTestServiceTest {

  private final AbExperimentRepository experiments = mock(AbExperimentRepository.class);
  private final AbAssignmentRepository assignments = mock(AbAssignmentRepository.class);
  private final AbTestService service =
      new AbTestService(experiments, assignments, Clock.systemDefaultZone());

  @Test
  void verdictsFollowSampleSizeAndSignificance() {
    // Nhóm gợi ý: 60/100 khách bấm, 20/100 đặt hàng; nhóm bán chạy: 40/100 bấm, 50/100 đặt hàng.
    AbTestResultResponse result = resultFor(stats(100, 60, 20, 10), stats(100, 40, 50, 10));
    assertThat(result.clickRate().verdict()).isEqualTo("TREATMENT_BETTER");
    assertThat(result.clickRate().difference()).isEqualTo(20.0);
    assertThat(result.clickRate().probability()).isEqualTo(0.0047);
    assertThat(result.orderRate().verdict()).isEqualTo("CONTROL_BETTER");
    assertThat(result.boughtClickedRate().verdict()).isEqualTo("NO_DIFFERENCE");
    assertThat(result.treatment().revenuePerUser()).isEqualTo(1_000);

    AbTestResultResponse small = resultFor(stats(29, 29, 0, 0), stats(100, 0, 0, 0));
    assertThat(small.clickRate().verdict()).isEqualTo("NOT_ENOUGH_DATA");
    assertThat(small.clickRate().probability()).isNotNull();
  }

  private AbTestResultResponse resultFor(VariantStats treatment, VariantStats control) {
    AbExperiment experiment = new AbExperiment("Thử", 50, LocalDateTime.now().minusDays(1));
    ReflectionTestUtils.setField(experiment, "id", 1L);
    when(experiments.findById(1L)).thenReturn(Optional.of(experiment));
    when(assignments.stats(anyLong(), any()))
        .thenReturn(Map.of(AbVariant.TREATMENT, treatment, AbVariant.CONTROL, control));
    return service.result(1L);
  }

  private static VariantStats stats(long users, long clicked, long ordered, long boughtClicked) {
    return new VariantStats(
        users, users * 8, clicked, clicked, ordered, ordered, users * 1_000L, boughtClicked);
  }
}
