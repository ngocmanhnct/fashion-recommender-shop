package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Một sự kiện với sản phẩm, bảng product_event_log (Mục 3.14; BR-20). Dùng để đo tỷ lệ bấm vào gợi
 * ý ở Chương 5. Hiện chỉ ghi ADD_TO_CART; placement, source, rank_position chỉ có giá trị với sự
 * kiện của gợi ý, làm từ Tuần 8.
 */
@Entity
@Table(name = "product_event_log")
public class ProductEventLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "account_id")
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ProductEventType eventType;

  @Column(length = 50)
  private String placement;

  @Column(length = 30)
  private String source;

  /** Thứ hạng trong danh sách gợi ý; cột rank_position vì RANK là từ khóa của MySQL 8. */
  @Column(name = "rank_position")
  private Integer rankPosition;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected ProductEventLog() {}

  /** Tạo sự kiện không thuộc khối gợi ý (vd thêm vào giỏ hàng). */
  public ProductEventLog(
      Account account, Product product, ProductEventType eventType, LocalDateTime createdAt) {
    this.account = account;
    this.product = product;
    this.eventType = eventType;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public Product getProduct() {
    return product;
  }

  public ProductEventType getEventType() {
    return eventType;
  }

  public String getPlacement() {
    return placement;
  }

  public String getSource() {
    return source;
  }

  public Integer getRankPosition() {
    return rankPosition;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
