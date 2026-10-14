package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.BrandResponse;
import com.manhdn.fashionshop.dto.CategoryResponse;
import com.manhdn.fashionshop.service.BrandService;
import com.manhdn.fashionshop.service.CategoryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API công khai đọc danh mục và thương hiệu, không cần đăng nhập: dùng cho bộ lọc trang danh sách
 * sản phẩm (US-01, US-02), form khảo sát sở thích (US-23) và form sản phẩm của trang quản trị.
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

  private final CategoryService categoryService;
  private final BrandService brandService;

  /** Nhận các service qua constructor. */
  public CatalogController(CategoryService categoryService, BrandService brandService) {
    this.categoryService = categoryService;
    this.brandService = brandService;
  }

  /**
   * Mọi danh mục, xếp theo tên.
   *
   * @return danh sách danh mục
   */
  @GetMapping("/categories")
  public List<CategoryResponse> categories() {
    return categoryService.findAll();
  }

  /**
   * Mọi thương hiệu, xếp theo tên.
   *
   * @return danh sách thương hiệu
   */
  @GetMapping("/brands")
  public List<BrandResponse> brands() {
    return brandService.findAll();
  }
}
