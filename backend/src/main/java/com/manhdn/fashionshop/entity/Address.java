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
 * Một địa chỉ trong sổ địa chỉ của khách hàng, bảng address (Mục 3.2; US-09). Chỉ để chọn nhanh khi
 * đặt hàng: đơn hàng lưu bản sao người nhận và địa chỉ riêng (BR-29).
 */
@Entity
@Table(name = "address")
public class Address {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(nullable = false)
  private String recipientName;

  @Column(nullable = false)
  private String phone;

  @Column(nullable = false)
  private String addressDetail;

  @Column(nullable = false)
  private boolean isDefault;

  @Column(nullable = false)
  private LocalDateTime createdAt;

  /** Constructor rỗng cho JPA. */
  protected Address() {}

  /** Tạo địa chỉ mới, chưa là địa chỉ mặc định. */
  public Address(
      Account account,
      String recipientName,
      String phone,
      String addressDetail,
      LocalDateTime createdAt) {
    this.account = account;
    this.createdAt = createdAt;
    update(recipientName, phone, addressDetail);
  }

  /** Sửa người nhận, số điện thoại và địa chỉ chi tiết. */
  public void update(String recipientName, String phone, String addressDetail) {
    this.recipientName = recipientName;
    this.phone = phone;
    this.addressDetail = addressDetail;
  }

  /** Đặt hoặc bỏ làm địa chỉ mặc định. */
  public void setDefault(boolean isDefault) {
    this.isDefault = isDefault;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public String getRecipientName() {
    return recipientName;
  }

  public String getPhone() {
    return phone;
  }

  public String getAddressDetail() {
    return addressDetail;
  }

  public boolean isDefault() {
    return isDefault;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }
}
