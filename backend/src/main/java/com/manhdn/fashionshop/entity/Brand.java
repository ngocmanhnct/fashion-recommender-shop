package com.manhdn.fashionshop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Thương hiệu, bảng brand (dac-ta-yeu-cau.md Mục 3.3). */
@Entity
@Table(name = "brand")
public class Brand {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String name;

  /** Constructor rỗng cho JPA. */
  protected Brand() {}

  /** Tạo thương hiệu mới. */
  public Brand(String name) {
    this.name = name;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
