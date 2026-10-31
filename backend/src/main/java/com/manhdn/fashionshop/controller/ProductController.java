package com.manhdn.fashionshop.controller;

import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.ProductDetailResponse;
import com.manhdn.fashionshop.dto.ProductSearchCriteria;
import com.manhdn.fashionshop.dto.ProductSort;
import com.manhdn.fashionshop.dto.RelatedProductResponse;
import com.manhdn.fashionshop.service.ProductCatalogService;
import com.manhdn.fashionshop.service.ProductViewService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API sản phẩm cho trang người dùng (US-01, US-02, US-03, US-12). Các API GET công khai, không cần
 * đăng nhập; ghi lượt xem chỉ cho tài khoản Khách hàng (xem SecurityConfig).
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private final ProductCatalogService catalogService;
  private final ProductViewService viewService;

  /** Nhận các service qua constructor. */
  public ProductController(ProductCatalogService catalogService, ProductViewService viewService) {
    this.catalogService = catalogService;
    this.viewService = viewService;
  }

  /**
   * Danh sách sản phẩm đang bán: tìm theo từ khóa, lọc, sắp xếp, phân trang.
   *
   * @param keyword từ khóa, khớp đầu từ trong tên sản phẩm, danh mục, thương hiệu (không phân biệt
   *     hoa thường và dấu), tối đa 100 ký tự, tùy chọn
   * @param categoryId lọc theo danh mục, tùy chọn
   * @param brandId lọc theo thương hiệu, tùy chọn
   * @param minPrice giá thấp nhất (VNĐ), tùy chọn: sản phẩm có ít nhất 1 biến thể trong khoảng giá
   * @param maxPrice giá cao nhất (VNĐ), tùy chọn
   * @param minRating điểm đánh giá trung bình tối thiểu (1 đến 5), tùy chọn
   * @param sort POPULAR (mặc định), NEWEST, PRICE_ASC hoặc PRICE_DESC
   * @param page số trang từ 0 (mặc định 0, tối đa 10000)
   * @param size số sản phẩm mỗi trang (mặc định 20, tối đa 48)
   * @return 200 kèm một trang thẻ sản phẩm; 400 nếu tham số không hợp lệ
   */
  @GetMapping
  public PageResponse<ProductCardResponse> search(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Long brandId,
      @RequestParam(required = false) Long minPrice,
      @RequestParam(required = false) Long maxPrice,
      @RequestParam(required = false) Integer minRating,
      @RequestParam(defaultValue = "POPULAR") ProductSort sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return catalogService.search(
        new ProductSearchCriteria(
            keyword, categoryId, brandId, minPrice, maxPrice, minRating, sort, page, size));
  }

  /**
   * Gợi ý khi tìm theo từ khóa không thấy kết quả: tối đa 4 sản phẩm còn hàng khớp một phần từ
   * khóa, hoặc sản phẩm bán chạy nếu không có sản phẩm nào khớp.
   *
   * @param keyword từ khóa đã tìm, tùy chọn
   * @return 200 kèm tối đa 4 thẻ sản phẩm; 400 nếu từ khóa dài quá 100 ký tự
   */
  @GetMapping("/suggestions")
  public List<ProductCardResponse> suggestions(@RequestParam(required = false) String keyword) {
    return catalogService.suggestions(keyword);
  }

  /**
   * Chi tiết sản phẩm đang bán.
   *
   * @param id mã sản phẩm
   * @return 200 kèm chi tiết, biến thể và tồn kho; 404 nếu không có hoặc đã ngừng bán
   */
  @GetMapping("/{id}")
  public ProductDetailResponse get(@PathVariable Long id) {
    return catalogService.getDetail(id);
  }

  /**
   * "Thường được mua cùng" các sản phẩm trong giỏ hàng (chức năng 20), công khai vì khách chưa đăng
   * nhập cũng có giỏ trong trình duyệt.
   *
   * @param productIds mã các sản phẩm trong giỏ, từ 1 đến 50 mã, vd {@code ?productIds=1,2}
   * @return 200 kèm tối đa 4 sản phẩm còn hàng và lý do (rỗng nếu chưa ai mua cùng); 400 nếu thiếu
   *     mã hoặc quá 50 mã
   */
  @GetMapping("/bought-together")
  public List<RelatedProductResponse> boughtTogether(
      @RequestParam(required = false) List<Long> productIds) {
    return catalogService.boughtTogether(productIds);
  }

  /**
   * Gợi ý sản phẩm liên quan: tối đa 4 sản phẩm còn hàng, mỗi sản phẩm kèm lý do.
   *
   * @param id mã sản phẩm đang xem
   * @return 200 kèm danh sách gợi ý; 404 nếu sản phẩm không có hoặc đã ngừng bán
   */
  @GetMapping("/{id}/related")
  public List<RelatedProductResponse> related(@PathVariable Long id) {
    return catalogService.related(id);
  }

  /**
   * Ghi lượt xem sản phẩm của Khách hàng đang đăng nhập (US-03), dùng cho ngưỡng BR-19 và gợi ý cá
   * nhân hóa. Trả 204; 401 nếu chưa đăng nhập; 403 nếu không phải Khách hàng; 404 nếu sản phẩm
   * không có, đã ngừng bán, hoặc tài khoản không còn tồn tại.
   *
   * @param id mã sản phẩm đang xem
   * @param jwt token của Khách hàng, claim sub là mã tài khoản
   */
  @PostMapping("/{id}/views")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recordView(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
    viewService.recordView(Long.parseLong(jwt.getSubject()), id);
  }
}
