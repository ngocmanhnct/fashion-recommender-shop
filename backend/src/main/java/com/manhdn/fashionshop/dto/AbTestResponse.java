package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.AbExperiment;
import java.time.LocalDateTime;

/**
 * Một lần thử nghiệm A/B gợi ý (US-27).
 *
 * @param id mã thử nghiệm
 * @param name tên thử nghiệm
 * @param treatmentPercent phần trăm khách vào nhóm thấy gợi ý của ml-service
 * @param startedAt lúc bắt đầu
 * @param endedAt lúc dừng; null khi còn chạy
 * @param running còn đang chạy
 */
public record AbTestResponse(
    long id,
    String name,
    int treatmentPercent,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    boolean running) {

  /** Chuyển entity sang DTO. */
  public static AbTestResponse from(AbExperiment e) {
    return new AbTestResponse(
        e.getId(),
        e.getName(),
        e.getTreatmentPercent(),
        e.getStartedAt(),
        e.getEndedAt(),
        e.isRunning());
  }
}
