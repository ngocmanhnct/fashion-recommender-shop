package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra nhật ký lượt hiện, lượt bấm gợi ý (product_event_log) và tỷ lệ bấm cho Quản trị viên với
 * MySQL thật; mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecommendationEventIntegrationTest {

  private static final String EVENTS = "/api/recommendations/events";
  private static final String CTR = "/api/admin/recommendations/ctr";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private long first;
  private long second;

  @BeforeEach
  void setUp() {
    // Đếm cho đúng dù CSDL test còn sự kiện cũ; test rollback nên dữ liệu cũ không mất.
    jdbcTemplate.update("DELETE FROM product_event_log WHERE event_type <> 'ADD_TO_CART'");
    long category = categoryService.create("Danh mục Sự kiện " + UUID.randomUUID()).id();
    first = product(category, "Sự kiện 1");
    second = product(category, "Sự kiện 2");
  }

  @Test
  void customerShownEventWritesOneRowPerProductWithPlacementSourceAndRank() throws Exception {
    long account = account();
    customer(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body("SHOWN", "HOME", item(first, "BEHAVIOR", 1), item(second, "BEHAVIOR", 2))),
            account)
        .andExpect(status().isNoContent());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT account_id, product_id, event_type, placement, source, rank_position"
                + " FROM product_event_log WHERE event_type <> 'ADD_TO_CART' ORDER BY rank_position");
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0))
        .containsEntry("event_type", "RECOMMENDATION_SHOWN")
        .containsEntry("placement", "HOME")
        .containsEntry("source", "BEHAVIOR");
    assertThat(((Number) rows.get(0).get("account_id")).longValue()).isEqualTo(account);
    assertThat(((Number) rows.get(1).get("product_id")).longValue()).isEqualTo(second);
    assertThat(((Number) rows.get(1).get("rank_position")).intValue()).isEqualTo(2);
  }

  @Test
  void guestClickIsLoggedWithoutAccountAndUnknownProductsAreSkipped() throws Exception {
    mockMvc
        .perform(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body(
                        "CLICKED",
                        "PRODUCT_DETAIL",
                        item(first, "RELATED", 3),
                        item(999_999_999L, "RELATED", 4))))
        .andExpect(status().isNoContent());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT account_id, event_type FROM product_event_log"
                + " WHERE event_type <> 'ADD_TO_CART'");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("account_id")).isNull();
    assertThat(rows.get(0)).containsEntry("event_type", "RECOMMENDATION_CLICKED");
  }

  @Test
  void adminEventsAreIgnored() throws Exception {
    mockMvc
        .perform(
            post(EVENTS)
                .with(
                    jwt()
                        .jwt(j -> j.subject("1").claim("role", "ADMIN"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SHOWN", "HOME", item(first, "POPULAR", 1))))
        .andExpect(status().isNoContent());

    assertThat(recommendationEvents()).isZero();
  }

  @Test
  void invalidEventsAreRejected() throws Exception {
    mockMvc
        .perform(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SHOWN", "CART_PAGE", item(first, "POPULAR", 1))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.placement").exists());
    mockMvc
        .perform(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("OPENED", "HOME", item(first, "popular source", 0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.type").exists());
    String[] many = new String[21];
    for (int i = 0; i < many.length; i++) {
      many[i] = item(first, "POPULAR", i + 1);
    }
    mockMvc
        .perform(
            post(EVENTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("SHOWN", "HOME", many)))
        .andExpect(status().isBadRequest());

    assertThat(recommendationEvents()).isZero();
  }

  @Test
  void ctrGroupsByPlacementAndSourceWithinTheDateRange() throws Exception {
    LocalDateTime now = LocalDateTime.now();
    insert("RECOMMENDATION_SHOWN", "HOME", "BEHAVIOR", 4, now);
    insert("RECOMMENDATION_CLICKED", "HOME", "BEHAVIOR", 1, now);
    insert("RECOMMENDATION_SHOWN", "PRODUCT_DETAIL", "RELATED", 4, now);
    insert("RECOMMENDATION_SHOWN", "HOME", "BEHAVIOR", 10, now.minusDays(40));
    String today = LocalDate.now().toString();

    admin(get(CTR).param("from", today).param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value(today))
        .andExpect(jsonPath("$.total.shown").value(8))
        .andExpect(jsonPath("$.total.clicked").value(1))
        .andExpect(jsonPath("$.total.ctr").value(12.5))
        .andExpect(jsonPath("$.rows.length()").value(2))
        .andExpect(jsonPath("$.rows[0].placement").value("HOME"))
        .andExpect(jsonPath("$.rows[0].source").value("BEHAVIOR"))
        .andExpect(jsonPath("$.rows[0].ctr").value(25.0))
        .andExpect(jsonPath("$.rows[1].placement").value("PRODUCT_DETAIL"))
        .andExpect(jsonPath("$.rows[1].clicked").value(0))
        .andExpect(jsonPath("$.rows[1].ctr").value(0.0));
    admin(get(CTR)).andExpect(status().isOk()).andExpect(jsonPath("$.total.shown").value(8));
    admin(get(CTR).param("from", "2026-10-20").param("to", "2026-10-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ctrIsForAdminsOnly() throws Exception {
    mockMvc.perform(get(CTR)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(CTR).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  // ---------- Tiện ích ----------

  private ResultActions customer(MockHttpServletRequestBuilder request, long accountId)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)).claim("role", "CUSTOMER"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

  private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private static String body(String type, String placement, String... items) {
    return "{\"type\":\""
        + type
        + "\",\"placement\":\""
        + placement
        + "\",\"items\":["
        + String.join(",", items)
        + "]}";
  }

  private static String item(long productId, String source, int rank) {
    return "{\"productId\":" + productId + ",\"source\":\"" + source + "\",\"rank\":" + rank + "}";
  }

  /** Ghi n sự kiện của sản phẩm đầu tiên vào thời điểm cho trước. */
  private void insert(String type, String placement, String source, int count, LocalDateTime at) {
    for (int i = 1; i <= count; i++) {
      jdbcTemplate.update(
          "INSERT INTO product_event_log"
              + " (account_id, product_id, event_type, placement, source, rank_position, created_at)"
              + " VALUES (NULL, ?, ?, ?, ?, ?, ?)",
          first,
          type,
          placement,
          source,
          i,
          at);
    }
  }

  private int recommendationEvents() {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM product_event_log WHERE event_type <> 'ADD_TO_CART'",
            Integer.class);
    return count == null ? 0 : count;
  }

  private long account() {
    return accountRepository
        .save(
            new Account(
                "su-kien-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER))
        .getId();
  }

  private long product(long categoryId, String name) {
    return productService
        .create(
            new ProductCreateRequest(
                name,
                null,
                null,
                null,
                categoryId,
                null,
                List.of(),
                List.of(new VariantRequest(null, null, 300_000L, 1, null))))
        .id();
  }
}
