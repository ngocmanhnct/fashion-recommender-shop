package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.BrandResponse;
import com.manhdn.fashionshop.dto.NameRequest;
import com.manhdn.fashionshop.service.BrandService;
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

/** API quản lý thương hiệu, chỉ cho Quản trị viên (đường dẫn /api/admin, xem SecurityConfig). */
@RestController
@RequestMapping("/api/admin/brands")
public class BrandAdminController {

  private final BrandService brandService;

  /** Nhận BrandService qua constructor. */
  public BrandAdminController(BrandService brandService) {
    this.brandService = brandService;
  }

  /**
   * Tạo thương hiệu.
   *
   * @param request tên thương hiệu
   * @return 201 kèm thương hiệu vừa tạo; 409 nếu trùng tên
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BrandResponse create(@Valid @RequestBody NameRequest request) {
    return brandService.create(request.name());
  }

  /**
   * Đổi tên thương hiệu.
   *
   * @param id mã thương hiệu
   * @param request tên mới
   * @return 200 kèm thương hiệu; 404 nếu không có; 409 nếu trùng tên
   */
  @PutMapping("/{id}")
  public BrandResponse rename(@PathVariable Long id, @Valid @RequestBody NameRequest request) {
    return brandService.rename(id, request.name());
  }

  /**
   * Xóa thương hiệu không còn sản phẩm.
   *
   * @param id mã thương hiệu
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    brandService.delete(id);
  }
}
