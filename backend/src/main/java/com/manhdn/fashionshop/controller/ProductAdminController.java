package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.ProductSummaryResponse;
import com.manhdn.fashionshop.dto.ProductUpdateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.dto.VariantResponse;
import com.manhdn.fashionshop.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API quản lý sản phẩm và biến thể (US-16), chỉ cho Quản trị viên. */
@RestController
@RequestMapping("/api/admin/products")
public class ProductAdminController {

  private final ProductService productService;

  /** Nhận ProductService qua constructor. */
  public ProductAdminController(ProductService productService) {
    this.productService = productService;
  }

  /**
   * Danh sách sản phẩm có phân trang, mới nhất trước.
   *
   * @param keyword từ khóa trong tên, tùy chọn
   * @param categoryId lọc theo danh mục, tùy chọn
   * @param includeDeleted có hiện cả sản phẩm đã ngừng bán không, mặc định không
   * @param pageable tham số page (từ 0), size (mặc định 20), sort
   * @return một trang sản phẩm dạng tóm tắt
   */
  @GetMapping
  public PageResponse<ProductSummaryResponse> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(defaultValue = "false") boolean includeDeleted,
      @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
    return productService.search(keyword, categoryId, includeDeleted, pageable);
  }

  /**
   * Chi tiết sản phẩm.
   *
   * @param id mã sản phẩm
   * @return 200 kèm chi tiết; 404 nếu không có
   */
  @GetMapping("/{id}")
  public ProductResponse get(@PathVariable Long id) {
    return productService.get(id);
  }

  /**
   * Thêm sản phẩm kèm ít nhất 1 biến thể.
   *
   * @param request thông tin chung, ảnh và biến thể
   * @return 201 kèm sản phẩm; 400 nếu dữ liệu sai; 404 nếu danh mục/thương hiệu không tồn tại; 409
   *     nếu trùng biến thể hoặc mã SKU
   */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
    return productService.create(request);
  }

  /**
   * Sửa thông tin chung và ảnh.
   *
   * @param id mã sản phẩm
   * @param request thông tin mới
   * @return 200 kèm sản phẩm; 409 nếu sản phẩm đã ngừng bán
   */
  @PutMapping("/{id}")
  public ProductResponse update(
      @PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
    return productService.update(id, request);
  }

  /**
   * Xóa mềm sản phẩm (ngừng bán), giải phóng mã SKU của các biến thể.
   *
   * @param id mã sản phẩm
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    productService.delete(id);
  }

  /**
   * Thêm biến thể.
   *
   * @param id mã sản phẩm
   * @param request dữ liệu biến thể
   * @return 201 kèm biến thể; 409 nếu trùng size × màu hoặc mã SKU
   */
  @PostMapping("/{id}/variants")
  @ResponseStatus(HttpStatus.CREATED)
  public VariantResponse addVariant(
      @PathVariable Long id, @Valid @RequestBody VariantRequest request) {
    return productService.addVariant(id, request);
  }

  /**
   * Sửa biến thể.
   *
   * @param id mã sản phẩm
   * @param variantId mã biến thể
   * @param request dữ liệu mới
   * @return 200 kèm biến thể; 404 nếu biến thể không thuộc sản phẩm; 409 nếu trùng
   */
  @PutMapping("/{id}/variants/{variantId}")
  public VariantResponse updateVariant(
      @PathVariable Long id,
      @PathVariable Long variantId,
      @Valid @RequestBody VariantRequest request) {
    return productService.updateVariant(id, variantId, request);
  }

  /**
   * Xóa biến thể chưa từng vào giỏ hàng hay đơn hàng, và không phải biến thể cuối cùng.
   *
   * @param id mã sản phẩm
   * @param variantId mã biến thể
   */
  @DeleteMapping("/{id}/variants/{variantId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteVariant(@PathVariable Long id, @PathVariable Long variantId) {
    productService.deleteVariant(id, variantId);
  }
}
