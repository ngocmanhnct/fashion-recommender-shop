package com.manhdn.fashionshop.entity;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;

/**
 * Đơn hàng, bảng orders (Mục 3.6; US-10, US-11, US-20). Tên bảng là "orders" vì ORDER là từ khóa
 * của SQL. Người nhận và địa chỉ là bản sao lúc đặt hàng (BR-29); address_id chỉ là tham chiếu tiện
 * lợi tới sổ địa chỉ, về NULL khi khách xóa địa chỉ đó.
 */
@Entity
@Table(name = "orders")
public class Order {

  /** Thời gian chờ khách thanh toán online trước khi đơn tự hủy (BR-34). */
  public static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(30);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PaymentMethod paymentMethod;

  @Enumerated(EnumType.STRING)
  private PaymentStatus paymentStatus;

  /** Mã tham chiếu của lần thanh toán VNPay thành công (vnp_TxnRef), null nếu chưa thanh toán. */
  private String paymentTxnRef;

  /** Mã giao dịch phía VNPay (vnp_TransactionNo) của lần thanh toán thành công. */
  private String paymentTransactionNo;

  private LocalDateTime paidAt;

  @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
  private Refund refund;

  @Column(nullable = false, updatable = false)
  private String idempotencyKey;

  private Long addressId;

  @Column(nullable = false)
  private String recipientNameSnapshot;

  @Column(nullable = false)
  private String phoneSnapshot;

  @Column(nullable = false)
  private String addressDetailSnapshot;

  /** Mã giảm giá đã áp dụng (BR-31: tối đa 1 mã), null nếu không dùng mã. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voucher_id")
  private Voucher voucher;

  @Column(nullable = false)
  private Long discountAmount;

  @Column(nullable = false)
  private Long subtotalAmount;

  @Column(nullable = false)
  private Long shippingFee;

  @Column(nullable = false)
  private Long totalAmount;

  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(nullable = false)
  private LocalDateTime updatedAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
  @OrderBy("id")
  @BatchSize(size = 50)
  private List<OrderItem> items = new ArrayList<>();

  /** Constructor rỗng cho JPA. */
  protected Order() {}

  /**
   * Tạo đơn hàng thanh toán khi nhận hàng, ở trạng thái Chờ xử lý (US-10), chưa có dòng nào.
   *
   * @param account tài khoản đặt hàng
   * @param idempotencyKey mã định danh lần bấm xác nhận (BR-10)
   * @param address địa chỉ được chọn, chép thành bản sao (BR-29)
   * @param paymentMethod phương thức thanh toán; ONLINE thì trạng thái thanh toán bắt đầu là
   *     PENDING, COD thì không có trạng thái thanh toán
   * @param shippingFee phí vận chuyển (BR-35)
   * @param createdAt thời điểm đặt hàng
   */
  public static Order create(
      Account account,
      String idempotencyKey,
      Address address,
      PaymentMethod paymentMethod,
      long shippingFee,
      LocalDateTime createdAt) {
    Order order = new Order();
    order.account = account;
    order.status = OrderStatus.PENDING;
    order.paymentMethod = paymentMethod;
    order.paymentStatus = paymentMethod == PaymentMethod.ONLINE ? PaymentStatus.PENDING : null;
    order.idempotencyKey = idempotencyKey;
    order.addressId = address.getId();
    order.recipientNameSnapshot = address.getRecipientName();
    order.phoneSnapshot = address.getPhone();
    order.addressDetailSnapshot = address.getAddressDetail();
    order.discountAmount = 0L;
    order.subtotalAmount = 0L;
    order.shippingFee = shippingFee;
    order.totalAmount = shippingFee;
    order.createdAt = createdAt;
    order.updatedAt = createdAt;
    return order;
  }

  /** Thêm một dòng đơn và cộng vào tổng tiền hàng, tổng thanh toán. */
  public void addItem(OrderItem item) {
    item.attachTo(this);
    items.add(item);
    subtotalAmount += item.getLineTotal();
    totalAmount = subtotalAmount + shippingFee - discountAmount;
  }

  /**
   * Áp mã giảm giá sau khi đã thêm đủ dòng đơn: số tiền giảm trừ vào tổng thanh toán (BR-32).
   *
   * @param voucher mã giảm giá
   * @param discount số tiền giảm thực nhận, đã tính mức sàn
   */
  public void applyVoucher(Voucher voucher, long discount) {
    this.voucher = voucher;
    this.discountAmount = discount;
    this.totalAmount = subtotalAmount + shippingFee - discountAmount;
  }

  /** Ghi nhận thanh toán VNPay thành công (US-10). */
  public void markPaid(String txnRef, String transactionNo, LocalDateTime now) {
    this.paymentStatus = PaymentStatus.PAID;
    this.paymentTxnRef = txnRef;
    this.paymentTransactionNo = transactionNo;
    this.paidAt = now;
    this.updatedAt = now;
  }

  /** Đơn ONLINE bị hủy khi chưa thanh toán: không còn chờ thanh toán nữa (BR-34). */
  public void markPaymentFailed() {
    this.paymentStatus = PaymentStatus.FAILED;
  }

  /** Đơn ONLINE còn chờ khách thanh toán: chưa hủy và chưa nhận được xác nhận từ VNPay. */
  public boolean awaitingPayment() {
    return paymentMethod == PaymentMethod.ONLINE
        && paymentStatus == PaymentStatus.PENDING
        && status != OrderStatus.CANCELLED;
  }

  /** Đơn đủ điều kiện để cửa hàng xử lý tiếp: COD, hoặc ONLINE đã thanh toán (US-17). */
  public boolean paymentSettledForProcessing() {
    return paymentMethod == PaymentMethod.COD || paymentStatus == PaymentStatus.PAID;
  }

  /** Chuyển sang Đã hủy. Việc hoàn tồn kho (BR-16) do service làm cùng giao dịch. */
  public void cancel(LocalDateTime now) {
    this.status = OrderStatus.CANCELLED;
    this.updatedAt = now;
  }

  /** Chuyển sang trạng thái kế tiếp do quản trị viên chọn (US-17); service kiểm tra hợp lệ. */
  public void advanceTo(OrderStatus next, LocalDateTime now) {
    this.status = next;
    this.updatedAt = now;
  }

  public Long getId() {
    return id;
  }

  public Account getAccount() {
    return account;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public PaymentMethod getPaymentMethod() {
    return paymentMethod;
  }

  public PaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getPaymentTxnRef() {
    return paymentTxnRef;
  }

  public String getPaymentTransactionNo() {
    return paymentTransactionNo;
  }

  public LocalDateTime getPaidAt() {
    return paidAt;
  }

  /** Gắn lần hoàn tiền vừa tạo, để đọc lại được trong cùng giao dịch. */
  void attachRefund(Refund refund) {
    this.refund = refund;
  }

  /** Lần hoàn tiền của đơn (BR-28), null nếu không có. */
  public Refund getRefund() {
    return refund;
  }

  /** Thời hạn khách phải thanh toán xong đơn ONLINE, quá hạn thì đơn tự hủy (BR-34). */
  public LocalDateTime paymentDeadline() {
    return createdAt.plus(PAYMENT_TIMEOUT);
  }

  public Long getAddressId() {
    return addressId;
  }

  public String getRecipientNameSnapshot() {
    return recipientNameSnapshot;
  }

  public String getPhoneSnapshot() {
    return phoneSnapshot;
  }

  public String getAddressDetailSnapshot() {
    return addressDetailSnapshot;
  }

  public Voucher getVoucher() {
    return voucher;
  }

  public long getDiscountAmount() {
    return discountAmount;
  }

  public long getSubtotalAmount() {
    return subtotalAmount;
  }

  public long getShippingFee() {
    return shippingFee;
  }

  public long getTotalAmount() {
    return totalAmount;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  public List<OrderItem> getItems() {
    return Collections.unmodifiableList(items);
  }
}
