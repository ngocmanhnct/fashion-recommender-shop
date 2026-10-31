package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra trang tổng quan của Quản trị viên (US-28, BR-47) với MySQL thật. Dữ liệu đặt vào các
 * ngày năm 2031 để không lẫn với dữ liệu khác trong CSDL test. Mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardIntegrationTest {

  private static final String DASHBOARD = "/api/admin/dashboard";
  private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private long category;

  @BeforeEach
  void setUp() {
    // CSDL test dùng chung giữa các lần chạy: dừng thử nghiệm còn sót (nếu có) trong giao dịch này.
    jdbcTemplate.update("UPDATE ab_experiment SET ended_at = NOW() WHERE ended_at IS NULL");
    category = categoryService.create("Danh mục tổng quan " + UUID.randomUUID()).id();
  }

  @Test
  void overviewCountsDeliveredRevenueByVietnamDayStatusesTopProductsAndRecommendations()
      throws Exception {
    long buyer = account(vn(2031, 3, 2, 9));
    account(vn(2031, 2, 27, 9)); // đăng ký trước khoảng: không tính
    ProductResponse shoe = product("Giày tổng quan");
    ProductResponse sock = product("Vớ tổng quan");

    // 01:00 ngày 3/3 giờ Việt Nam (máy chủ UTC thấy là 18:00 ngày 2/3) vẫn là doanh thu ngày 3/3.
    long early = order(buyer, "DELIVERED", 230_000L, vn(2031, 3, 3, 1));
    insertOrderItem(jdbcTemplate, early, shoe.variants().get(0).id(), 2);
    long noon = order(buyer, "DELIVERED", 100_000L, vn(2031, 3, 3, 12));
    insertOrderItem(jdbcTemplate, noon, sock.variants().get(0).id(), 5);
    order(buyer, "PENDING", 500_000L, vn(2031, 3, 1, 10));
    order(buyer, "CANCELLED", 900_000L, vn(2031, 3, 2, 10));
    // Ngoài khoảng: không tính.
    order(buyer, "DELIVERED", 700_000L, vn(2031, 3, 6, 0));
    jdbcTemplate.update("UPDATE orders SET subtotal_amount = 200000 WHERE id = ?", early);
    jdbcTemplate.update("UPDATE orders SET subtotal_amount = 100000 WHERE id = ?", noon);

    // Bấm giày từ gợi ý 2 ngày trước đơn: tính; bấm vớ 10 ngày trước đơn: quá 7 ngày, không tính.
    event(buyer, shoe.id(), "RECOMMENDATION_SHOWN", "HOME", "BEHAVIOR", vn(2031, 3, 1, 8));
    event(buyer, shoe.id(), "RECOMMENDATION_CLICKED", "HOME", "BEHAVIOR", vn(2031, 3, 1, 8));
    event(buyer, sock.id(), "RECOMMENDATION_SHOWN", "PRODUCT_DETAIL", "RELATED", vn(2031, 3, 2, 8));
    event(buyer, sock.id(), "RECOMMENDATION_CLICKED", "HOME", "BEHAVIOR", vn(2031, 2, 21, 8));

    admin(get(DASHBOARD).param("from", "2031-03-01").param("to", "2031-03-05"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2031-03-01"))
        .andExpect(jsonPath("$.to").value("2031-03-05"))
        .andExpect(jsonPath("$.summary.revenue").value(330_000))
        .andExpect(jsonPath("$.summary.deliveredOrders").value(2))
        .andExpect(jsonPath("$.summary.orders").value(4))
        .andExpect(jsonPath("$.summary.averageOrderValue").value(165_000))
        .andExpect(jsonPath("$.summary.newCustomers").value(1))
        .andExpect(jsonPath("$.summary.goodsRevenue").value(300_000))
        .andExpect(jsonPath("$.summary.recommendedRevenue").value(2_000))
        .andExpect(jsonPath("$.summary.recommendedShare").value(0.67))
        .andExpect(jsonPath("$.days.length()").value(5))
        .andExpect(jsonPath("$.days[0].date").value("2031-03-01"))
        .andExpect(jsonPath("$.days[0].orders").value(1))
        .andExpect(jsonPath("$.days[0].revenue").value(0))
        .andExpect(jsonPath("$.days[1].orders").value(1))
        .andExpect(jsonPath("$.days[2].date").value("2031-03-03"))
        .andExpect(jsonPath("$.days[2].revenue").value(330_000))
        .andExpect(jsonPath("$.days[2].deliveredOrders").value(2))
        .andExpect(jsonPath("$.days[4].orders").value(0))
        .andExpect(jsonPath("$.ordersByStatus.length()").value(5))
        .andExpect(jsonPath("$.ordersByStatus[0].status").value("PENDING"))
        .andExpect(jsonPath("$.ordersByStatus[0].count").value(1))
        .andExpect(jsonPath("$.ordersByStatus[3].status").value("DELIVERED"))
        .andExpect(jsonPath("$.ordersByStatus[3].count").value(2))
        .andExpect(jsonPath("$.ordersByStatus[4].count").value(1))
        .andExpect(jsonPath("$.topProducts.length()").value(2))
        .andExpect(jsonPath("$.topProducts[0].productId").value(sock.id()))
        .andExpect(jsonPath("$.topProducts[0].name").value(sock.name()))
        .andExpect(jsonPath("$.topProducts[0].quantity").value(5))
        .andExpect(jsonPath("$.topProducts[0].revenue").value(5_000))
        .andExpect(jsonPath("$.topProducts[1].productId").value(shoe.id()))
        .andExpect(jsonPath("$.recommendations.total.shown").value(2))
        .andExpect(jsonPath("$.recommendations.total.clicked").value(1))
        .andExpect(jsonPath("$.recommendations.total.ctr").value(50.0))
        .andExpect(jsonPath("$.recommendations.byPlacement[0].placement").value("HOME"))
        .andExpect(jsonPath("$.recommendations.byPlacement[0].ctr").value(100.0))
        .andExpect(jsonPath("$.recommendations.byPlacement[1].placement").value("PRODUCT_DETAIL"))
        .andExpect(jsonPath("$.recommendations.byPlacement[1].clicked").value(0))
        .andExpect(jsonPath("$.recommendations.bySource[0].source").value("BEHAVIOR"))
        .andExpect(jsonPath("$.recommendations.bySource[1].source").value("RELATED"))
        .andExpect(jsonPath("$.abTest").doesNotExist());
  }

  @Test
  void emptyRangeHasZerosAndRunningAbTestIsSummarised() throws Exception {
    jdbcTemplate.update(
        "INSERT INTO ab_experiment (name, treatment_percent, started_at) VALUES (?, 50, ?)",
        "Thử nghiệm tổng quan",
        LocalDateTime.now());

    admin(get(DASHBOARD).param("from", "2031-07-01").param("to", "2031-07-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.revenue").value(0))
        .andExpect(jsonPath("$.summary.averageOrderValue").doesNotExist())
        .andExpect(jsonPath("$.summary.recommendedShare").doesNotExist())
        .andExpect(jsonPath("$.days.length()").value(1))
        .andExpect(jsonPath("$.topProducts.length()").value(0))
        .andExpect(jsonPath("$.recommendations.total.ctr").doesNotExist())
        .andExpect(jsonPath("$.recommendations.byPlacement.length()").value(0))
        .andExpect(jsonPath("$.abTest.name").value("Thử nghiệm tổng quan"))
        .andExpect(jsonPath("$.abTest.treatmentUsers").value(0));
  }

  @Test
  void defaultsToLastThirtyDaysAndRejectsBadRangesAndCustomers() throws Exception {
    admin(get(DASHBOARD))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days.length()").value(30));
    admin(get(DASHBOARD).param("from", "2031-03-05").param("to", "2031-03-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"));
    admin(get(DASHBOARD).param("from", "2030-01-01").param("to", "2031-01-02"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Chỉ xem được tối đa 366 ngày mỗi lần"));
    admin(get(DASHBOARD).param("from", "2030-01-01").param("to", "2031-01-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days.length()").value(366));
    mockMvc
        .perform(
            get(DASHBOARD)
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(DASHBOARD)).andExpect(status().isUnauthorized());
  }

  /** Giờ Việt Nam đổi sang giờ JVM (giờ backend lưu vào CSDL). */
  private static LocalDateTime vn(int year, int month, int day, int hour) {
    return LocalDateTime.of(year, month, day, hour, 0)
        .atZone(VIETNAM)
        .withZoneSameInstant(ZoneId.systemDefault())
        .toLocalDateTime();
  }

  private long account(LocalDateTime createdAt) {
    long id =
        accountRepository
            .save(
                new Account(
                    "tong-quan-" + UUID.randomUUID() + "@test.local",
                    "khong-dung",
                    "Khách tổng quan",
                    Role.CUSTOMER))
            .getId();
    accountRepository.flush();
    jdbcTemplate.update("UPDATE account SET created_at = ? WHERE id = ?", createdAt, id);
    return id;
  }

  private ProductResponse product(String name) {
    return productService.create(
        new ProductCreateRequest(
            name + " " + UUID.randomUUID(),
            null,
            null,
            null,
            category,
            null,
            List.of(),
            List.of(new VariantRequest(null, null, 300_000L, 10, null))));
  }

  private long order(long accountId, String status, long total, LocalDateTime at) {
    long id = insertOrder(jdbcTemplate, accountId, status);
    jdbcTemplate.update(
        "UPDATE orders SET total_amount = ?, created_at = ? WHERE id = ?", total, at, id);
    return id;
  }

  private void event(
      long accountId,
      long productId,
      String type,
      String placement,
      String source,
      LocalDateTime at) {
    jdbcTemplate.update(
        "INSERT INTO product_event_log"
            + " (account_id, product_id, event_type, placement, source, rank_position, created_at)"
            + " VALUES (?, ?, ?, ?, ?, 1, ?)",
        accountId,
        productId,
        type,
        placement,
        source,
        at);
  }

  private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }
}
