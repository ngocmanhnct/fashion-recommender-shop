package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.ProductSummaryResponse;
import com.manhdn.fashionshop.dto.ProductUpdateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.dto.VariantResponse;
import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductVariant;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ProductVariantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ quản lý sản phẩm của Quản trị viên (US-16, QĐ_QTV1; BR-05, BR-11, BR-36).
 *
 * <p>Quy tắc chính: sản phẩm luôn có ít nhất 1 biến thể; trong một sản phẩm không có 2 biến thể
 * trùng tổ hợp size × màu; mã SKU (nếu có) là duy nhất toàn hệ thống; xóa sản phẩm là xóa mềm.
 */
@Service
public class ProductService {

  private static final String SKU_TAKEN = "Mã SKU %s đã được dùng cho biến thể khác";
  private static final String SKU_IN_USE = "Mã SKU đã được dùng cho biến thể khác";

  private final ProductRepository productRepository;
  private final ProductVariantRepository variantRepository;
  private final CategoryService categoryService;
  private final BrandService brandService;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public ProductService(
      ProductRepository productRepository,
      ProductVariantRepository variantRepository,
      CategoryService categoryService,
      BrandService brandService,
      Clock clock) {
    this.productRepository = productRepository;
    this.variantRepository = variantRepository;
    this.categoryService = categoryService;
    this.brandService = brandService;
    this.clock = clock;
  }

  /**
   * Tìm sản phẩm cho trang quản trị.
   *
   * @param keyword từ khóa trong tên, null hoặc rỗng = không lọc
   * @param categoryId lọc theo danh mục, null = mọi danh mục
   * @param includeDeleted có lấy cả sản phẩm đã xóa mềm không
   * @param pageable trang và cách sắp xếp
   * @return một trang sản phẩm dạng tóm tắt
   */
  @Transactional(readOnly = true)
  public PageResponse<ProductSummaryResponse> search(
      String keyword, Long categoryId, boolean includeDeleted, Pageable pageable) {
    String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
    return PageResponse.from(
        productRepository.searchForAdmin(normalized, categoryId, includeDeleted, pageable),
        ProductSummaryResponse::from);
  }

  /**
   * Chi tiết một sản phẩm, kể cả sản phẩm đã xóa mềm.
   *
   * @param id mã sản phẩm
   * @return chi tiết sản phẩm
   * @throws NoSuchElementException nếu không có sản phẩm
   */
  @Transactional(readOnly = true)
  public ProductResponse get(Long id) {
    return ProductResponse.from(getEntity(id));
  }

  /**
   * Thêm sản phẩm kèm biến thể và ảnh (QĐ_QTV1 ý 1).
   *
   * @param request thông tin chung và ít nhất 1 biến thể
   * @return sản phẩm vừa tạo
   * @throws NoSuchElementException nếu danh mục hoặc thương hiệu không tồn tại
   * @throws ConflictException nếu trùng tổ hợp size × màu hoặc trùng mã SKU
   */
  @Transactional
  public ProductResponse create(ProductCreateRequest request) {
    Category category = categoryService.getEntity(request.categoryId());
    Brand brand = request.brandId() == null ? null : brandService.getEntity(request.brandId());
    checkNoDuplicateInRequest(request.variants());

    Product product =
        new Product(category, brand, request.name().trim(), blankToNull(request.description()));
    product.replaceImages(request.imageUrls() == null ? List.of() : request.imageUrls());
    for (VariantRequest v : request.variants()) {
      String sku = normalizeSku(v.skuCode());
      if (sku != null && variantRepository.existsBySkuCode(sku)) {
        throw new ConflictException(SKU_TAKEN.formatted(sku));
      }
      product.addVariant(v.size().trim(), v.color().trim(), v.price(), v.stock(), sku);
    }
    return ProductResponse.from(saveAndFlush(product));
  }

  /**
   * Sửa thông tin chung và thay toàn bộ ảnh. Sản phẩm đã ngừng bán thì không sửa được.
   *
   * @param id mã sản phẩm
   * @param request thông tin mới
   * @return sản phẩm sau khi sửa
   * @throws NoSuchElementException nếu sản phẩm, danh mục hoặc thương hiệu không tồn tại
   * @throws ConflictException nếu sản phẩm đã bị xóa mềm
   */
  @Transactional
  public ProductResponse update(Long id, ProductUpdateRequest request) {
    Product product = getActiveEntity(id);
    Category category = categoryService.getEntity(request.categoryId());
    Brand brand = request.brandId() == null ? null : brandService.getEntity(request.brandId());
    product.updateInfo(category, brand, request.name().trim(), blankToNull(request.description()));
    product.replaceImages(request.imageUrls() == null ? List.of() : request.imageUrls());
    flush();
    return ProductResponse.from(product);
  }

  /**
   * Xóa mềm sản phẩm (US-16 luồng 2a): đánh dấu thời điểm ngừng bán và đặt mã SKU của mọi biến thể
   * về NULL (BR-36). Gọi lại trên sản phẩm đã xóa thì không làm gì. Việc loại sản phẩm khỏi giỏ
   * hàng của khách (BR-11) do phần giỏ hàng xử lý khi đọc giỏ.
   *
   * @param id mã sản phẩm
   * @throws NoSuchElementException nếu không có sản phẩm
   */
  @Transactional
  public void delete(Long id) {
    Product product = getEntity(id);
    if (!product.isDeleted()) {
      product.softDelete(LocalDateTime.now(clock));
    }
  }

  /**
   * Thêm biến thể vào sản phẩm đang bán.
   *
   * @param productId mã sản phẩm
   * @param request dữ liệu biến thể
   * @return biến thể vừa tạo
   * @throws ConflictException nếu sản phẩm đã ngừng bán, trùng size × màu hoặc trùng mã SKU
   */
  @Transactional
  public VariantResponse addVariant(Long productId, VariantRequest request) {
    Product product = getActiveEntity(productId);
    checkOptionFree(product, request, null);
    String sku = normalizeSku(request.skuCode());
    if (sku != null && variantRepository.existsBySkuCode(sku)) {
      throw new ConflictException(SKU_TAKEN.formatted(sku));
    }
    ProductVariant variant =
        product.addVariant(
            request.size().trim(), request.color().trim(), request.price(), request.stock(), sku);
    flush();
    return VariantResponse.from(variant);
  }

  /**
   * Sửa biến thể: size, màu, giá, tồn kho, mã SKU.
   *
   * <p>Tồn kho đặt bằng giá trị admin nhập (kiểm kê). Đơn hàng trừ kho bằng UPDATE có điều kiện
   * (BR-08) nên không bao giờ âm, nhưng nếu admin lưu đúng lúc có đơn vừa trừ kho thì giá trị admin
   * nhập sẽ ghi đè; chấp nhận được với quy mô cửa hàng một người quản trị.
   *
   * @param productId mã sản phẩm
   * @param variantId mã biến thể
   * @param request dữ liệu mới
   * @return biến thể sau khi sửa
   * @throws NoSuchElementException nếu biến thể không thuộc sản phẩm
   * @throws ConflictException nếu sản phẩm đã ngừng bán, trùng size × màu hoặc trùng mã SKU
   */
  @Transactional
  public VariantResponse updateVariant(Long productId, Long variantId, VariantRequest request) {
    Product product = getActiveEntity(productId);
    ProductVariant variant = findVariant(product, variantId);
    checkOptionFree(product, request, variantId);
    String sku = normalizeSku(request.skuCode());
    if (sku != null && variantRepository.existsBySkuCodeAndIdNot(sku, variantId)) {
      throw new ConflictException(SKU_TAKEN.formatted(sku));
    }
    variant.update(
        request.size().trim(), request.color().trim(), request.price(), request.stock(), sku);
    flush();
    return VariantResponse.from(variant);
  }

  /**
   * Xóa hẳn một biến thể. Không xóa được biến thể cuối cùng (sản phẩm luôn có ít nhất 1 biến thể),
   * và không xóa được biến thể đã nằm trong giỏ hàng hoặc đơn hàng (khóa ngoại chặn); khi đó admin
   * đặt tồn kho về 0 để ngừng bán biến thể đó.
   *
   * @param productId mã sản phẩm
   * @param variantId mã biến thể
   * @throws ConflictException nếu là biến thể cuối cùng hoặc đã được tham chiếu
   */
  @Transactional
  public void deleteVariant(Long productId, Long variantId) {
    Product product = getActiveEntity(productId);
    ProductVariant variant = findVariant(product, variantId);
    if (product.getVariants().size() == 1) {
      throw new ConflictException("Sản phẩm phải còn ít nhất 1 biến thể");
    }
    product.getVariants().remove(variant);
    try {
      variantRepository.delete(variant);
      variantRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(
          "Biến thể đã có trong giỏ hàng hoặc đơn hàng, không xóa được. Hãy đặt tồn kho về 0");
    }
  }

  private Product getEntity(Long id) {
    return productRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sản phẩm"));
  }

  private Product getActiveEntity(Long id) {
    Product product = getEntity(id);
    if (product.isDeleted()) {
      throw new ConflictException("Sản phẩm đã ngừng bán, không sửa được");
    }
    return product;
  }

  private static ProductVariant findVariant(Product product, Long variantId) {
    return product.getVariants().stream()
        .filter(v -> v.getId().equals(variantId))
        .findFirst()
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy biến thể của sản phẩm"));
  }

  /** Lưu sản phẩm mới; vi phạm UNIQUE của sku_code (2 request đồng thời) thành 409. */
  private Product saveAndFlush(Product product) {
    try {
      return productRepository.saveAndFlush(product);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(SKU_IN_USE);
    }
  }

  /**
   * Ghi thay đổi của sản phẩm đang được quản lý trong transaction xuống CSDL ngay, để vi phạm
   * UNIQUE thành 409 và biến thể mới có id trước khi trả về.
   */
  private void flush() {
    try {
      productRepository.flush();
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(SKU_IN_USE);
    }
  }

  /** Trong một request không có 2 biến thể trùng size × màu hoặc trùng mã SKU. */
  private static void checkNoDuplicateInRequest(List<VariantRequest> variants) {
    Set<String> options = new HashSet<>();
    Set<String> skus = new HashSet<>();
    for (VariantRequest v : variants) {
      String option =
          v.size().trim().toLowerCase(Locale.ROOT)
              + "|"
              + v.color().trim().toLowerCase(Locale.ROOT);
      if (!options.add(option)) {
        throw new ConflictException(
            "Trùng biến thể size %s, màu %s".formatted(v.size().trim(), v.color().trim()));
      }
      String sku = normalizeSku(v.skuCode());
      if (sku != null && !skus.add(sku)) {
        throw new ConflictException("Trùng mã SKU %s".formatted(sku));
      }
    }
  }

  /** Sản phẩm chưa có biến thể nào (ngoài biến thể đang sửa) cùng size × màu. */
  private static void checkOptionFree(Product product, VariantRequest request, Long exceptId) {
    String size = request.size().trim();
    String color = request.color().trim();
    boolean taken =
        product.getVariants().stream()
            .filter(v -> !v.getId().equals(exceptId))
            .anyMatch(v -> v.sameOptionAs(size, color));
    if (taken) {
      throw new ConflictException("Sản phẩm đã có biến thể size %s, màu %s".formatted(size, color));
    }
  }

  private static String normalizeSku(String sku) {
    return sku == null || sku.isBlank() ? null : sku.trim();
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
