package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Một nhóm khách hàng của lần huấn luyện mới nhất, bảng customer_segment (Mục 3.10; US-19). Chỉ
 * ml-service ghi; backend chỉ đọc.
 */
@Entity
@Immutable
@Table(name = "customer_segment")
public class CustomerSegment {

  @Id private Long id;

  /** Mã nhóm: VIP, LOYAL, SAVER, NEW, AT_RISK, LOST. */
  @Column(nullable = false)
  private String code;

  @Column(nullable = false)
  private String name;

  /** Ý nghĩa, số liệu trung bình và gợi ý chăm sóc. */
  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private LocalDateTime computedAt;

  /** Constructor rỗng cho JPA. */
  protected CustomerSegment() {}

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public LocalDateTime getComputedAt() {
    return computedAt;
  }
}
