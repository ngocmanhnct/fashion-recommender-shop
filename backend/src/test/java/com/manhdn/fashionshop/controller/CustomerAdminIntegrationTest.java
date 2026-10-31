package com.manhdn.fashionshop.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra API xem khách hàng (US-18) và kết quả phân nhóm (US-19) với MySQL thật; dữ liệu phân
 * nhóm ghi thẳng bằng SQL như ml-service. Mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomerAdminIntegrationTest {

  private static final String CUSTOMERS = "/api/admin/customers";
  private static final String SEGMENTS = "/api/admin/segments";

  @Autowired private MockMvc mockMvc;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  /** Chuỗi riêng của từng lần chạy, để tìm đúng khách do test tạo. */
  private String marker;

  private long vip;
  private long buyer;
  private long newcomer;

  @BeforeEach
  void setUp() {
    // CSDL test có thể còn kết quả phân nhóm cũ; test rollback nên dữ liệu cũ không mất.
    jdbcTemplate.update("DELETE FROM account_segment");
    jdbcTemplate.update("DELETE FROM customer_segment");
    jdbcTemplate.update("DELETE FROM segment_model");
    marker = UUID.randomUUID().toString().substring(0, 8);
    vip = customer("Trần Vy " + marker, "vy");
    buyer = customer("Lê Khang " + marker, "khang");
    newcomer = customer("Phạm Mới " + marker, "moi");

    long delivered = OrderFixtures.insertOrder(jdbcTemplate, vip, "DELIVERED");
    jdbcTemplate.update("UPDATE orders SET total_amount = 1500000 WHERE id = ?", delivered);
    OrderFixtures.insertOrder(jdbcTemplate, vip, "CANCELLED");
    OrderFixtures.insertOrder(jdbcTemplate, buyer, "PENDING");

    long model =
        insert(
            "INSERT INTO segment_model (cluster_count, elbow_cluster_count, customer_count,"
                + " silhouette, log_recency_mean, log_recency_std, log_frequency_mean,"
                + " log_frequency_std, log_monetary_mean, log_monetary_std, trained_at)"
                + " VALUES (2, 2, 40, 0.42, 3, 1, 1, 0.5, 13, 1, '2026-10-28 02:00:00')");
    segment(model, "NEW", "Khách mới"); // nhóm chưa có khách nào
    long vipSegment = segment(model, "VIP", "Khách VIP");
    assign(vip, vipSegment, 3, 1, 1_500_000);
    // 1 khách khác cũng thuộc nhóm VIP để kiểm tra số trung bình.
    assign(customer("Khách khác " + marker, "khac"), vipSegment, 7, 3, 4_500_000);
  }

  @Test
  void listShowsCustomersWithOrderCountAndSegmentNewestFirst() throws Exception {
    admin(get(CUSTOMERS).param("keyword", marker))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(4))
        .andExpect(jsonPath("$.content[3].id").value(vip))
        .andExpect(jsonPath("$.content[3].orderCount").value(2))
        .andExpect(jsonPath("$.content[3].segment.code").value("VIP"))
        .andExpect(jsonPath("$.content[3].segment.name").value("Khách VIP"))
        .andExpect(jsonPath("$.content[2].id").value(buyer))
        .andExpect(jsonPath("$.content[2].orderCount").value(1))
        .andExpect(jsonPath("$.content[2].segment").doesNotExist());
  }

  @Test
  void listFiltersByNameEmailAndSegment() throws Exception {
    // Họ tên khớp không phân biệt dấu (collation của MySQL).
    admin(get(CUSTOMERS).param("keyword", "pham moi " + marker))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(newcomer));
    admin(get(CUSTOMERS).param("keyword", "khang-" + marker))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(buyer));
    admin(get(CUSTOMERS).param("keyword", marker).param("segment", "VIP"))
        .andExpect(jsonPath("$.totalElements").value(2));
    admin(get(CUSTOMERS).param("keyword", marker).param("segment", "NONE"))
        .andExpect(jsonPath("$.totalElements").value(2));
    admin(get(CUSTOMERS).param("keyword", marker).param("segment", "NEW"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void listRejectsBadParameters() throws Exception {
    admin(get(CUSTOMERS).param("segment", "vip; drop")).andExpect(status().isBadRequest());
    admin(get(CUSTOMERS).param("size", "51")).andExpect(status().isBadRequest());
    admin(get(CUSTOMERS).param("keyword", "x".repeat(101))).andExpect(status().isBadRequest());
  }

  @Test
  void detailShowsOrderTotalsAndSegment() throws Exception {
    admin(get(CUSTOMERS + "/" + vip))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fullName").value("Trần Vy " + marker))
        .andExpect(jsonPath("$.orderCount").value(2))
        .andExpect(jsonPath("$.deliveredOrderCount").value(1))
        .andExpect(jsonPath("$.deliveredTotal").value(1_500_000))
        .andExpect(jsonPath("$.segment.code").value("VIP"))
        .andExpect(jsonPath("$.segment.description").value("Mô tả Khách VIP"))
        .andExpect(jsonPath("$.segment.recency").value(3))
        .andExpect(jsonPath("$.segment.frequency").value(1))
        .andExpect(jsonPath("$.segment.monetary").value(1_500_000));
    admin(get(CUSTOMERS + "/" + newcomer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.orderCount").value(0))
        .andExpect(jsonPath("$.deliveredTotal").value(0))
        .andExpect(jsonPath("$.segment").doesNotExist());
  }

  @Test
  void ordersArePagedNewestFirst() throws Exception {
    admin(get(CUSTOMERS + "/" + vip + "/orders").param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content.length()").value(1));
    admin(get(CUSTOMERS + "/" + vip + "/orders").param("page", "-1"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownOrAdminAccountIsNotFound() throws Exception {
    long adminId =
        accountRepository
            .save(new Account("qtv-" + marker + "@test.local", "x", "Quản trị", Role.ADMIN))
            .getId();
    admin(get(CUSTOMERS + "/" + adminId)).andExpect(status().isNotFound());
    admin(get(CUSTOMERS + "/999999999")).andExpect(status().isNotFound());
    admin(get(CUSTOMERS + "/999999999/orders")).andExpect(status().isNotFound());
  }

  @Test
  void segmentsShowModelGroupsInValueOrderAndUnsegmentedCount() throws Exception {
    long customers = accountRepository.countByRole(Role.CUSTOMER);
    admin(get(SEGMENTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model.clusterCount").value(2))
        .andExpect(jsonPath("$.model.silhouette").value(0.42))
        .andExpect(jsonPath("$.model.trainedAt").value("2026-10-28T02:00:00"))
        .andExpect(jsonPath("$.segments[0].code").value("VIP"))
        .andExpect(
            jsonPath("$.segments[0].goal").value("Giữ chân nhóm mang lại nhiều doanh thu nhất"))
        .andExpect(jsonPath("$.segments[0].customerCount").value(2))
        .andExpect(jsonPath("$.segments[0].share").value(100.0))
        .andExpect(jsonPath("$.segments[0].avgRecency").value(5.0))
        .andExpect(jsonPath("$.segments[0].avgFrequency").value(2.0))
        .andExpect(jsonPath("$.segments[0].avgMonetary").value(3_000_000))
        .andExpect(jsonPath("$.segments[0].revenue").value(1_500_000))
        .andExpect(jsonPath("$.segments[0].revenueShare").value(100.0))
        .andExpect(jsonPath("$.segments[1].code").value("NEW"))
        .andExpect(jsonPath("$.segments[1].customerCount").value(0))
        .andExpect(jsonPath("$.segments[1].revenue").value(0))
        .andExpect(jsonPath("$.segmentedCount").value(2))
        .andExpect(jsonPath("$.unsegmentedCount").value(customers - 2));
  }

  @Test
  void segmentsBeforeFirstTrainingAreEmpty() throws Exception {
    jdbcTemplate.update("DELETE FROM account_segment");
    jdbcTemplate.update("DELETE FROM customer_segment");
    jdbcTemplate.update("DELETE FROM segment_model");
    admin(get(SEGMENTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.model").doesNotExist())
        .andExpect(jsonPath("$.segments.length()").value(0))
        .andExpect(jsonPath("$.segmentedCount").value(0));
  }

  @Test
  void customerApisAreForAdminsOnly() throws Exception {
    mockMvc.perform(get(CUSTOMERS)).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get(SEGMENTS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
  }

  // ---------- Tiện ích ----------

  private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private long customer(String fullName, String emailPrefix) {
    return accountRepository
        .save(
            new Account(
                emailPrefix + "-" + marker + "@test.local", "khong-dung", fullName, Role.CUSTOMER))
        .getId();
  }

  private long segment(long modelId, String code, String name) {
    return insert(
        "INSERT INTO customer_segment (model_id, code, name, description, center_recency,"
            + " center_frequency, center_monetary, computed_at)"
            + " VALUES ("
            + modelId
            + ", '"
            + code
            + "', '"
            + name
            + "', 'Mô tả "
            + name
            + "', 0, 0, 0, NOW())");
  }

  private void assign(long accountId, long segmentId, int recency, int frequency, long monetary) {
    jdbcTemplate.update(
        "INSERT INTO account_segment (account_id, segment_id, rfm_recency, rfm_frequency,"
            + " rfm_monetary, computed_at) VALUES (?, ?, ?, ?, ?, ?)",
        accountId,
        segmentId,
        recency,
        frequency,
        monetary,
        LocalDateTime.now());
  }

  private long insert(String sql) {
    jdbcTemplate.update(sql);
    Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return id == null ? 0 : id;
  }
}
