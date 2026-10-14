package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.CategoryResponse;
import com.manhdn.fashionshop.dto.NameRequest;
import com.manhdn.fashionshop.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API quản lý danh mục, chỉ cho Quản trị viên (đường dẫn /api/admin, xem SecurityConfig). */
@RestController
@RequestMapping("/api/admin/categories")
public class CategoryAdminController {

  private final CategoryService categoryService;

  /** Nhận CategoryService qua constructor. */
  public CategoryAdminController(CategoryService categoryService) {
    this.categoryService = categoryService;
  }

  /**
   * Tạo danh mục.
   *
   * @param request tên danh mục
   * @return 201 kèm danh mục vừa tạo; 409 nếu trùng tên
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CategoryResponse create(@Valid @RequestBody NameRequest request) {
    return categoryService.create(request.name());
  }

  /**
   * Đổi tên danh mục.
   *
   * @param id mã danh mục
   * @param request tên mới
   * @return 200 kèm danh mục; 404 nếu không có; 409 nếu trùng tên
   */
  @PutMapping("/{id}")
  public CategoryResponse rename(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
    return categoryService.rename(id, request.name());
  }

  /**
   * Xóa danh mục không còn sản phẩm.
   *
   * @param id mã danh mục
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    categoryService.delete(id);
  }
}
