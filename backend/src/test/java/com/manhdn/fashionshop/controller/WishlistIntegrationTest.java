package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.manhdn.fashionshop.service.WishlistService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Kiểm tra API danh sách yêu thích (US-14) với MySQL thật.
 *
 * <p>Không dùng {@code @Transactional}: yêu cầu tính lại gợi ý chỉ được ghi ngay trước khi giao
 * dịch commit. Mọi dòng test tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class WishlistIntegrationTest {

  private static final String WISHLIST = "/api/wishlist";

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
    categoryId = categoryService.create("Danh mục yêu thích " + UUID.randomUUID()).id();
  }

  @AfterEach
  void cleanUp() {
    deleteWhere("DELETE FROM recompute_request WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM wishlist_item WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    jdbcTemplate.update("DELETE FROM category WHERE id = ?", categoryId);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  @Test
  void addingTwiceKeepsOneRowListsNewestFirstAndRequestsRecompute() throws Exception {
    long account = account();
    long first = product(5);
    long soldOut = product(0);

    perform(account, put(WISHLIST + "/" + first)).andExpect(status().isOk());
    perform(account, put(WISHLIST + "/" + soldOut))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    perform(account, put(WISHLIST + "/" + first))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0]").value(Math.min(first, soldOut)))
        .andExpect(jsonPath("$[1]").value(Math.max(first, soldOut)));

    perform(account, get(WISHLIST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].product.id").value(soldOut))
        .andExpect(jsonPath("$[0].product.inStock").value(false))
        .andExpect(jsonPath("$[0].addedAt").exists())
        .andExpect(jsonPath("$[1].product.id").value(first))
        .andExpect(jsonPath("$[1].product.minPrice").value(120_000));
    assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE account_id = ?", account))
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
  void removingIsIdempotentAndDeletedProductsDisappear() throws Exception {
    long account = account();
    long kept = product(5);
    long removed = product(5);
    perform(account, put(WISHLIST + "/" + kept)).andExpect(status().isOk());
    perform(account, put(WISHLIST + "/" + removed)).andExpect(status().isOk());

    perform(account, delete(WISHLIST + "/" + removed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0]").value(kept));
    perform(account, delete(WISHLIST + "/" + removed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    productService.delete(kept);
    perform(account, get(WISHLIST + "/ids"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    perform(account, get(WISHLIST)).andExpect(jsonPath("$.length()").value(0));
    perform(account, put(WISHLIST + "/" + kept)).andExpect(status().isNotFound());
    perform(account, put(WISHLIST + "/999999999")).andExpect(status().isNotFound());
    // Dòng của sản phẩm đã ngừng bán vẫn giữ trong CSDL.
    assertThat(count("SELECT COUNT(*) FROM wishlist_item WHERE account_id = ?", account))
        .isEqualTo(1);
  }

  @Test
  void wishlistIsLimitedTo100Products() throws Exception {
    long account = account();
    for (int i = 0; i < WishlistService.MAX_ITEMS; i++) {
      long product = product(1);
      jdbcTemplate.update(
          "INSERT INTO wishlist_item (account_id, product_id, created_at) VALUES (?, ?, NOW())",
          account,
          product);
    }

    perform(account, put(WISHLIST + "/" + product(1)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value("Danh sách yêu thích đã đủ 100 sản phẩm, hãy bỏ bớt trước khi thêm"));
    // Thêm lại sản phẩm đã có vẫn được khi danh sách đầy.
    perform(account, put(WISHLIST + "/" + productIds.get(0))).andExpect(status().isOk());
  }

  @Test
  void wishlistIsForCustomersOnly() throws Exception {
    mockMvc.perform(get(WISHLIST)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            put(WISHLIST + "/1")
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
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
                "yeu-thich-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  private long product(int stock) {
    long id =
        productService
            .create(
                new ProductCreateRequest(
                    "Sản phẩm yêu thích " + UUID.randomUUID(),
                    null,
                    null,
                    null,
                    categoryId,
                    null,
                    List.of(),
                    List.of(new VariantRequest(null, null, 120_000L, stock, null))))
            .id();
    productIds.add(id);
    return id;
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
