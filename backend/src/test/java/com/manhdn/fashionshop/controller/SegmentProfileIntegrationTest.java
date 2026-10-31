package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Kiểm tra API hồ sơ nhóm khách hàng (US-25) và danh sách khách cần chăm sóc (BR-43) với MySQL
 * thật; dữ liệu phân nhóm ghi thẳng bằng SQL như ml-service. Mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SegmentProfileIntegrationTest {

  private static final String PROFILE = "/api/admin/segments/";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String marker;
  private long loyal;
  private long regular;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM account_segment");
    jdbcTemplate.update("DELETE FROM customer_segment");
    jdbcTemplate.update("DELETE FROM segment_model");
    marker = UUID.randomUUID().toString().substring(0, 8);
    // Cột DATETIME làm tròn phần lẻ của giây (có thể lên giây sau), nên bỏ phần lẻ trước khi ghi.
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    LocalDate today = now.toLocalDate();
    long variant = variant("Giày hồ sơ " + marker);
    long otherVariant = variant("Túi hồ sơ " + marker);

    // Khách trung thành: 3 đơn cách nhau 20 ngày, đơn gần nhất 60 ngày trước, nên đã quá nhịp.
    loyal = customer("Nguyễn Lan " + marker, "lan", "FEMALE", today.minusYears(30));
    long first = delivered(loyal, 200_000, now.minusDays(100));
    insertOrderItem(jdbcTemplate, first, variant, 3);
    delivered(loyal, 200_000, now.minusDays(80));
    delivered(loyal, 200_000, now.minusDays(60));
    // Khách thường: 2 đơn cách nhau 10 ngày, vừa mua hôm nay; đơn đã hủy không tính.
    regular = customer("Võ Minh " + marker, "minh", "MALE", today.minusYears(20));
    long second = delivered(regular, 100_000, now.minusDays(10));
    insertOrderItem(jdbcTemplate, second, variant, 1);
    insertOrderItem(jdbcTemplate, delivered(regular, 100_000, now), otherVariant, 1);
    insertOrder(jdbcTemplate, regular, "CANCELLED");
    // Khách chưa mua, chưa khai giới tính, ngày sinh.
    long silent = customer("Đỗ Im " + marker, "im", null, null);
    // Khách nhóm khác, để tính tỷ lệ doanh thu và tỷ lệ khách.
    long other = customer("Khách nhóm khác " + marker, "khac", null, null);
    delivered(other, 500_000, now.minusDays(5));

    long model =
        insert(
            "INSERT INTO segment_model (cluster_count, elbow_cluster_count, customer_count,"
                + " silhouette, log_recency_mean, log_recency_std, log_frequency_mean,"
                + " log_frequency_std, log_monetary_mean, log_monetary_std, trained_at)"
                + " VALUES (2, 2, 40, 0.42, 3, 1, 1, 0.5, 13, 1, '2026-10-28 02:00:00')");
    long loyalSegment = segment(model, "LOYAL", "Khách trung thành");
    long newSegment = segment(model, "NEW", "Khách mới");
    assign(loyal, loyalSegment);
    assign(regular, loyalSegment);
    assign(silent, loyalSegment);
    assign(other, newSegment);
  }

  @Test
  void profileSummarizesRevenueRhythmAndCategories() throws Exception {
    admin(PROFILE + "LOYAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("LOYAL"))
        .andExpect(jsonPath("$.name").value("Khách trung thành"))
        .andExpect(jsonPath("$.customerCount").value(3))
        .andExpect(jsonPath("$.customerShare").value(75.0))
        .andExpect(jsonPath("$.revenue").value(800_000))
        .andExpect(jsonPath("$.revenueShare").value(61.5))
        .andExpect(jsonPath("$.deliveredOrders").value(5))
        .andExpect(jsonPath("$.avgOrderValue").value(160_000))
        .andExpect(jsonPath("$.repeatCustomers").value(2))
        .andExpect(jsonPath("$.avgPurchaseIntervalDays").value(15.0))
        .andExpect(jsonPath("$.avgTenureDays").value(55.0))
        .andExpect(jsonPath("$.topCategories.length()").value(2))
        .andExpect(jsonPath("$.topCategories[0].quantity").value(4))
        .andExpect(jsonPath("$.topCategories[0].share").value(80.0))
        .andExpect(jsonPath("$.topCategories[1].quantity").value(1));
  }

  @Test
  void profileSplitsCustomersByGenderAndAge() throws Exception {
    admin(PROFILE + "LOYAL")
        .andExpect(jsonPath("$.genders.length()").value(4))
        .andExpect(jsonPath("$.genders[0].key").value("MALE"))
        .andExpect(jsonPath("$.genders[0].count").value(1))
        .andExpect(jsonPath("$.genders[0].share").value(33.3))
        .andExpect(jsonPath("$.genders[1].key").value("FEMALE"))
        .andExpect(jsonPath("$.genders[2].count").value(0))
        .andExpect(jsonPath("$.genders[3].key").value("UNKNOWN"))
        .andExpect(jsonPath("$.genders[3].count").value(1))
        .andExpect(jsonPath("$.ageGroups.length()").value(7))
        .andExpect(jsonPath("$.ageGroups[1].key").value("18_24"))
        .andExpect(jsonPath("$.ageGroups[1].count").value(1))
        .andExpect(jsonPath("$.ageGroups[2].key").value("25_34"))
        .andExpect(jsonPath("$.ageGroups[2].count").value(1))
        .andExpect(jsonPath("$.ageGroups[6].key").value("UNKNOWN"))
        .andExpect(jsonPath("$.ageGroups[6].count").value(1));
  }

  @Test
  void careListHasRepeatBuyersPastTheirOwnRhythm() throws Exception {
    admin(PROFILE + "LOYAL")
        .andExpect(jsonPath("$.careCount").value(1))
        .andExpect(jsonPath("$.careList[0].accountId").value(loyal))
        .andExpect(jsonPath("$.careList[0].fullName").value("Nguyễn Lan " + marker))
        .andExpect(jsonPath("$.careList[0].orders").value(3))
        .andExpect(jsonPath("$.careList[0].purchaseIntervalDays").value(20.0))
        .andExpect(jsonPath("$.careList[0].daysSinceLastOrder").value(60));
  }

  @Test
  void segmentWithoutRepeatBuyersHasNoRhythm() throws Exception {
    admin(PROFILE + "NEW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerCount").value(1))
        .andExpect(jsonPath("$.revenueShare").value(38.5))
        .andExpect(jsonPath("$.avgPurchaseIntervalDays").doesNotExist())
        .andExpect(jsonPath("$.topCategories.length()").value(0))
        .andExpect(jsonPath("$.careCount").value(0));
  }

  @Test
  void unknownSegmentIsNotFoundAndCustomersAreForbidden() throws Exception {
    admin(PROFILE + "KHONG_CO").andExpect(status().isNotFound());
    mockMvc.perform(get(PROFILE + "LOYAL")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get(PROFILE + "LOYAL")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  // ---------- Tiện ích ----------

  private ResultActions admin(String path) throws Exception {
    return mockMvc.perform(
        get(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private long customer(String fullName, String prefix, String gender, LocalDate birthdate) {
    long id =
        accountRepository
            .save(
                new Account(
                    prefix + "-" + marker + "@test.local", "khong-dung", fullName, Role.CUSTOMER))
            .getId();
    jdbcTemplate.update(
        "UPDATE account SET gender = ?, birthdate = ? WHERE id = ?", gender, birthdate, id);
    return id;
  }

  private long delivered(long accountId, long total, LocalDateTime createdAt) {
    long id = insertOrder(jdbcTemplate, accountId, "DELIVERED");
    jdbcTemplate.update(
        "UPDATE orders SET total_amount = ?, created_at = ? WHERE id = ?", total, createdAt, id);
    return id;
  }

  private long variant(String categoryName) {
    long categoryId = categoryService.create(categoryName).id();
    long productId =
        productService
            .create(
                new ProductCreateRequest(
                    "Sản phẩm " + categoryName,
                    null,
                    null,
                    null,
                    categoryId,
                    null,
                    List.of(),
                    List.of(new VariantRequest(null, null, 90_000L, 5, null))))
            .id();
    Long id =
        jdbcTemplate.queryForObject(
            "SELECT id FROM product_variant WHERE product_id = ?", Long.class, productId);
    return id == null ? 0 : id;
  }

  private long segment(long modelId, String code, String name) {
    jdbcTemplate.update(
        "INSERT INTO customer_segment (model_id, code, name, description, center_recency,"
            + " center_frequency, center_monetary, computed_at)"
            + " VALUES (?, ?, ?, ?, 0, 0, 0, NOW())",
        modelId,
        code,
        name,
        "Mô tả " + name);
    return lastId();
  }

  private void assign(long accountId, long segmentId) {
    jdbcTemplate.update(
        "INSERT INTO account_segment (account_id, segment_id, rfm_recency, rfm_frequency,"
            + " rfm_monetary, computed_at) VALUES (?, ?, 1, 1, 1, ?)",
        accountId,
        segmentId,
        LocalDateTime.now());
  }

  private long insert(String sql) {
    jdbcTemplate.update(sql);
    return lastId();
  }

  private long lastId() {
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0 : id;
  }
}
