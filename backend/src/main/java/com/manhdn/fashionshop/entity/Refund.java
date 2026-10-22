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
import jakarta.persistence.OneToOne;
import java.time.LocalDateTime;

/** Một lần hoàn tiền cho đơn đã thanh toán online rồi bị hủy (BR-28), bảng refund. */
@Entity
public class Refund {

  /** Số lần gọi cổng thanh toán tối đa trước khi chuyển Hoàn tiền thất bại. */
  public static final int MAX_ATTEMPTS = 3;

  /** Độ dài tối đa của cột failure_reason. */
  private static final int MAX_REASON_LENGTH = 500;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  private Order order;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private RefundStatus status;

  @Column(nullable = false, updatable = false)
  private Long amount;

  @Column(nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(nullable = false)
  private Integer retryCount;

  @Column(nullable = false, updatable = false)
  private LocalDateTime initiatedAt;

  private LocalDateTime completedAt;

  private String failureReason;

  /** Constructor rỗng cho JPA. */
  protected Refund() {}

  /**
   * Khởi tạo hoàn tiền 100% số tiền đã thanh toán của đơn (BR-28). Mỗi đơn có tối đa 1 lần hoàn
   * tiền: mã chống trùng gắn với mã đơn, cột order_id là UNIQUE.
   */
  public Refund(Order order, LocalDateTime now) {
    this.order = order;
    this.status = RefundStatus.PROCESSING;
    this.amount = order.getTotalAmount();
    this.idempotencyKey = "refund-order-" + order.getId();
    this.retryCount = 0;
    this.initiatedAt = now;
    order.attachRefund(this);
  }

  /** Cổng thanh toán xác nhận đã hoàn tiền. */
  public void complete(LocalDateTime now) {
    this.status = RefundStatus.COMPLETED;
    this.completedAt = now;
    this.failureReason = null;
  }

  /** Một lần gọi thất bại; hết số lần thử thì chuyển Hoàn tiền thất bại. */
  public void recordFailure(String reason) {
    this.retryCount++;
    this.failureReason =
        reason.length() > MAX_REASON_LENGTH ? reason.substring(0, MAX_REASON_LENGTH) : reason;
    if (retryCount >= MAX_ATTEMPTS) {
      this.status = RefundStatus.FAILED;
    }
  }

  /** Quản trị viên đã tự hoàn tiền qua cổng thanh toán hoặc liên hệ khách (US-17). */
  public void completeManually(LocalDateTime now) {
    this.status = RefundStatus.COMPLETED;
    this.completedAt = now;
    this.failureReason = "Quản trị viên xác nhận đã hoàn tiền thủ công";
  }

  public Long getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public RefundStatus getStatus() {
    return status;
  }

  public long getAmount() {
    return amount;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public LocalDateTime getInitiatedAt() {
    return initiatedAt;
  }

  public LocalDateTime getCompletedAt() {
    return completedAt;
  }

  public String getFailureReason() {
    return failureReason;
  }
}
