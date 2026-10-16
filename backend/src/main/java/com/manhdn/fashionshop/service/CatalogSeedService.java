package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CatalogSeed;
import com.manhdn.fashionshop.dto.CatalogSeed.SeedProduct;
import com.manhdn.fashionshop.dto.NameRequest;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.BrandRepository;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Nạp danh mục sản phẩm mẫu (dữ liệu Tiki đã chọn lọc) vào cửa hàng.
 *
 * <p>Mỗi sản phẩm được kiểm tra như dữ liệu gửi lên API quản trị (Bean Validation) rồi tạo qua
 * {@link ProductService#create}, nên chịu đúng các quy tắc như khi Quản trị viên tự thêm (BR-05,
 * BR-11). Cả lần nạp nằm trong 1 giao dịch: một sản phẩm sai thì không sản phẩm nào được lưu.
 */
@Service
public class CatalogSeedService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;
  private final BrandRepository brandRepository;
  private final ProductService productService;
  private final Validator validator;
  private final JsonMapper jsonMapper;

  /** Nhận các thành phần cần thiết qua constructor. */
  public CatalogSeedService(
      ProductRepository productRepository,
      CategoryRepository categoryRepository,
      BrandRepository brandRepository,
      ProductService productService,
      Validator validator,
      JsonMapper jsonMapper) {
    this.productRepository = productRepository;
    this.categoryRepository = categoryRepository;
    this.brandRepository = brandRepository;
    this.productService = productService;
    this.validator = validator;
    this.jsonMapper = jsonMapper;
  }

  /**
   * Nạp danh mục mẫu nếu cửa hàng chưa có sản phẩm nào, kể cả sản phẩm đã xóa mềm. Nhờ vậy khởi
   * động lại nhiều lần không nạp trùng, và không đè lên sản phẩm Quản trị viên đã thêm hoặc sửa.
   *
   * @param resource file JSON danh mục mẫu, chỉ được đọc khi cần nạp
   * @return số sản phẩm đã nạp, 0 nếu bỏ qua vì đã có sản phẩm
   * @throws UncheckedIOException nếu không đọc được file
   * @throws IllegalArgumentException nếu một sản phẩm trong file không hợp lệ
   */
  @Transactional
  public int seedIfEmpty(Resource resource) {
    if (productRepository.count() > 0) {
      return 0;
    }
    return importCatalog(read(resource));
  }

  /**
   * Đọc file JSON danh mục mẫu.
   *
   * @param resource file JSON
   * @return danh mục mẫu
   * @throws UncheckedIOException nếu không đọc được file
   */
  public CatalogSeed read(Resource resource) {
    try (InputStream in = resource.getInputStream()) {
      return jsonMapper.readValue(in, CatalogSeed.class);
    } catch (IOException ex) {
      throw new UncheckedIOException(
          "Không đọc được danh mục mẫu " + resource.getDescription(), ex);
    }
  }

  /**
   * Nạp mọi sản phẩm của danh mục mẫu. Danh mục, thương hiệu chưa có thì tạo mới; đã có (so tên
   * theo collation của MySQL: không phân biệt hoa thường và dấu) thì dùng lại.
   *
   * @param seed danh mục mẫu
   * @return số sản phẩm đã nạp
   * @throws IllegalArgumentException nếu một sản phẩm không hợp lệ; thông báo có tên sản phẩm
   */
  @Transactional
  public int importCatalog(CatalogSeed seed) {
    Map<String, Long> categoryIds = new HashMap<>();
    Map<String, Long> brandIds = new HashMap<>();
    for (SeedProduct product : seed.products()) {
      String label = "Sản phẩm mẫu \"" + product.name() + "\": ";
      checkValid(new NameRequest(product.category()), label + "danh mục ");
      Long categoryId = categoryIds.computeIfAbsent(product.category().trim(), this::categoryId);
      Long brandId = null;
      if (product.brand() != null && !product.brand().isBlank()) {
        checkValid(new NameRequest(product.brand()), label + "thương hiệu ");
        brandId = brandIds.computeIfAbsent(product.brand().trim(), this::brandId);
      }
      ProductCreateRequest request =
          new ProductCreateRequest(
              product.name(),
              product.description(),
              product.variationName1(),
              product.variationName2(),
              categoryId,
              brandId,
              List.of(),
              product.variants());
      checkValid(request, label);
      try {
        productService.create(request);
      } catch (InvalidRequestException | ConflictException ex) {
        throw new IllegalArgumentException(label + ex.getMessage(), ex);
      }
    }
    return seed.products().size();
  }

  private Long categoryId(String name) {
    return categoryRepository
        .findByName(name)
        .orElseGet(() -> categoryRepository.save(new Category(name)))
        .getId();
  }

  private Long brandId(String name) {
    return brandRepository
        .findByName(name)
        .orElseGet(() -> brandRepository.save(new Brand(name)))
        .getId();
  }

  private void checkValid(Object request, String label) {
    Set<ConstraintViolation<Object>> violations = validator.validate(request);
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException(
          label
              + violations.stream()
                  .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                  .sorted()
                  .collect(Collectors.joining("; ")));
    }
  }
}
