package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Một lần thử nghiệm A/B gợi ý ở trang chủ, bảng ab_experiment (US-27). */
@Entity
@Table(name = "ab_experiment")
public class AbExperiment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(nullable = false, updatable = false)
  private int treatmentPercent;

  @Column(nullable = false, updatable = false)
  private LocalDateTime startedAt;

  private LocalDateTime endedAt;

  /** Constructor rỗng cho JPA. */
  protected AbExperiment() {}

  /** Bắt đầu một thử nghiệm với tỷ lệ khách vào nhóm TREATMENT (phần trăm). */
  public AbExperiment(String name, int treatmentPercent, LocalDateTime startedAt) {
    this.name = name;
    this.treatmentPercent = treatmentPercent;
    this.startedAt = startedAt;
  }

  /** Kết thúc thử nghiệm; khách không còn bị chia nhóm từ lúc này. */
  public void stop(LocalDateTime now) {
    this.endedAt = now;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getTreatmentPercent() {
    return treatmentPercent;
  }

  public LocalDateTime getStartedAt() {
    return startedAt;
  }

  public LocalDateTime getEndedAt() {
    return endedAt;
  }

  /** Thử nghiệm còn đang chạy (chưa dừng). */
  public boolean isRunning() {
    return endedAt == null;
  }
}
