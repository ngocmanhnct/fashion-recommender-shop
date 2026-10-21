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

/**
 * Một dòng đơn hàng, bảng order_item (Mục 3.6). Tên sản phẩm, nhãn phân loại và đơn giá là bản sao
 * lúc đặt hàng (BR-15), nên đơn cũ vẫn hiển thị đúng khi sản phẩm đổi giá, đổi tên hay ngừng bán.
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "variant_id", nullable = false)
  private ProductVariant variant;

  @Column(nullable = false)
  private String productNameSnapshot;

  private String variantLabelSnapshot;

  @Column(nullable = false)
  private Long unitPriceSnapshot;

  @Column(nullable = false)
  private Integer quantity;

  @Column(nullable = false)
  private Long lineTotal;

  /** Constructor rỗng cho JPA. */
  protected OrderItem() {}

  /** Chép tên, nhãn phân loại và giá hiện tại của biến thể thành dòng đơn (BR-15). */
  public OrderItem(ProductVariant variant, int quantity) {
    this.variant = variant;
    this.productNameSnapshot = variant.getProduct().getName();
    this.variantLabelSnapshot = variant.label();
    this.unitPriceSnapshot = variant.getPrice();
    this.quantity = quantity;
    this.lineTotal = variant.getPrice() * quantity;
  }

  /** Gắn dòng vào đơn; chỉ {@link Order#addItem} gọi. */
  void attachTo(Order order) {
    this.order = order;
  }

  public Long getId() {
    return id;
  }

  public Order getOrder() {
    return order;
  }

  public ProductVariant getVariant() {
    return variant;
  }

  public String getProductNameSnapshot() {
    return productNameSnapshot;
  }

  public String getVariantLabelSnapshot() {
    return variantLabelSnapshot;
  }

  public long getUnitPriceSnapshot() {
    return unitPriceSnapshot;
  }

  public int getQuantity() {
    return quantity;
  }

  public long getLineTotal() {
    return lineTotal;
  }
}
