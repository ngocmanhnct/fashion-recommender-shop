package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Kiểm tra API giỏ hàng (US-06, US-07, BR-06, BR-07, BR-11) với MySQL thật.
 *
 * <p>Không dùng {@code @Transactional}: yêu cầu tính lại gợi ý chỉ được ghi ngay trước khi giao
 * dịch commit. Mọi dòng test tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private long categoryId;

  @BeforeEach
  void createCategory() {
    categoryId = categoryService.create("Danh mục giỏ hàng " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    deleteWhere("DELETE FROM recompute_request WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_event_log WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM cart_item WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  @Test
  void addingSameVariantTwiceSumsQuantityLogsEventAndRequestsRecompute() throws Exception {
    long account = account();
    long variant = variantOf(product(150_000L, 5));

    perform(account, addItem(variant, 2)).andExpect(status().isOk());
    perform(account, addItem(variant, 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].variantId").value(variant))
        .andExpect(jsonPath("$.items[0].quantity").value(3))
        .andExpect(jsonPath("$.items[0].unitPrice").value(150_000))
        .andExpect(jsonPath("$.items[0].categoryName").value(startsWith("Danh mục giỏ hàng")))
        .andExpect(jsonPath("$.items[0].lineTotal").value(450_000))
        .andExpect(jsonPath("$.totalQuantity").value(3))
        .andExpect(jsonPath("$.subtotal").value(450_000))
        .andExpect(jsonPath("$.notices.length()").value(0));

    assertThat(count("SELECT COUNT(*) FROM cart_item WHERE account_id = ?", account)).isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM product_event_log WHERE account_id = ?"
                    + " AND event_type = 'ADD_TO_CART'",
                account))
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM recompute_request WHERE account_id = ?"
                    + " AND task_type = 'RECOMMENDATION'"
                    + " AND due_at <= TIMESTAMPADD(SECOND, 3, UTC_TIMESTAMP(3))",
                account))
        .isEqualTo(1);
  }

  @Test
  void addingMoreThanStockOrOutOfStockOrDeletedIsRejected() throws Exception {
    long account = account();
    long variant = variantOf(product(100_000L, 3));
    perform(account, addItem(variant, 2)).andExpect(status().isOk());

    perform(account, addItem(variant, 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Chỉ còn 3 sản phẩm, giỏ hàng đã có 2"));
    perform(account, addItem(variantOf(product(100_000L, 0)), 1))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Phân loại này đã hết hàng"));

    long deleted = product(100_000L, 5);
    productService.delete(deleted);
    perform(account, addItem(variantOf(deleted), 1)).andExpect(status().isNotFound());
    perform(account, addItem(999_999_999L, 1)).andExpect(status().isNotFound());
    perform(account, addItem(variant, 0)).andExpect(status().isBadRequest());
    perform(account, addItem(variant, 100)).andExpect(status().isBadRequest());

    assertThat(count("SELECT quantity FROM cart_item WHERE account_id = ?", account)).isEqualTo(2);
  }

  @Test
  void readingCartRemovesUnavailableLinesAndReducesQuantityWithNotices() throws Exception {
    long account = account();
    long keep = product(50_000L, 10);
    long soldOut = product(60_000L, 5);
    long stopped = product(70_000L, 5);
    long shrinking = product(80_000L, 5);
    perform(account, addItem(variantOf(keep), 1)).andExpect(status().isOk());
    perform(account, addItem(variantOf(soldOut), 1)).andExpect(status().isOk());
    perform(account, addItem(variantOf(stopped), 1)).andExpect(status().isOk());
    perform(account, addItem(variantOf(shrinking), 4)).andExpect(status().isOk());

    jdbcTemplate.update("UPDATE product_variant SET stock = 0 WHERE product_id = ?", soldOut);
    jdbcTemplate.update("UPDATE product_variant SET stock = 2 WHERE product_id = ?", shrinking);
    productService.delete(stopped);

    perform(account, get("/api/cart"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[1].quantity").value(2))
        .andExpect(jsonPath("$.subtotal").value(50_000 + 2 * 80_000))
        .andExpect(jsonPath("$.notices.length()").value(3))
        .andExpect(jsonPath("$.notices[0].type").value("REMOVED_OUT_OF_STOCK"))
        .andExpect(jsonPath("$.notices[1].type").value("REMOVED_UNAVAILABLE"))
        .andExpect(jsonPath("$.notices[2].type").value("QUANTITY_REDUCED"))
        .andExpect(
            jsonPath("$.notices[2].message")
                .value("Chỉ còn 2 sản phẩm nên số lượng đã giảm từ 4 xuống 2"));

    // Đã sửa xong ở lần đọc trước nên lần đọc sau không còn thông báo
    perform(account, get("/api/cart"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.notices.length()").value(0));
  }

  @Test
  void updateAndRemoveLine() throws Exception {
    long account = account();
    long variant = variantOf(product(100_000L, 4));
    perform(account, addItem(variant, 1)).andExpect(status().isOk());

    perform(
            account,
            put("/api/cart/items/" + variant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 4}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].quantity").value(4));
    perform(
            account,
            put("/api/cart/items/" + variant)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Chỉ còn 4 sản phẩm"));
    perform(
            account,
            put("/api/cart/items/999999999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 1}"))
        .andExpect(status().isNotFound());

    perform(account, delete("/api/cart/items/" + variant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.subtotal").value(0));
    perform(account, delete("/api/cart/items/" + variant)).andExpect(status().isOk());
  }

  @Test
  void mergeSumsSameVariantCapsAtStockAndSkipsUnavailable() throws Exception {
    long account = account();
    long shared = variantOf(product(100_000L, 5));
    long fresh = variantOf(product(200_000L, 9));
    long soldOut = variantOf(product(300_000L, 0));
    perform(account, addItem(shared, 3)).andExpect(status().isOk());

    perform(
            account,
            post("/api/cart/merge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"items\": [{\"variantId\": %d, \"quantity\": 4},".formatted(shared)
                        + " {\"variantId\": %d, \"quantity\": 2},".formatted(fresh)
                        + " {\"variantId\": %d, \"quantity\": 1},".formatted(soldOut)
                        + " {\"variantId\": 999999999, \"quantity\": 1}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].quantity").value(5))
        .andExpect(jsonPath("$.items[1].variantId").value(fresh))
        .andExpect(jsonPath("$.items[1].quantity").value(2))
        .andExpect(jsonPath("$.notices.length()").value(3))
        .andExpect(jsonPath("$.notices[0].type").value("QUANTITY_REDUCED"))
        .andExpect(jsonPath("$.notices[1].type").value("REMOVED_OUT_OF_STOCK"))
        .andExpect(jsonPath("$.notices[2].type").value("REMOVED_UNAVAILABLE"));
  }

  @Test
  void previewIsPublicAndWritesNothing() throws Exception {
    long variant = variantOf(product(120_000L, 3));
    long before = count("SELECT COUNT(*) FROM cart_item");

    mockMvc
        .perform(
            post("/api/cart/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"items\": [{\"variantId\": %d, \"quantity\": 2}, {\"variantId\": %d,"
                            .formatted(variant, variant)
                        + " \"quantity\": 2}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].quantity").value(3))
        .andExpect(jsonPath("$.subtotal").value(360_000))
        .andExpect(jsonPath("$.notices[0].type").value("QUANTITY_REDUCED"));

    assertThat(count("SELECT COUNT(*) FROM cart_item")).isEqualTo(before);
  }

  @Test
  void cartNeedsCustomerToken() throws Exception {
    mockMvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/cart")
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder addItem(long variantId, int quantity) {
    return post("/api/cart/items")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"variantId\": %d, \"quantity\": %d}".formatted(variantId, quantity));
  }

  private ResultActions perform(long accountId, MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private long account() {
    Account account =
        accountRepository.save(
            new Account(
                "gio-hang-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  private long product(long price, int stock) {
    ProductResponse product =
        productService.create(
            new ProductCreateRequest(
                "Sản phẩm giỏ hàng " + UUID.randomUUID(),
                null,
                null,
                null,
                categoryId,
                null,
                List.of(),
                List.of(new VariantRequest(null, null, price, stock, null))));
    productIds.add(product.id());
    return product.id();
  }

  private long variantOf(long productId) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM product_variant WHERE product_id = ?", Long.class, productId);
  }

  private long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, Map.of("ids", ids));
    }
  }
}
