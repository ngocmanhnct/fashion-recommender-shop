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

/**
 * Một liên kết đặt lại mật khẩu đã gửi qua email, bảng password_reset_token (migration V10, BR-48).
 * Chỉ lưu bản băm SHA-256 của chuỗi trong liên kết; dùng được 1 lần và hết hạn sau 30 phút.
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(nullable = false, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  private LocalDateTime usedAt;

  /** Constructor rỗng cho JPA. */
  protected PasswordResetToken() {}

  /** Liên kết mới của tài khoản, tạo lúc createdAt, hết hạn lúc expiresAt. */
  public PasswordResetToken(
      Account account, String tokenHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
    this.account = account;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** Còn dùng được lúc now: chưa dùng và chưa hết hạn. */
  public boolean isUsableAt(LocalDateTime now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public LocalDateTime getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(LocalDateTime usedAt) {
    this.usedAt = usedAt;
  }
}
