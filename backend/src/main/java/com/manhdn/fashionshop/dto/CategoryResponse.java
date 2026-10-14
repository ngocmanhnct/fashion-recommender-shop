package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Category;

/**
 * Danh mục trả về cho client.
 *
 * @param id mã danh mục
 * @param name tên danh mục
 */
public record CategoryResponse(Long id, String name) {

  /** Chuyển entity sang DTO. */
  public static CategoryResponse from(Category category) {
    return new CategoryResponse(category.getId(), category.getName());
  }
}
