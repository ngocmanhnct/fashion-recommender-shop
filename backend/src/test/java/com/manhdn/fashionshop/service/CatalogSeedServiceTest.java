package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.manhdn.fashionshop.dto.CatalogSeed;
import com.manhdn.fashionshop.dto.CatalogSeed.SeedProduct;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductVariant;
import com.manhdn.fashionshop.repository.BrandRepository;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

/** Kiểm tra nạp danh mục mẫu với MySQL thật; mỗi test rollback ở cuối. */
@SpringBootTest
@Transactional
class CatalogSeedServiceTest {

  private static final Pattern COMBINING_MARK = Pattern.compile("[\\x{0300}-\\x{036F}]");

  @Autowired private CatalogSeedService catalogSeedService;
  @Autowired private ProductRepository productRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private BrandRepository brandRepository;

  @Test
  void importsProductsAndReusesCategoriesAndBrandsByName() {
    CatalogSeed seed =
        new CatalogSeed(
            "test",
            List.of(
                product(
                    "Giày mẫu nam",
                    "Hiệu mẫu",
                    "Giày mẫu A",
                    "Kích cỡ",
                    variant("39", 350000, "MAU-A-39"),
                    variant("40", 350000, "MAU-A-40")),
                product("giay MAU nam", "hiệu MẪU", "Giày mẫu B", null, variant(null, 99000, null)),
                product("Túi mẫu nữ", " ", "Túi mẫu C", null, variant(null, 120000, null))));

    assertThat(catalogSeedService.importCatalog(seed)).isEqualTo(3);

    Category shoes = categoryRepository.findByName("Giày mẫu nam").orElseThrow();
    List<Product> shoeProducts =
        productRepository.findAll().stream()
            .filter(p -> p.getCategory().getId().equals(shoes.getId()))
            .toList();
    assertThat(shoeProducts).extracting(Product::getName).contains("Giày mẫu A", "Giày mẫu B");
    assertThat(brandRepository.findAll())
        .filteredOn(b -> b.getName().equalsIgnoreCase("Hiệu mẫu"))
        .hasSize(1);

    Product first =
        shoeProducts.stream().filter(p -> p.getName().equals("Giày mẫu A")).findAny().orElseThrow();
    assertThat(first.getVariationName1()).isEqualTo("Kích cỡ");
    assertThat(first.getVariants())
        .extracting(ProductVariant::getOption1Value)
        .containsExactly("39", "40");
    Product bag =
        productRepository.findAll().stream()
            .filter(p -> p.getName().equals("Túi mẫu C"))
            .findAny()
            .orElseThrow();
    assertThat(bag.getBrand()).isNull();
    assertThat(bag.variationCount()).isZero();
  }

  @Test
  void invalidProductStopsImportAndNamesTheProduct() {
    CatalogSeed seed =
        new CatalogSeed(
            "test",
            List.of(
                product("Giày mẫu lỗi", null, "Giày thiếu cỡ", "Kích cỡ", variant(null, 1, null))));

    assertThatThrownBy(() -> catalogSeedService.importCatalog(seed))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Giày thiếu cỡ")
        .hasMessageContaining("Phải nhập giá trị cho tầng phân loại \"Kích cỡ\"");
  }

  @Test
  void seedIfEmptySkipsWithoutReadingFileWhenProductsExist() {
    catalogSeedService.importCatalog(
        new CatalogSeed(
            "test",
            List.of(product("Phụ kiện mẫu", null, "Ví mẫu", null, variant(null, 50000, null)))));

    assertThat(catalogSeedService.seedIfEmpty(new ClassPathResource("seed/khong-co-file.json")))
        .isZero();
  }

  @Test
  void shippedTikiCatalogPassesAllProductRules() {
    CatalogSeed seed = catalogSeedService.read(new ClassPathResource("seed/tiki-catalog.json"));

    assertThat(seed.products()).isNotEmpty();
    assertThat(catalogSeedService.importCatalog(seed)).isEqualTo(seed.products().size());
  }

  @Test
  void shippedTikiCatalogTextIsComposedUnicode() {
    CatalogSeed seed = catalogSeedService.read(new ClassPathResource("seed/tiki-catalog.json"));

    List<String> texts = new ArrayList<>();
    for (SeedProduct product : seed.products()) {
      texts.addAll(
          Arrays.asList(
              product.category(),
              product.brand(),
              product.name(),
              product.description(),
              product.variationName1(),
              product.variationName2()));
      for (VariantRequest variant : product.variants()) {
        texts.add(variant.option1Value());
        texts.add(variant.option2Value());
      }
    }
    // Chữ tách dấu (NFD) hay kèm dấu kết hợp (U+0300–U+036F) không tìm được bằng từ khóa
    assertThat(texts)
        .filteredOn(
            text ->
                text != null
                    && (!Normalizer.isNormalized(text, Normalizer.Form.NFC)
                        || COMBINING_MARK.matcher(text).find()))
        .isEmpty();
  }

  private static SeedProduct product(
      String category, String brand, String name, String tier, VariantRequest... variants) {
    return new SeedProduct(category, brand, name, null, tier, null, List.of(variants));
  }

  private static VariantRequest variant(String option, long price, String sku) {
    return new VariantRequest(option, null, price, 10, sku);
  }
}
