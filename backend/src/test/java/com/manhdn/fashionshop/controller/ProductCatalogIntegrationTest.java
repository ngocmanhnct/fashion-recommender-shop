package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertReview;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.ProductUpdateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.BrandService;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import jakarta.persistence.EntityManager;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra API sản phẩm của trang người dùng (US-01, US-02, US-03, US-12) qua HTTP với MySQL thật.
 * Dữ liệu tạo qua service, đơn hàng và đánh giá thêm bằng SQL; mỗi test rollback ở cuối.
 *
 * <p>Số đã bán, điểm đánh giá là trường {@code @Formula}, chỉ được tính khi sản phẩm được nạp từ
 * CSDL, nên sau khi chuẩn bị dữ liệu phải {@code flush()} rồi {@code clear()} EntityManager
 * (request của MockMvc dùng chung transaction và persistence context với test). CSDL test có thể
 * còn dữ liệu của test khác, nên mọi kiểm tra chỉ xét danh mục, thương hiệu, từ khóa riêng của từng
 * test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductCatalogIntegrationTest {

  private static final String PRODUCTS = "/api/products";
  private static final String KEYWORD = "keyword";
  private static final String NAMES = "$.content[*].name";
  private static final String DETAIL = "$.detail";
  private static final String TOTAL = "$.totalElements";
  private static final String BEST_SELLER = "Sản phẩm bán chạy";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private BrandService brandService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  // ---------- Danh sách, chi tiết ----------

  @Test
  void anonymousSeesProductsForSaleAndDeletedOnesAreHidden() throws Exception {
    long category = category("Danh mục Zorvak");
    long alive =
        productWithVariants(
                category,
                "Zorvak còn bán",
                new VariantRequest("Cỡ 1", null, 150000L, 0, null),
                new VariantRequest("Cỡ 2", null, 120000L, 3, null))
            .id();
    long soldOut = product(category, null, "Zorvak hết hàng", 0, 90000L).id();
    long deleted = product(category, null, "Zorvak ngừng bán", 5, 80000L).id();
    productService.delete(deleted);
    reload();

    search(KEYWORD, "zorvak", "sort", "NEWEST")
        .andExpect(status().isOk())
        .andExpect(jsonPath(TOTAL).value(2))
        .andExpect(jsonPath(NAMES, contains("Zorvak hết hàng", "Zorvak còn bán")))
        .andExpect(jsonPath("$.content[0].inStock").value(false))
        .andExpect(jsonPath("$.content[1].categoryName").value("Danh mục Zorvak"))
        .andExpect(jsonPath("$.content[1].minPrice").value(120000))
        .andExpect(jsonPath("$.content[1].maxPrice").value(150000))
        .andExpect(jsonPath("$.content[1].inStock").value(true))
        .andExpect(jsonPath("$.content[1].soldCount").value(0))
        .andExpect(jsonPath("$.content[1].reviewCount").value(0))
        .andExpect(jsonPath("$.content[1].ratingAverage").doesNotExist());

    mockMvc
        .perform(get(PRODUCTS + "/" + alive))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Zorvak còn bán"))
        .andExpect(jsonPath("$.variationName1").value("Kích cỡ"))
        .andExpect(jsonPath("$.category.name").value("Danh mục Zorvak"))
        .andExpect(jsonPath("$.variants.length()").value(2))
        .andExpect(jsonPath("$.variants[0].stock").value(0))
        .andExpect(jsonPath("$.variants[1].stock").value(3))
        .andExpect(jsonPath("$.minPrice").value(120000))
        .andExpect(jsonPath("$.inStock").value(true));
    mockMvc
        .perform(get(PRODUCTS + "/" + soldOut))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.inStock").value(false));
    mockMvc
        .perform(get(PRODUCTS + "/" + deleted))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath(DETAIL).value("Không tìm thấy sản phẩm"));
    mockMvc.perform(get(PRODUCTS + "/" + deleted + "/related")).andExpect(status().isNotFound());
  }

  // ---------- Tìm theo từ khóa (US-02) ----------

  @Test
  void keywordMustMatchEveryTokenInNameCategoryOrBrand() throws Exception {
    long shoes = category("Giày Quilmex");
    long sandals = category("Dép Zelphor");
    long brand = brand("Fendrix");
    product(shoes, brand, "Sneaker Vortan", 1, 500000L);
    product(sandals, null, "Dép Vortan", 1, 200000L);
    reload();

    search(KEYWORD, "quilmex fendrix").andExpect(jsonPath(NAMES, contains("Sneaker Vortan")));
    search(KEYWORD, "vortan")
        .andExpect(jsonPath(NAMES, containsInAnyOrder("Sneaker Vortan", "Dép Vortan")));
    search(KEYWORD, "Vortan, FENDRIX!").andExpect(jsonPath(NAMES, contains("Sneaker Vortan")));
    search(KEYWORD, "giay vortan").andExpect(jsonPath(NAMES, contains("Sneaker Vortan")));
    search(KEYWORD, "vortan khongcotu").andExpect(jsonPath(TOTAL).value(0));
  }

  @Test
  void keywordIgnoresCaseAndAccentsButOnlyMatchesWordStarts() throws Exception {
    long category = category("Thời trang Morvide");
    product(category, null, "Giày cao cổ Morvide", 1, 400000L);
    product(category, null, "Áo thun Morvide", 1, 150000L);
    product(category, null, "Sneakerzz Morvide", 1, 600000L);
    reload();

    search(KEYWORD, "áo morvide").andExpect(jsonPath(NAMES, contains("Áo thun Morvide")));
    search(KEYWORD, "AO MORVIDE").andExpect(jsonPath(NAMES, contains("Áo thun Morvide")));
    search(KEYWORD, "sneak morvide").andExpect(jsonPath(NAMES, contains("Sneakerzz Morvide")));
    search(KEYWORD, "hun morvide").andExpect(jsonPath(TOTAL).value(0));
  }

  @Test
  void decomposedVietnameseNamesAreStoredComposedSoKeywordsMatchThem() throws Exception {
    long category = category(nfd("Guốc Nolvex"));
    long brand = brand(nfd("Hãng Nolvex"));
    long id = product(category, brand, nfd("Dép Đúc Liền Nolvex"), 1, 120000L).id();
    reload();

    search(KEYWORD, "đúc liền nolvex").andExpect(jsonPath(NAMES, contains("Dép Đúc Liền Nolvex")));
    search(KEYWORD, "duc lien nolvex").andExpect(jsonPath(NAMES, contains("Dép Đúc Liền Nolvex")));
    search(KEYWORD, "guoc nolvex").andExpect(jsonPath(NAMES, contains("Dép Đúc Liền Nolvex")));
    search(KEYWORD, "hãng nolvex").andExpect(jsonPath(NAMES, contains("Dép Đúc Liền Nolvex")));
    mockMvc
        .perform(get(PRODUCTS + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.category.name").value("Guốc Nolvex"))
        .andExpect(jsonPath("$.brand.name").value("Hãng Nolvex"));

    productService.update(
        id,
        new ProductUpdateRequest(
            nfd("Dép Mộc Nolvex"), null, null, null, category, brand, List.of()));
    reload();
    search(KEYWORD, "moc nolvex").andExpect(jsonPath(NAMES, contains("Dép Mộc Nolvex")));
  }

  @Test
  void percentAndUnderscoreAreNotWildcards() throws Exception {
    long category = category("Khăn Plexor");
    product(category, null, "Khăn 100% cotton Plexor", 1, 90000L);
    product(category, null, "Khăn 2000 sợi Plexor", 1, 95000L);
    reload();

    search(KEYWORD, "%").andExpect(jsonPath(TOTAL).value(0));
    search(KEYWORD, "_").andExpect(jsonPath(TOTAL).value(0));
    search(KEYWORD, "100% plexor").andExpect(jsonPath(NAMES, contains("Khăn 100% cotton Plexor")));
    search(KEYWORD, "plexor_").andExpect(jsonPath(TOTAL).value(2));
  }

  // ---------- Bộ lọc (US-01) ----------

  @Test
  void filtersByCategoryBrandAndVariantPriceRange() throws Exception {
    long bags = category("Túi Xandrel");
    long wallets = category("Ví Xandrel");
    long brand = brand("Hiệu Xandrel");
    product(bags, brand, "Xandrel một", 2, 100000L, 300000L);
    product(bags, null, "Xandrel hai", 2, 500000L);
    product(wallets, brand, "Xandrel ba", 2, 200000L);
    reload();

    search(KEYWORD, "xandrel", "categoryId", String.valueOf(bags))
        .andExpect(jsonPath(NAMES, containsInAnyOrder("Xandrel một", "Xandrel hai")));
    search(KEYWORD, "xandrel", "brandId", String.valueOf(brand))
        .andExpect(jsonPath(NAMES, containsInAnyOrder("Xandrel một", "Xandrel ba")));
    search(KEYWORD, "xandrel", "minPrice", "250000", "maxPrice", "400000")
        .andExpect(jsonPath(NAMES, contains("Xandrel một")));
    search(KEYWORD, "xandrel", "minPrice", "450000")
        .andExpect(jsonPath(NAMES, contains("Xandrel hai")));
    search(KEYWORD, "xandrel", "maxPrice", "150000")
        .andExpect(jsonPath(NAMES, contains("Xandrel một")));
    search(
            KEYWORD,
            "xandrel",
            "categoryId",
            String.valueOf(bags),
            "brandId",
            String.valueOf(brand),
            "minPrice",
            "300000",
            "maxPrice",
            "300000")
        .andExpect(jsonPath(NAMES, contains("Xandrel một")));
  }

  @Test
  void ratingFilterUsesTheRoundedAverageAndExcludesUnratedProducts() throws Exception {
    long category = category("Danh mục Ombrik");
    ProductResponse top = product(category, null, "Ombrik được khen", 5, 300000L);
    ProductResponse middle = product(category, null, "Ombrik bình thường", 5, 200000L);
    product(category, null, "Ombrik chưa đánh giá", 5, 100000L);
    // 19 lần 4 sao và 1 lần 3 sao: trung bình 3,95, làm tròn thành 4,0 như trên giao diện
    List<Integer> topRatings = new ArrayList<>(Collections.nCopies(19, 4));
    topRatings.add(3);
    rate(top, topRatings);
    rate(middle, List.of(4, 3));
    reload();

    search(KEYWORD, "ombrik", "minRating", "4")
        .andExpect(jsonPath(NAMES, contains("Ombrik được khen")))
        .andExpect(jsonPath("$.content[0].ratingAverage").value(4.0))
        .andExpect(jsonPath("$.content[0].reviewCount").value(20));
    search(KEYWORD, "ombrik", "minRating", "3")
        .andExpect(jsonPath(NAMES, containsInAnyOrder("Ombrik được khen", "Ombrik bình thường")));
    search(KEYWORD, "ombrik", "minRating", "1").andExpect(jsonPath(TOTAL).value(2));
    search(KEYWORD, "ombrik").andExpect(jsonPath(TOTAL).value(3));
    mockMvc
        .perform(get(PRODUCTS + "/" + middle.id()))
        .andExpect(jsonPath("$.ratingAverage").value(3.5))
        .andExpect(jsonPath("$.reviewCount").value(2));
  }

  // ---------- Sắp xếp (US-01) ----------

  @Test
  void sortsByPopularityNewestAndPrice() throws Exception {
    long groupA = category("Nhóm Brivane A");
    long groupB = category("Nhóm Brivane B");
    product(groupA, null, "Brivane A1", 1, 100000L);
    product(groupA, null, "Brivane A2", 1, 300000L);
    ProductResponse a3 = product(groupA, null, "Brivane A3", 1, 200000L);
    product(groupB, null, "Brivane B1", 1, 250000L);
    ProductResponse b2 = product(groupB, null, "Brivane B2", 1, 150000L);
    reload();

    // Chưa bán được gì: sản phẩm đầu của mỗi danh mục trước, rồi tới sản phẩm thứ hai...
    search(KEYWORD, "brivane")
        .andExpect(
            jsonPath(
                NAMES,
                contains("Brivane A1", "Brivane B1", "Brivane A2", "Brivane B2", "Brivane A3")));
    search(KEYWORD, "brivane", "sort", "NEWEST")
        .andExpect(
            jsonPath(
                NAMES,
                contains("Brivane B2", "Brivane B1", "Brivane A3", "Brivane A2", "Brivane A1")));
    search(KEYWORD, "brivane", "sort", "PRICE_ASC")
        .andExpect(
            jsonPath(
                NAMES,
                contains("Brivane A1", "Brivane B2", "Brivane A3", "Brivane B1", "Brivane A2")));
    search(KEYWORD, "brivane", "sort", "PRICE_DESC")
        .andExpect(
            jsonPath(
                NAMES,
                contains("Brivane A2", "Brivane B1", "Brivane A3", "Brivane B2", "Brivane A1")));

    // Đơn đã hủy không tính là đã bán
    long buyer = account("brivane@test.local");
    insertOrderItem(jdbcTemplate, insertOrder(jdbcTemplate, buyer, "PENDING"), variantOf(a3), 5);
    insertOrderItem(jdbcTemplate, insertOrder(jdbcTemplate, buyer, "CANCELLED"), variantOf(b2), 9);
    reload();
    search(KEYWORD, "brivane")
        .andExpect(
            jsonPath(
                NAMES,
                contains("Brivane A3", "Brivane A1", "Brivane B1", "Brivane A2", "Brivane B2")))
        .andExpect(jsonPath("$.content[0].soldCount").value(5))
        .andExpect(jsonPath("$.content[4].soldCount").value(0));
  }

  @Test
  void pagesTheResults() throws Exception {
    long category = category("Danh mục Pagivo");
    for (int i = 1; i <= 3; i++) {
      product(category, null, "Pagivo " + i, 1, 10000L * i);
    }
    reload();

    search(KEYWORD, "pagivo", "sort", "PRICE_ASC", "page", "1", "size", "2")
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath(TOTAL).value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath(NAMES, contains("Pagivo 3")));
    search(KEYWORD, "pagivo", "page", String.valueOf(10000))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  void invalidParametersGive400() throws Exception {
    expectBadRequest(
        "Giá thấp nhất không được lớn hơn giá cao nhất", "minPrice", "500", "maxPrice", "100");
    expectBadRequest("Giá không được âm", "minPrice", "-1");
    expectBadRequest("Mức đánh giá phải từ 1 đến 5 sao", "minRating", "6");
    expectBadRequest("Mức đánh giá phải từ 1 đến 5 sao", "minRating", "0");
    expectBadRequest("Số sản phẩm mỗi trang phải từ 1 đến 48", "size", "0");
    expectBadRequest("Số sản phẩm mỗi trang phải từ 1 đến 48", "size", "49");
    expectBadRequest("Số trang phải từ 0 đến 10000", "page", "-1");
    expectBadRequest("Số trang phải từ 0 đến 10000", "page", "10001");
    expectBadRequest("Từ khóa tối đa 100 ký tự", KEYWORD, "a".repeat(101));
    expectBadRequest("Giá trị của tham số sort không hợp lệ", "sort", "FOO");
    expectBadRequest("Giá trị của tham số page không hợp lệ", "page", "abc");
    mockMvc
        .perform(get(PRODUCTS + "/abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath(DETAIL).value("Giá trị của tham số id không hợp lệ"));
    mockMvc
        .perform(get(PRODUCTS + "/suggestions").param(KEYWORD, "a".repeat(101)))
        .andExpect(status().isBadRequest());
  }

  // ---------- Gợi ý khi tìm không thấy (US-02) ----------

  @Test
  void suggestionsPreferProductsMatchingMoreTokensAndSkipSoldOutOnes() throws Exception {
    long category = category("Danh mục Selvan");
    long buyer = account("selvan@test.local");
    ProductResponse both = product(category, null, "Kandor Trelvo đen", 2, 300000L);
    product(category, null, "Trelvo trắng", 2, 100000L);
    ProductResponse popular = product(category, null, "Kandor xám", 2, 200000L);
    ProductResponse soldOut = product(category, null, "Kandor Trelvo cũ", 0, 100000L);
    insertOrderItem(
        jdbcTemplate, insertOrder(jdbcTemplate, buyer, "DELIVERED"), variantOf(popular), 3);
    reload();

    mockMvc
        .perform(get(PRODUCTS + "/suggestions").param(KEYWORD, "trelvo kandor khongcotu"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$[*].name", contains("Kandor Trelvo đen", "Kandor xám", "Trelvo trắng")))
        .andExpect(jsonPath("$[0].id").value(both.id()))
        .andExpect(jsonPath("$[*].id", not(hasItem(soldOut.id().intValue()))));
  }

  @Test
  void suggestionsFallBackToInStockBestSellers() throws Exception {
    long category = category("Danh mục Bestovo");
    long buyer = account("bestovo@test.local");
    ProductResponse best = product(category, null, "Bestovo bán chạy", 5, 300000L);
    ProductResponse soldOut = product(category, null, "Bestovo hết hàng", 0, 300000L);
    for (int i = 1; i <= 3; i++) {
      product(category, null, "Bestovo phụ " + i, 1, 100000L);
    }
    long order = insertOrder(jdbcTemplate, buyer, "DELIVERED");
    insertOrderItem(jdbcTemplate, order, variantOf(best), 1000);
    insertOrderItem(jdbcTemplate, order, variantOf(soldOut), 2000);
    reload();

    for (String keyword : List.of("khongcosanphamnaokhop", "", "%%")) {
      mockMvc
          .perform(get(PRODUCTS + "/suggestions").param(KEYWORD, keyword))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(4))
          .andExpect(jsonPath("$[0].id").value(best.id()))
          .andExpect(jsonPath("$[0].soldCount").value(1000))
          .andExpect(jsonPath("$[*].id", not(hasItem(soldOut.id().intValue()))));
    }
  }

  // ---------- Gợi ý sản phẩm liên quan (US-12, BR-21, BR-22) ----------

  @Test
  void relatedFollowsTiersAndFillsWithBestSellers() throws Exception {
    long shirts = category("Áo Dravik");
    long trousers = category("Quần Dravik");
    long bestCategory = category("Bán chạy Dravik");
    long brand = brand("Hiệu Dravik");
    long buyer = account("dravik@test.local");
    ProductResponse viewed = product(shirts, brand, "Dravik đang xem", 1, 100000L);
    ProductResponse sameBoth = product(shirts, brand, "Dravik cùng cả hai", 1, 100000L);
    ProductResponse soldOut = product(shirts, brand, "Dravik hết hàng", 0, 100000L);
    ProductResponse sameCategory = product(shirts, null, "Dravik cùng danh mục", 1, 100000L);
    ProductResponse sameBrand = product(trousers, brand, "Dravik cùng thương hiệu", 1, 100000L);
    ProductResponse best = product(bestCategory, null, "Dravik bán chạy", 9, 100000L);
    insertOrderItem(
        jdbcTemplate, insertOrder(jdbcTemplate, buyer, "SHIPPING"), variantOf(best), 1000);
    reload();

    mockMvc
        .perform(get(PRODUCTS + "/" + viewed.id() + "/related"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].product.id").value(sameBoth.id()))
        .andExpect(
            jsonPath("$[0].reason").value("Cùng danh mục và thương hiệu với sản phẩm bạn đang xem"))
        .andExpect(jsonPath("$[1].product.id").value(sameCategory.id()))
        .andExpect(jsonPath("$[1].reason").value("Cùng danh mục với sản phẩm bạn đang xem"))
        .andExpect(jsonPath("$[2].product.id").value(sameBrand.id()))
        .andExpect(jsonPath("$[2].reason").value("Cùng thương hiệu với sản phẩm bạn đang xem"))
        .andExpect(jsonPath("$[3].product.id").value(best.id()))
        .andExpect(jsonPath("$[3].reason").value(BEST_SELLER))
        .andExpect(jsonPath("$[*].product.id", not(hasItem(soldOut.id().intValue()))));

    // Sản phẩm một mình trong danh mục, không có thương hiệu: chỉ còn sản phẩm bán chạy
    long hats = category("Mũ Dravik");
    ProductResponse lonely = product(hats, null, "Dravik một mình", 1, 100000L);
    reload();
    mockMvc
        .perform(get(PRODUCTS + "/" + lonely.id() + "/related"))
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].product.id").value(best.id()))
        .andExpect(
            jsonPath("$[*].reason", contains(BEST_SELLER, BEST_SELLER, BEST_SELLER, BEST_SELLER)))
        .andExpect(jsonPath("$[*].product.id", not(hasItem(lonely.id().intValue()))))
        .andExpect(jsonPath("$[*].product.id", not(hasItem(soldOut.id().intValue()))));
  }

  // ---------- Ghi lượt xem: phân quyền ----------

  @Test
  void recordingViewsNeedsCustomerRole() throws Exception {
    long category = category("Danh mục Viewra");
    long id = product(category, null, "Viewra", 1, 100000L).id();

    mockMvc.perform(post(PRODUCTS + "/" + id + "/views")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post(PRODUCTS + "/" + id + "/views")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  // ---------- Hàm hỗ trợ ----------

  private ResultActions search(String... params) throws Exception {
    var request = get(PRODUCTS);
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc.perform(request).andExpect(status().isOk());
  }

  private void expectBadRequest(String detail, String... params) throws Exception {
    var request = get(PRODUCTS);
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath(DETAIL).value(detail));
  }

  /**
   * Ghi dữ liệu đã chuẩn bị xuống CSDL rồi bỏ entity cũ, để request sau nạp lại trường @Formula.
   */
  private void reload() {
    entityManager.flush();
    entityManager.clear();
  }

  private long category(String name) {
    return categoryService.create(name).id();
  }

  private long brand(String name) {
    return brandService.create(name).id();
  }

  private long account(String email) {
    return accountRepository
        .save(new Account(email, "khong-dung", "Khách thử", Role.CUSTOMER))
        .getId();
  }

  /**
   * Tạo sản phẩm, mọi biến thể cùng tồn kho. Một giá thì không có phân loại; nhiều giá thì có 1
   * tầng "Kích cỡ", mỗi giá là 1 biến thể.
   */
  private ProductResponse product(
      long categoryId, Long brandId, String name, int stock, long... prices) {
    List<VariantRequest> variants = new ArrayList<>();
    for (int i = 0; i < prices.length; i++) {
      String option = prices.length == 1 ? null : "Cỡ " + (i + 1);
      variants.add(new VariantRequest(option, null, prices[i], stock, null));
    }
    return create(categoryId, brandId, name, variants);
  }

  /** Tạo sản phẩm không thương hiệu có 1 tầng "Kích cỡ" với các biến thể cho trước. */
  private ProductResponse productWithVariants(
      long categoryId, String name, VariantRequest... variants) {
    return create(categoryId, null, name, List.of(variants));
  }

  private ProductResponse create(
      long categoryId, Long brandId, String name, List<VariantRequest> variants) {
    String tier = variants.get(0).option1Value() == null ? null : "Kích cỡ";
    return productService.create(
        new ProductCreateRequest(name, null, tier, null, categoryId, brandId, List.of(), variants));
  }

  /** Chuỗi ở dạng tách dấu (Unicode NFD), như tên gõ trên macOS hoặc trong dữ liệu Tiki. */
  private static String nfd(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD);
  }

  private static long variantOf(ProductResponse product) {
    return product.variants().get(0).id();
  }

  /** Mỗi điểm là đánh giá của một tài khoản riêng (mỗi tài khoản tối đa 1 đánh giá/sản phẩm). */
  private void rate(ProductResponse product, List<Integer> ratings) {
    for (int i = 0; i < ratings.size(); i++) {
      long account = account("danhgia-%d-%d@test.local".formatted(product.id(), i));
      long order = insertOrder(jdbcTemplate, account, "DELIVERED");
      long item = insertOrderItem(jdbcTemplate, order, variantOf(product), 1);
      insertReview(jdbcTemplate, account, product.id(), item, ratings.get(i));
    }
  }
}
