package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.client.MlServiceClient;
import com.manhdn.fashionshop.client.MlServiceClient.MlSuggestion;
import com.manhdn.fashionshop.client.MlServiceClient.SurveyRequest;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra các khối gợi ý dùng kết quả của dịch vụ học máy (US-12, US-13, US-23) với MySQL thật:
 * trang chủ đọc bảng recommendation (BR-20), sản phẩm liên quan và gợi ý theo khảo sát gọi
 * ml-service rồi lấp bằng sản phẩm bán chạy (BR-22). ml-service được thay bằng {@code
 * MlServiceClient} giả; mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationIntegrationTest {

  private static final String HOME = "/api/recommendations/me";
  private static final String BEST_SELLER = "Sản phẩm bán chạy";
  private static final String SIMILAR = "Tương tự sản phẩm bạn đang xem";
  private static final String BOUGHT_TOGETHER = "/api/products/bought-together";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @MockitoBean private MlServiceClient mlServiceClient;

  // ---------- Trang chủ (US-13) ----------

  @Test
  void homeReadsStoredListInRankOrderAndSkipsUnavailableProducts() throws Exception {
    long category = category("Danh mục Gợi ý Kestrel");
    long account = account();
    insertOrder(jdbcTemplate, account, "DELIVERED");
    List<Long> ids = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      ids.add(product(category, "Kestrel " + i, 1).id());
    }
    long soldOut = product(category, "Kestrel hết hàng", 0).id();
    long deleted = product(category, "Kestrel ngừng bán", 1).id();
    productService.delete(deleted);
    store(account, soldOut, ids.get(0), deleted, ids.get(1), ids.get(2), ids.get(3), ids.get(4));
    store(account, ids.get(5), ids.get(6), ids.get(7), ids.get(8));
    reload();

    customer(get(HOME), account)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("BEHAVIOR"))
        .andExpect(jsonPath("$.products.length()").value(8))
        .andExpect(jsonPath("$.products[0].product.id").value(ids.get(0)))
        .andExpect(jsonPath("$.products[0].product.name").value("Kestrel 1"))
        .andExpect(jsonPath("$.products[0].reason").value("Lý do " + ids.get(0)))
        .andExpect(jsonPath("$.products[0].source").value("BEHAVIOR"))
        .andExpect(jsonPath("$.products[7].product.id").value(ids.get(7)))
        .andExpect(jsonPath("$.products[*].product.id", not(hasItem((int) soldOut))))
        .andExpect(jsonPath("$.products[*].product.id", not(hasItem((int) deleted))));
  }

  @Test
  void listWithoutEnoughBehaviourComesFromSurveyAndNoListMeansBestSellers() throws Exception {
    long category = category("Danh mục Gợi ý Osprey");
    long fromSurvey = account();
    long newcomer = account();
    long product = product(category, "Osprey khảo sát", 3).id();
    store(fromSurvey, product);
    reload();

    customer(get(HOME), fromSurvey)
        .andExpect(jsonPath("$.source").value("SURVEY"))
        .andExpect(jsonPath("$.products[0].product.id").value(product))
        .andExpect(jsonPath("$.products[0].source").value("SURVEY"));
    customer(get(HOME), newcomer)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("POPULAR"))
        .andExpect(jsonPath("$.products[0].reason").value(BEST_SELLER))
        .andExpect(jsonPath("$.products[0].source").value("POPULAR"));
  }

  @Test
  void homeAddsRowForLatestViewWithoutCartItemsAndKeepsItOutOfMainList() throws Exception {
    long category = category("Danh mục Gợi ý Harrier");
    long account = account();
    insertOrder(jdbcTemplate, account, "DELIVERED");
    long viewed = product(category, "Harrier vừa xem", 1).id();
    long similar = product(category, "Harrier tương tự", 1).id();
    ProductResponse carted = product(category, "Harrier trong giỏ", 1);
    long stored = product(category, "Harrier đã tính", 1).id();
    store(account, similar, stored);
    view(account, viewed, LocalDateTime.now().minusMinutes(1));
    cart(account, variantOf(carted), LocalDateTime.now().minusHours(2));
    jdbcTemplate.update(
        "INSERT INTO recompute_request (account_id, task_type, due_at, created_at)"
            + " VALUES (?, 'RECOMMENDATION', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
        account);
    when(mlServiceClient.related(viewed, 4))
        .thenReturn(
            Optional.of(List.of(suggestion(carted.id(), SIMILAR), suggestion(similar, SIMILAR))));
    reload();

    customer(get(HOME), account)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.source").value("BEHAVIOR"))
        .andExpect(jsonPath("$.updating").value(true))
        .andExpect(jsonPath("$.updatedSecondsAgo").isNumber())
        .andExpect(jsonPath("$.recent.kind").value("VIEW"))
        .andExpect(jsonPath("$.recent.product.id").value(viewed))
        .andExpect(jsonPath("$.recent.product.name").value("Harrier vừa xem"))
        .andExpect(jsonPath("$.recent.products[0].product.id").value(similar))
        .andExpect(jsonPath("$.recent.products[0].reason").value(SIMILAR))
        .andExpect(jsonPath("$.recent.products[0].source").value("RELATED"))
        .andExpect(
            jsonPath("$.recent.products[*].product.id", not(hasItem(carted.id().intValue()))))
        .andExpect(jsonPath("$.products[0].product.id").value(stored))
        .andExpect(jsonPath("$.products[*].product.id", not(hasItem((int) similar))));
  }

  @Test
  void latestCartItemWinsOverOlderViewAndOldActivityIsIgnored() throws Exception {
    long category = category("Danh mục Gợi ý Plover");
    long account = account();
    long idle = account();
    long viewed = product(category, "Plover đã xem", 1).id();
    ProductResponse carted = product(category, "Plover vừa thêm giỏ", 1);
    long similar = product(category, "Plover tương tự", 1).id();
    view(account, viewed, LocalDateTime.now().minusHours(3));
    cart(account, variantOf(carted), LocalDateTime.now().minusMinutes(1));
    view(idle, viewed, LocalDateTime.now().minusHours(25));
    when(mlServiceClient.related(carted.id(), 4))
        .thenReturn(Optional.of(List.of(suggestion(similar, SIMILAR))));
    reload();

    customer(get(HOME), account)
        .andExpect(jsonPath("$.source").value("POPULAR"))
        .andExpect(jsonPath("$.updating").value(false))
        .andExpect(jsonPath("$.updatedSecondsAgo").isEmpty())
        .andExpect(jsonPath("$.recent.kind").value("CART"))
        .andExpect(jsonPath("$.recent.product.id").value(carted.id()))
        .andExpect(jsonPath("$.recent.products[0].product.id").value(similar))
        .andExpect(jsonPath("$.products[*].product.id", not(hasItem((int) similar))));
    customer(get(HOME), idle)
        .andExpect(jsonPath("$.source").value("POPULAR"))
        .andExpect(jsonPath("$.recent").isEmpty());
  }

  @Test
  void homeListIsForCustomersOnly() throws Exception {
    mockMvc.perform(get(HOME)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(HOME).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  // ---------- Sản phẩm liên quan (US-12, BR-21, BR-22) ----------

  @Test
  void relatedKeepsMlOrderAndReasonsThenFillsWithBestSellers() throws Exception {
    long shoes = category("Giày Gợi ý Merlin");
    long other = category("Bán chạy Gợi ý Merlin");
    long buyer = account();
    ProductResponse viewed = product(shoes, "Merlin đang xem", 1);
    ProductResponse similar = product(shoes, "Merlin tương tự", 1);
    ProductResponse soldOut = product(shoes, "Merlin hết hàng", 0);
    ProductResponse best = product(other, "Merlin bán chạy", 9);
    product(other, "Merlin lấp chỗ 1", 1);
    product(other, "Merlin lấp chỗ 2", 1);
    insertOrderItem(
        jdbcTemplate, insertOrder(jdbcTemplate, buyer, "SHIPPING"), variantOf(best), 1000);
    when(mlServiceClient.related(viewed.id(), 4))
        .thenReturn(
            Optional.of(
                List.of(
                    suggestion(soldOut.id(), SIMILAR),
                    suggestion(viewed.id(), SIMILAR),
                    suggestion(similar.id(), SIMILAR))));
    reload();

    mockMvc
        .perform(get("/api/products/" + viewed.id() + "/related"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4))
        .andExpect(jsonPath("$[0].product.id").value(similar.id()))
        .andExpect(jsonPath("$[0].reason").value(SIMILAR))
        .andExpect(jsonPath("$[0].source").value("RELATED"))
        .andExpect(jsonPath("$[1].product.id").value(best.id()))
        .andExpect(jsonPath("$[1].reason").value(BEST_SELLER))
        .andExpect(jsonPath("$[1].source").value("POPULAR"))
        .andExpect(jsonPath("$[*].product.id", not(hasItem(viewed.id().intValue()))))
        .andExpect(jsonPath("$[*].product.id", not(hasItem(soldOut.id().intValue()))));
  }

  // ---------- Thường được mua cùng ở giỏ hàng (chức năng 20) ----------

  @Test
  void boughtTogetherKeepsMlOrderAndSkipsCartAndSoldOutProducts() throws Exception {
    long bags = category("Túi Mua cùng Lark");
    ProductResponse inCart = product(bags, "Lark trong giỏ", 1);
    ProductResponse other = product(bags, "Lark trong giỏ 2", 1);
    ProductResponse soldOut = product(bags, "Lark hết hàng", 0);
    ProductResponse together = product(bags, "Lark mua cùng", 1);
    product(bags, "Lark bán chạy", 9);
    String reason = "Khách mua Lark trong giỏ cũng hay mua";
    when(mlServiceClient.boughtTogether(List.of(inCart.id(), other.id()), 4))
        .thenReturn(
            Optional.of(
                List.of(
                    suggestion(other.id(), reason),
                    suggestion(soldOut.id(), reason),
                    suggestion(together.id(), reason))));
    reload();

    mockMvc
        .perform(
            get(BOUGHT_TOGETHER)
                .param("productIds", inCart.id() + "," + other.id() + "," + inCart.id()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].product.id").value(together.id()))
        .andExpect(jsonPath("$[0].reason").value(reason))
        .andExpect(jsonPath("$[0].source").value("RELATED"));
  }

  @Test
  void withoutMlServiceBoughtTogetherCountsSharedOrdersAndDoesNotFill() throws Exception {
    when(mlServiceClient.boughtTogether(anyList(), anyInt())).thenReturn(Optional.empty());
    long shoes = category("Giày Mua cùng Finch");
    long buyer = account();
    ProductResponse inCart = product(shoes, "Finch trong giỏ", 1);
    ProductResponse often = product(shoes, "Finch hay mua cùng", 1);
    ProductResponse once = product(shoes, "Finch mua cùng 1 lần", 1);
    ProductResponse cancelled = product(shoes, "Finch chỉ trong đơn hủy", 1);
    ProductResponse best = product(shoes, "Finch bán chạy", 9);
    long first = insertOrder(jdbcTemplate, buyer, "DELIVERED");
    insertOrderItem(jdbcTemplate, first, variantOf(inCart), 1);
    insertOrderItem(jdbcTemplate, first, variantOf(once), 1);
    insertOrderItem(jdbcTemplate, first, variantOf(often), 1);
    long second = insertOrder(jdbcTemplate, buyer, "PENDING");
    insertOrderItem(jdbcTemplate, second, variantOf(inCart), 1);
    insertOrderItem(jdbcTemplate, second, variantOf(often), 1);
    long third = insertOrder(jdbcTemplate, buyer, "CANCELLED");
    insertOrderItem(jdbcTemplate, third, variantOf(inCart), 1);
    insertOrderItem(jdbcTemplate, third, variantOf(cancelled), 1);
    insertOrderItem(
        jdbcTemplate, insertOrder(jdbcTemplate, buyer, "DELIVERED"), variantOf(best), 50);
    reload();

    mockMvc
        .perform(get(BOUGHT_TOGETHER).param("productIds", String.valueOf(inCart.id())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].product.id").value(often.id()))
        .andExpect(jsonPath("$[0].reason").value("Thường được mua cùng sản phẩm trong giỏ"))
        .andExpect(jsonPath("$[0].source").value("RULE"))
        .andExpect(jsonPath("$[1].product.id").value(once.id()));
  }

  @Test
  void boughtTogetherNeedsOneToFiftyProducts() throws Exception {
    mockMvc.perform(get(BOUGHT_TOGETHER)).andExpect(status().isBadRequest());
    List<String> ids = new ArrayList<>();
    for (long id = 1; id <= 51; id++) {
      ids.add(String.valueOf(id));
    }
    mockMvc
        .perform(get(BOUGHT_TOGETHER).param("productIds", String.join(",", ids)))
        .andExpect(status().isBadRequest());
  }

  // ---------- Khảo sát (US-23) ----------

  @Test
  void surveySendsProfileToMlAndFillsWithCategoryBestSellers() throws Exception {
    long bags = surveyCategory("Balo laptop");
    long cheapBag = product(bags, "Balo Gợi ý rẻ", 2).id();
    long otherBag = product(bags, "Balo Gợi ý khác", 2).id();
    when(mlServiceClient.forSurvey(any()))
        .thenReturn(
            Optional.of(List.of(suggestion(cheapBag, "Giá tốt trong nhóm sản phẩm bạn quan tâm"))));
    reload();

    evaluate(
            answer("Q1", "SELF_MALE"),
            answer("Q2", "SCHOOL"),
            answer("Q3", "PRICE"),
            answer("S1", "200K_500K"),
            answer("S2", "BUY_ANYWAY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].product.id").value(cheapBag))
        .andExpect(
            jsonPath("$.products[0].reason").value("Giá tốt trong nhóm sản phẩm bạn quan tâm"))
        .andExpect(jsonPath("$.products[*].product.id", hasItem((int) otherBag)));

    ArgumentCaptor<SurveyRequest> request = ArgumentCaptor.forClass(SurveyRequest.class);
    verify(mlServiceClient).forSurvey(request.capture());
    assertThat(request.getValue().categoryIds()).contains(bags);
    assertThat(request.getValue().style()).isEqualTo("SAVER");
    assertThat(request.getValue().priceMin()).isEqualTo(200_000);
    assertThat(request.getValue().priceMax()).isEqualTo(650_000);
    assertThat(request.getValue().limit()).isEqualTo(8);
  }

  @Test
  void giftSurveyHasNoStyleBonusAndSpeaksAboutTheRecipient() throws Exception {
    long bags = surveyCategory("Balo laptop");
    long bag = product(bags, "Balo Gợi ý quà", 2).id();
    when(mlServiceClient.forSurvey(any()))
        .thenReturn(
            Optional.of(List.of(suggestion(bag, "Hợp với sở thích bạn chọn trong khảo sát"))));
    reload();

    evaluate(
            answer("Q1", "GIFT"),
            answer("G1", "FEMALE"),
            answer("G2", "SCHOOL"),
            answer("G3", "OVER_1M"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[0].product.id").value(bag))
        .andExpect(jsonPath("$.products[0].reason").value("Hợp với người nhận"));
    ArgumentCaptor<SurveyRequest> request = ArgumentCaptor.forClass(SurveyRequest.class);
    verify(mlServiceClient).forSurvey(request.capture());
    assertThat(request.getValue().style()).isNull();
    assertThat(request.getValue().priceMax()).isNull();
  }

  @Test
  void withoutMlServiceRelatedUsesRules() throws Exception {
    when(mlServiceClient.related(anyLong(), anyInt())).thenReturn(Optional.empty());
    long shirts = category("Áo Gợi ý Wren");
    ProductResponse viewed = product(shirts, "Wren đang xem", 1);
    ProductResponse sibling = product(shirts, "Wren cùng danh mục", 1);
    reload();

    mockMvc
        .perform(get("/api/products/" + viewed.id() + "/related"))
        .andExpect(jsonPath("$[0].product.id").value(sibling.id()))
        .andExpect(jsonPath("$[0].reason").value("Cùng danh mục với sản phẩm bạn đang xem"))
        .andExpect(jsonPath("$[0].source").value("RULE"))
        .andExpect(jsonPath("$[*].reason", not(contains(SIMILAR))));
  }

  // ---------- Tiện ích ----------

  private ResultActions customer(MockHttpServletRequestBuilder request, long accountId)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private ResultActions evaluate(String... answers) throws Exception {
    return mockMvc.perform(
        post("/api/survey/evaluate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"answers\":[" + String.join(",", answers) + "]}"));
  }

  private static String answer(String question, String option) {
    return "{\"questionCode\":\"" + question + "\",\"optionCodes\":[\"" + option + "\"]}";
  }

  private static MlSuggestion suggestion(long productId, String reason) {
    return new MlSuggestion(productId, 0.5, reason, "RELATED");
  }

  /** Ghi tiếp danh sách gợi ý của tài khoản như ml-service, lý do là "Lý do <mã sản phẩm>". */
  private void store(long accountId, long... productIds) {
    Integer last =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(rank_position), 0) FROM recommendation WHERE account_id = ?",
            Integer.class,
            accountId);
    int rank = last == null ? 0 : last;
    for (long productId : productIds) {
      rank++;
      jdbcTemplate.update(
          "INSERT INTO recommendation"
              + " (account_id, product_id, rank_position, score, reason, generated_at)"
              + " VALUES (?, ?, ?, ?, ?, ?)",
          accountId,
          productId,
          rank,
          1.0 / rank,
          "Lý do " + productId,
          LocalDateTime.now());
    }
  }

  private void view(long accountId, long productId, LocalDateTime at) {
    jdbcTemplate.update(
        "INSERT INTO product_view_log (account_id, product_id, viewed_at) VALUES (?, ?, ?)",
        accountId,
        productId,
        at);
  }

  private void cart(long accountId, long variantId, LocalDateTime at) {
    jdbcTemplate.update(
        "INSERT INTO cart_item (account_id, variant_id, quantity, updated_at) VALUES (?, ?, 1, ?)",
        accountId,
        variantId,
        at);
  }

  private void reload() {
    entityManager.flush();
    entityManager.clear();
  }

  private long category(String name) {
    return categoryService.create(name).id();
  }

  /** Danh mục có đúng tên trong cây câu hỏi khảo sát; tạo mới nếu CSDL test chưa có. */
  private long surveyCategory(String name) {
    return categoryRepository.findByName(name).map(c -> c.getId()).orElseGet(() -> category(name));
  }

  private long account() {
    return accountRepository
        .save(
            new Account(
                "goi-y-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER))
        .getId();
  }

  private ProductResponse product(long categoryId, String name, int stock) {
    return productService.create(
        new ProductCreateRequest(
            name,
            null,
            null,
            null,
            categoryId,
            null,
            List.of(),
            List.of(new VariantRequest(null, null, 300_000L, stock, null))));
  }

  private static long variantOf(ProductResponse product) {
    return product.variants().get(0).id();
  }
}
