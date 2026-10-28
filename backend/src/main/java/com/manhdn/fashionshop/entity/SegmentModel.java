package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Immutable;

/**
 * Một lần huấn luyện mô hình phân nhóm khách hàng, bảng segment_model (Mục 3.10, migration V6;
 * BR-23). Chỉ ml-service ghi; backend chỉ đọc để hiện chất lượng phân nhóm cho Quản trị viên.
 */
@Entity
@Immutable
@Table(name = "segment_model")
public class SegmentModel {

  @Id private Long id;

  /** Số nhóm K đã chọn. */
  @Column(nullable = false)
  private Integer clusterCount;

  /** K tại điểm gãy của đường inertia (Elbow). */
  @Column(nullable = false)
  private Integer elbowClusterCount;

  /** Số khách dùng để huấn luyện. */
  @Column(nullable = false)
  private Integer customerCount;

  @Column(nullable = false)
  private Double silhouette;

  @Column(nullable = false)
  private LocalDateTime trainedAt;

  /** Constructor rỗng cho JPA. */
  protected SegmentModel() {}

  public Long getId() {
    return id;
  }

  public Integer getClusterCount() {
    return clusterCount;
  }

  public Integer getElbowClusterCount() {
    return elbowClusterCount;
  }

  public Integer getCustomerCount() {
    return customerCount;
  }

  public Double getSilhouette() {
    return silhouette;
  }

  public LocalDateTime getTrainedAt() {
    return trainedAt;
  }
}
