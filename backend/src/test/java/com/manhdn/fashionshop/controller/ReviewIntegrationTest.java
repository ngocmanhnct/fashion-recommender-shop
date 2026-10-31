package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
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
 * Kiểm tra API đánh giá sản phẩm (US-15, BR-17, BR-18) với MySQL thật.
 *
 * <p>Không dùng {@code @Transactional}: gửi đánh giá chạy qua TransactionRetry, không gọi được
 * trong giao dịch khác. Mọi dòng test tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

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
    categoryId = categoryService.create("Danh mục đánh giá " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    deleteWhere("DELETE FROM review WHERE account_id IN (:ids)", accountIds);
    deleteWhere(
        "DELETE i FROM order_item i JOIN orders o ON o.id = i.order_id"
            + " WHERE o.account_id IN (:ids)",
        accountIds);
    deleteWhere("DELETE FROM orders WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  @Test
  void deliveredBuyersReviewEditAndEveryoneSeesSummary() throws Exception {
    long product = product();
    long first = account("Đoàn Ngọc Mạnh");
    long second = account("Trần Văn Bình");
    deliver(first, product, "DELIVERED");
    deliver(second, product, "DELIVERED");

    perform(first, get(mine(product)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canReview").value(true))
        .andExpect(jsonPath("$.review").isEmpty());
    perform(first, review(product, "{\"rating\": 4, \"comment\": \"  Rất đẹp  \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(4))
        .andExpect(jsonPath("$.comment").value("Rất đẹp"))
        .andExpect(jsonPath("$.reviewerName").value("Mạnh"));
    // Gửi lại là sửa đánh giá cũ (BR-18); nhận xét trống lưu NULL.
    perform(first, review(product, "{\"rating\": 5, \"comment\": \"   \"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rating").value(5))
        .andExpect(jsonPath("$.comment").isEmpty());
    perform(second, review(product, "{\"rating\": 2}")).andExpect(status().isOk());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM review WHERE product_id = ?", Long.class, product))
        .isEqualTo(2);

    mockMvc
        .perform(get(reviews(product)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ratingAverage").value(3.5))
        .andExpect(jsonPath("$.reviewCount").value(2))
        .andExpect(jsonPath("$.starCounts[0]").value(0))
        .andExpect(jsonPath("$.starCounts[1]").value(1))
        .andExpect(jsonPath("$.starCounts[4]").value(1))
        .andExpect(jsonPath("$.reviews.totalElements").value(2))
        .andExpect(jsonPath("$.reviews.content[0].reviewerName").value("Bình"))
        .andExpect(jsonPath("$.reviews.content[0].variantLabel").isEmpty())
        .andExpect(jsonPath("$.reviews.content[1].rating").value(5));
    mockMvc
        .perform(get(reviews(product)).param("size", "1").param("page", "1"))
        .andExpect(jsonPath("$.reviews.content.length()").value(1))
        .andExpect(jsonPath("$.reviews.content[0].reviewerName").value("Mạnh"));
    perform(first, get(mine(product)))
        .andExpect(jsonPath("$.review.rating").value(5))
        .andExpect(jsonPath("$.canReview").value(true));
  }

  @Test
  void onlyDeliveredProductsCanBeReviewed() throws Exception {
    long product = product();
    long buyer = account("Khách Chưa Nhận");
    deliver(buyer, product, "SHIPPING");
    deliver(buyer, product, "CANCELLED");
    deliver(buyer, product(), "DELIVERED");

    perform(buyer, get(mine(product))).andExpect(jsonPath("$.canReview").value(false));
    perform(buyer, review(product, "{\"rating\": 5}"))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Chỉ đánh giá được sản phẩm thuộc đơn hàng đã giao cho bạn"));
    mockMvc
        .perform(get(reviews(product)))
        .andExpect(jsonPath("$.ratingAverage").isEmpty())
        .andExpect(jsonPath("$.reviewCount").value(0));
  }

  @Test
  void invalidInputAndDeletedProductsAreRejected() throws Exception {
    long product = product();
    long buyer = account("Khách Thử");
    deliver(buyer, product, "DELIVERED");

    perform(buyer, review(product, "{\"rating\": 0}")).andExpect(status().isBadRequest());
    perform(buyer, review(product, "{\"rating\": 6}")).andExpect(status().isBadRequest());
    perform(buyer, review(product, "{\"comment\": \"Thiếu sao\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.rating").value("Hãy chọn số sao"));
    perform(buyer, review(product, "{\"rating\": 3, \"comment\": \"" + "a".repeat(1001) + "\"}"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get(reviews(product)).param("page", "-1")).andExpect(status().isBadRequest());
    mockMvc.perform(get(reviews(product)).param("size", "21")).andExpect(status().isBadRequest());

    productService.delete(product);
    mockMvc.perform(get(reviews(product))).andExpect(status().isNotFound());
    perform(buyer, review(product, "{\"rating\": 5}")).andExpect(status().isNotFound());
  }

  @Test
  void ownReviewIsForCustomersOnly() throws Exception {
    mockMvc.perform(get(mine(1))).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            review(1, "{\"rating\": 5}")
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  private static String reviews(long productId) {
    return "/api/products/" + productId + "/reviews";
  }

  private static String mine(long productId) {
    return reviews(productId) + "/me";
  }

  private static MockHttpServletRequestBuilder review(long productId, String body) {
    return put(mine(productId)).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private void deliver(long accountId, long productId, String status) {
    long variant =
        jdbcTemplate.queryForObject(
            "SELECT id FROM product_variant WHERE product_id = ?", Long.class, productId);
    insertOrderItem(jdbcTemplate, insertOrder(jdbcTemplate, accountId, status), variant, 1);
  }

  private ResultActions perform(long accountId, MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private long account(String fullName) {
    Account account =
        accountRepository.save(
            new Account(
                "danh-gia-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                fullName,
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  private long product() {
    long id =
        productService
            .create(
                new ProductCreateRequest(
                    "Sản phẩm đánh giá " + UUID.randomUUID(),
                    null,
                    null,
                    null,
                    categoryId,
                    null,
                    List.of(),
                    List.of(new VariantRequest(null, null, 90_000L, 5, null))))
            .id();
    productIds.add(id);
    return id;
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, Map.of("ids", ids));
    }
  }
}
