package com.manhdn.fashionshop.service;

import static com.manhdn.fashionshop.repository.ProductSpecifications.forSale;
import static com.manhdn.fashionshop.repository.ProductSpecifications.hasVariantPriceBetween;
import static com.manhdn.fashionshop.repository.ProductSpecifications.idNotIn;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inCategory;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inStock;
import static com.manhdn.fashionshop.repository.ProductSpecifications.matchesAllTokens;
import static com.manhdn.fashionshop.repository.ProductSpecifications.notInCategory;
import static com.manhdn.fashionshop.repository.ProductSpecifications.notOfBrand;
import static com.manhdn.fashionshop.repository.ProductSpecifications.ofBrand;
import static com.manhdn.fashionshop.repository.ProductSpecifications.ratingAtLeast;

import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.ProductDetailResponse;
import com.manhdn.fashionshop.dto.ProductSearchCriteria;
import com.manhdn.fashionshop.dto.ProductSort;
import com.manhdn.fashionshop.dto.RelatedProductResponse;
import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ProductSpecifications;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ xem sản phẩm của trang người dùng, không cần đăng nhập (US-01, US-02, US-03, US-12;
 * BR-21, BR-22). Chỉ đọc; chỉ trả sản phẩm đang bán (chưa xóa mềm).
 *
 * <p>"Bán chạy" và "phổ biến" cùng một nghĩa: tổng số lượng đã bán trong các đơn chưa bị hủy
 * ({@link Product#getSoldCount}). Khi số đã bán bằng nhau (vd chưa có đơn nào), sản phẩm đứng đầu
 * mỗi danh mục xếp trước, rồi tới sản phẩm thứ hai của mỗi danh mục..., nên danh sách xen kẽ các
 * danh mục thay vì chỉ toàn sản phẩm của danh mục được thêm đầu tiên.
 */
@Service
@Transactional(readOnly = true)
public class ProductCatalogService {

  /** Độ dài tối đa của từ khóa tìm kiếm. */
  public static final int MAX_KEYWORD_LENGTH = 100;

  /** Số trang lớn nhất được phép (tránh vượt giới hạn offset của truy vấn). */
  public static final int MAX_PAGE = 10_000;

  /** Số sản phẩm tối đa mỗi trang. */
  public static final int MAX_PAGE_SIZE = 48;

  /** Số sản phẩm gợi ý khi tìm không thấy kết quả (US-02). */
  public static final int SUGGESTION_LIMIT = 4;

  /** Số sản phẩm gợi ý liên quan trên trang chi tiết, cố định là 4 (BR-21). */
  public static final int RELATED_LIMIT = 4;

  static final String SAME_CATEGORY_AND_BRAND =
      "Cùng danh mục và thương hiệu với sản phẩm bạn đang xem";
  static final String SAME_CATEGORY = "Cùng danh mục với sản phẩm bạn đang xem";
  static final String SAME_BRAND = "Cùng thương hiệu với sản phẩm bạn đang xem";
  static final String BEST_SELLER = "Sản phẩm bán chạy";

  private static final String ID = "id";
  private static final String MIN_PRICE = "minPrice";

  /** Thứ tự "phổ biến": bán chạy trước, hòa thì theo vị trí trong danh mục, rồi theo id. */
  static final Sort POPULAR_ORDER =
      Sort.by(Order.desc("soldCount"), Order.asc("categoryRank"), Order.asc(ID));

  private final ProductRepository productRepository;

  /** Nhận repository qua constructor. */
  public ProductCatalogService(ProductRepository productRepository) {
    this.productRepository = productRepository;
  }

  /**
   * Tìm sản phẩm đang bán theo từ khóa và bộ lọc, có phân trang (US-01, US-02).
   *
   * <p>Từ khóa được tách thành các từ; mỗi từ phải khớp đầu một từ trong tên sản phẩm, tên danh mục
   * hoặc tên thương hiệu, không phân biệt hoa thường và dấu (xem {@link ProductSpecifications}). Từ
   * khóa chỉ có ký tự đặc biệt không khớp sản phẩm nào. Lọc theo giá là có ít nhất 1 biến thể giá
   * nằm trong khoảng; lọc theo đánh giá loại sản phẩm chưa có đánh giá.
   *
   * @param criteria từ khóa, bộ lọc, cách sắp xếp và trang
   * @return một trang thẻ sản phẩm
   * @throws InvalidRequestException nếu từ khóa dài quá 100 ký tự, giá âm hoặc giá thấp nhất lớn
   *     hơn giá cao nhất, mức đánh giá ngoài 1 đến 5, số trang ngoài 0 đến 10000, hoặc số sản phẩm
   *     mỗi trang ngoài 1 đến 48
   */
  public PageResponse<ProductCardResponse> search(ProductSearchCriteria criteria) {
    validate(criteria);
    PageRequest pageable =
        PageRequest.of(criteria.page(), criteria.size(), sortOf(criteria.sort()));
    return PageResponse.from(
        productRepository.findAll(toSpecification(criteria), pageable), ProductCardResponse::from);
  }

  /**
   * Gợi ý khi tìm theo từ khóa không thấy kết quả (US-02 "kèm gợi ý liên quan"): tối đa 4 sản phẩm
   * đang bán, còn hàng, khớp ít nhất 1 từ của từ khóa, khớp nhiều từ hơn đứng trước. Không có từ
   * nào hoặc không sản phẩm nào khớp thì trả sản phẩm bán chạy còn hàng (như BR-22).
   *
   * @param keyword từ khóa đã tìm, có thể trống
   * @return tối đa 4 thẻ sản phẩm
   * @throws InvalidRequestException nếu từ khóa dài quá 100 ký tự
   */
  public List<ProductCardResponse> suggestions(String keyword) {
    validateKeyword(keyword);
    List<String> tokens = ProductSpecifications.tokenize(keyword);
    List<Product> products = productRepository.findSuggestions(tokens, SUGGESTION_LIMIT);
    if (products.isEmpty()) {
      products = bestSellers(Set.of(), Specification.unrestricted(), SUGGESTION_LIMIT);
    }
    return products.stream().map(ProductCardResponse::from).toList();
  }

  /**
   * Chi tiết một sản phẩm đang bán (US-03).
   *
   * @param id mã sản phẩm
   * @return chi tiết kèm biến thể, giá, tồn kho
   * @throws NoSuchElementException nếu không có sản phẩm hoặc sản phẩm đã ngừng bán
   */
  public ProductDetailResponse getDetail(Long id) {
    return ProductDetailResponse.from(getForSale(id));
  }

  /**
   * Gợi ý sản phẩm liên quan trên trang chi tiết (US-12): tối đa 4 sản phẩm (BR-21) đang bán, còn
   * hàng, không gồm chính sản phẩm đang xem, mỗi sản phẩm kèm 1 lý do. Lấy lần lượt theo các tầng
   * cho đến khi đủ 4, mỗi tầng xếp theo thứ tự "phổ biến": (1) cùng danh mục và cùng thương hiệu,
   * (2) cùng danh mục, thương hiệu khác hoặc không có, (3) cùng thương hiệu, danh mục khác, (4) sản
   * phẩm bán chạy (BR-22). Sản phẩm không có thương hiệu thì bỏ tầng 1 và 3.
   *
   * <p>Đây là bản tạm theo quy tắc. Từ Tuần 8, các tầng 1 đến 3 được thay bằng content-based
   * filtering của dịch vụ học máy; tầng 4 (BR-22) giữ nguyên.
   *
   * @param id mã sản phẩm đang xem
   * @return tối đa 4 sản phẩm kèm lý do
   * @throws NoSuchElementException nếu không có sản phẩm hoặc sản phẩm đã ngừng bán
   */
  public List<RelatedProductResponse> related(Long id) {
    Product product = getForSale(id);
    Long categoryId = product.getCategory().getId();
    Brand brand = product.getBrand();
    List<RelatedProductResponse> result = new ArrayList<>();
    Set<Long> chosen = new HashSet<>(Set.of(product.getId()));
    if (brand != null) {
      addTier(
          result,
          chosen,
          inCategory(categoryId).and(ofBrand(brand.getId())),
          SAME_CATEGORY_AND_BRAND);
      addTier(result, chosen, inCategory(categoryId).and(notOfBrand(brand.getId())), SAME_CATEGORY);
      addTier(result, chosen, ofBrand(brand.getId()).and(notInCategory(categoryId)), SAME_BRAND);
    } else {
      addTier(result, chosen, inCategory(categoryId), SAME_CATEGORY);
    }
    addTier(result, chosen, Specification.unrestricted(), BEST_SELLER);
    return result;
  }

  /** Thêm sản phẩm của một tầng gợi ý liên quan cho đến khi đủ {@link #RELATED_LIMIT}. */
  private void addTier(
      List<RelatedProductResponse> result,
      Set<Long> chosen,
      Specification<Product> tier,
      String reason) {
    int missing = RELATED_LIMIT - result.size();
    if (missing <= 0) {
      return;
    }
    for (Product p : bestSellers(chosen, tier, missing)) {
      chosen.add(p.getId());
      result.add(new RelatedProductResponse(ProductCardResponse.from(p), reason));
    }
  }

  /** Sản phẩm đang bán, còn hàng, thỏa điều kiện, theo thứ tự "phổ biến", trừ các mã cho trước. */
  private List<Product> bestSellers(
      Set<Long> excludedIds, Specification<Product> filter, int limit) {
    Specification<Product> spec =
        forSale().and(inStock()).and(idNotIn(Set.copyOf(excludedIds))).and(filter);
    return productRepository.findBy(spec, q -> q.sortBy(POPULAR_ORDER).limit(limit).all());
  }

  private Product getForSale(Long id) {
    return productRepository
        .findById(id)
        .filter(p -> !p.isDeleted())
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sản phẩm"));
  }

  private static Specification<Product> toSpecification(ProductSearchCriteria c) {
    Specification<Product> spec = forSale();
    if (c.keyword() != null && !c.keyword().isBlank()) {
      spec = spec.and(matchesAllTokens(ProductSpecifications.tokenize(c.keyword())));
    }
    if (c.categoryId() != null) {
      spec = spec.and(inCategory(c.categoryId()));
    }
    if (c.brandId() != null) {
      spec = spec.and(ofBrand(c.brandId()));
    }
    if (c.minPrice() != null || c.maxPrice() != null) {
      spec = spec.and(hasVariantPriceBetween(c.minPrice(), c.maxPrice()));
    }
    if (c.minRating() != null) {
      spec = spec.and(ratingAtLeast(c.minRating()));
    }
    return spec;
  }

  private static Sort sortOf(ProductSort sort) {
    return switch (sort) {
      case POPULAR -> POPULAR_ORDER;
      case NEWEST -> Sort.by(Order.desc(ID));
      case PRICE_ASC -> Sort.by(Order.asc(MIN_PRICE), Order.asc(ID));
      case PRICE_DESC -> Sort.by(Order.desc(MIN_PRICE), Order.asc(ID));
    };
  }

  private static void validate(ProductSearchCriteria c) {
    validateKeyword(c.keyword());
    validatePrices(c.minPrice(), c.maxPrice());
    if (c.minRating() != null && (c.minRating() < 1 || c.minRating() > 5)) {
      throw new InvalidRequestException("Mức đánh giá phải từ 1 đến 5 sao");
    }
    if (c.page() < 0 || c.page() > MAX_PAGE) {
      throw new InvalidRequestException("Số trang phải từ 0 đến %d".formatted(MAX_PAGE));
    }
    if (c.size() < 1 || c.size() > MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Số sản phẩm mỗi trang phải từ 1 đến %d".formatted(MAX_PAGE_SIZE));
    }
  }

  private static void validateKeyword(String keyword) {
    if (keyword != null && keyword.length() > MAX_KEYWORD_LENGTH) {
      throw new InvalidRequestException("Từ khóa tối đa %d ký tự".formatted(MAX_KEYWORD_LENGTH));
    }
  }

  private static void validatePrices(Long minPrice, Long maxPrice) {
    if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)) {
      throw new InvalidRequestException("Giá không được âm");
    }
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new InvalidRequestException("Giá thấp nhất không được lớn hơn giá cao nhất");
    }
  }
}
