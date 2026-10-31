package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Đánh giá của một tài khoản cho một sản phẩm đã nhận hàng, bảng review (Mục 3.8; US-15, BR-17,
 * BR-18). Mỗi tài khoản có tối đa 1 đánh giá cho mỗi sản phẩm: đánh giá lại thì sửa dòng cũ.
 */
@Entity
@Table(name = "review")
public class Review {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  private Product product;

  /** Dòng đơn Đã giao chứng minh khách đã nhận sản phẩm (BR-17). */
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_item_id", nullable = false)
  private OrderItem orderItem;

  @JdbcTypeCode(SqlTypes.TINYINT)
  @Column(nullable = false)
  private Integer rating;

  @Column(columnDefinition = "TEXT")
  private String comment;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  /** Constructor rỗng cho JPA. */
  protected Review() {}

  /** Tạo đánh giá mới. */
  public Review(
      Account account,
      Product product,
      OrderItem orderItem,
      int rating,
      String comment,
      LocalDateTime now) {
    this.account = account;
    this.product = product;
    this.orderItem = orderItem;
    this.rating = rating;
    this.comment = comment;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Sửa đánh giá (BR-18), gắn với dòng đơn Đã giao mới nhất. */
  public void edit(OrderItem orderItem, int rating, String comment, LocalDateTime now) {
    this.orderItem = orderItem;
    this.rating = rating;
    this.comment = comment;
    this.updatedAt = now;
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

  public OrderItem getOrderItem() {
    return orderItem;
  }

  public int getRating() {
    return rating;
  }

  public String getComment() {
    return comment;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }
}
