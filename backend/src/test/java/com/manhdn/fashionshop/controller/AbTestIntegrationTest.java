package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.ProductResponse;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.AbVariant;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.AbTestService;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * Kiểm tra thử nghiệm A/B gợi ý ở trang chủ (US-27, BR-46) với MySQL thật: chia nhóm, nhóm CONTROL
 * thấy sản phẩm bán chạy, số liệu từng nhóm và vòng đời thử nghiệm. Mỗi test rollback ở cuối.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AbTestIntegrationTest {

  private static final String TESTS = "/api/admin/ab-tests";
  private static final String HOME = "/api/recommendations/me";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  private long category;

  @BeforeEach
  void setUp() {
    // CSDL test dùng chung giữa các lần chạy: dừng thử nghiệm còn sót (nếu có) trong giao dịch này.
    jdbcTemplate.update("UPDATE ab_experiment SET ended_at = NOW() WHERE ended_at IS NULL");
    category = categoryService.create("Danh mục A/B " + UUID.randomUUID()).id();
  }

  @Test
  void controlSeesBestSellersTreatmentSeesItsListAndGroupsStayFixed() throws Exception {
    long experiment = start(50);
    long treatment = accountIn(experiment, 50, AbVariant.TREATMENT);
    long control = accountIn(experiment, 50, AbVariant.CONTROL);
    long newcomer = account();
    long forTreatment = product("Sản phẩm gợi ý T", 2).id();
    long forControl = product("Sản phẩm gợi ý C", 2).id();
    store(treatment, forTreatment);
    store(control, forControl);
    reload();

    customer(get(HOME), treatment)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abVariant").value("TREATMENT"))
        .andExpect(jsonPath("$.source").value("SURVEY"))
        .andExpect(jsonPath("$.products[0].product.id").value(forTreatment));
    customer(get(HOME), control)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.abVariant").value("CONTROL"))
        .andExpect(jsonPath("$.source").value("POPULAR"))
        .andExpect(jsonPath("$.recent").doesNotExist())
        .andExpect(jsonPath("$.updating").value(false))
        .andExpect(jsonPath("$.products[0].source").value("POPULAR"));
    // Khách chưa có danh sách riêng không được chia nhóm.
    customer(get(HOME), newcomer)
        .andExpect(jsonPath("$.abVariant").doesNotExist())
        .andExpect(jsonPath("$.source").value("POPULAR"));
    // Gọi lại vẫn giữ nhóm cũ, không thêm dòng.
    customer(get(HOME), control).andExpect(jsonPath("$.abVariant").value("CONTROL"));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT variant FROM ab_assignment WHERE experiment_id = ? ORDER BY variant",
                String.class,
                experiment))
        .containsExactly("TREATMENT", "CONTROL");

    admin(post(TESTS + "/" + experiment + "/stop")).andExpect(status().isOk());
    reload();
    customer(get(HOME), control)
        .andExpect(jsonPath("$.abVariant").doesNotExist())
        .andExpect(jsonPath("$.products[0].product.id").value(forControl));
  }

  @Test
  void resultCountsHomeActionsAfterAssignmentPerGroup() throws Exception {
    long experiment = start(50);
    long treatment = accountIn(experiment, 50, AbVariant.TREATMENT);
    long control = accountIn(experiment, 50, AbVariant.CONTROL);
    ProductResponse clicked = product("Sản phẩm được bấm", 5);
    long other = product("Sản phẩm khác", 5).id();
    store(treatment, clicked.id());
    store(control, other);
    reload();
    customer(get(HOME), treatment).andExpect(status().isOk());
    customer(get(HOME), control).andExpect(status().isOk());

    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    // Trước lúc chia nhóm và ở vị trí khác trang chủ: không tính.
    event(treatment, other, "RECOMMENDATION_CLICKED", "HOME", now.minusHours(1));
    event(treatment, other, "RECOMMENDATION_CLICKED", "PRODUCT_DETAIL", now);
    event(treatment, clicked.id(), "RECOMMENDATION_SHOWN", "HOME", now);
    event(treatment, other, "RECOMMENDATION_SHOWN", "HOME", now);
    event(treatment, other, "RECOMMENDATION_SHOWN", "HOME_RECENT", now);
    event(treatment, clicked.id(), "RECOMMENDATION_CLICKED", "HOME", now);
    event(control, other, "RECOMMENDATION_SHOWN", "HOME", now);
    long bought = order(treatment, "DELIVERED", 420_000L, now);
    insertOrderItem(jdbcTemplate, bought, clicked.variants().get(0).id(), 1);
    order(treatment, "CANCELLED", 999_000L, now);
    long controlOrder = order(control, "PENDING", 200_000L, now);
    insertOrderItem(jdbcTemplate, controlOrder, clicked.variants().get(0).id(), 1);

    admin(get(TESTS + "/" + experiment))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.experiment.running").value(true))
        .andExpect(jsonPath("$.treatment.users").value(1))
        .andExpect(jsonPath("$.treatment.shown").value(3))
        .andExpect(jsonPath("$.treatment.clicks").value(1))
        .andExpect(jsonPath("$.treatment.ctr").value(33.33))
        .andExpect(jsonPath("$.treatment.usersClicked").value(1))
        .andExpect(jsonPath("$.treatment.clickRate").value(100.0))
        .andExpect(jsonPath("$.treatment.usersOrdered").value(1))
        .andExpect(jsonPath("$.treatment.orders").value(1))
        .andExpect(jsonPath("$.treatment.revenue").value(420_000))
        .andExpect(jsonPath("$.treatment.revenuePerUser").value(420_000))
        .andExpect(jsonPath("$.treatment.usersBoughtClicked").value(1))
        .andExpect(jsonPath("$.control.users").value(1))
        .andExpect(jsonPath("$.control.shown").value(1))
        .andExpect(jsonPath("$.control.clicks").value(0))
        .andExpect(jsonPath("$.control.ctr").value(0.0))
        .andExpect(jsonPath("$.control.usersOrdered").value(1))
        .andExpect(jsonPath("$.control.usersBoughtClicked").value(0))
        .andExpect(jsonPath("$.clickRate.difference").value(100.0))
        .andExpect(jsonPath("$.clickRate.verdict").value("NOT_ENOUGH_DATA"))
        .andExpect(jsonPath("$.orderRate.difference").value(0.0))
        .andExpect(jsonPath("$.minUsersPerGroup").value(30));
  }

  @Test
  void experimentNeedsValidInputRunsOneAtATimeAndStopsOnce() throws Exception {
    admin(post(TESTS).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.name").value("Chưa nhập tên thử nghiệm"))
        .andExpect(jsonPath("$.errors.treatmentPercent").value("Chưa nhập tỷ lệ chia nhóm"));
    admin(
            post(TESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lệch\",\"treatmentPercent\":5}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.treatmentPercent").value("Tỷ lệ nhóm gợi ý từ 10% đến 90%"));
    long id = start(70);
    admin(
            post(TESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lần hai\",\"treatmentPercent\":50}"))
        .andExpect(status().isConflict());
    admin(get(TESTS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].name").value("Gợi ý so với bán chạy"))
        .andExpect(jsonPath("$[0].treatmentPercent").value(70))
        .andExpect(jsonPath("$[0].running").value(true));
    admin(post(TESTS + "/" + id + "/stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.running").value(false))
        .andExpect(jsonPath("$.endedAt").isNotEmpty());
    admin(post(TESTS + "/" + id + "/stop")).andExpect(status().isBadRequest());
    admin(get(TESTS + "/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.treatment.users").value(0))
        .andExpect(jsonPath("$.treatment.ctr").doesNotExist())
        .andExpect(jsonPath("$.clickRate.verdict").value("NOT_ENOUGH_DATA"));
    admin(get(TESTS + "/0")).andExpect(status().isNotFound());
    // Bắt đầu được thử nghiệm mới sau khi dừng; khách không vào được API quản trị.
    start(50);
    customer(get(TESTS), account()).andExpect(status().isForbidden());
  }

  private long start(int treatmentPercent) throws Exception {
    String body =
        admin(
                post(TESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"Gợi ý so với bán chạy\",\"treatmentPercent\":"
                            + treatmentPercent
                            + "}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  /** Tạo tài khoản cho đến khi băm rơi vào nhóm mong muốn. */
  private long accountIn(long experiment, int percent, AbVariant variant) {
    while (true) {
      long id = account();
      if (AbTestService.variantOf(experiment, id, percent) == variant) {
        return id;
      }
    }
  }

  private long account() {
    return accountRepository
        .save(
            new Account(
                "ab-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách A/B",
                Role.CUSTOMER))
        .getId();
  }

  private ProductResponse product(String name, int stock) {
    return productService.create(
        new ProductCreateRequest(
            name + " " + UUID.randomUUID(),
            null,
            null,
            null,
            category,
            null,
            List.of(),
            List.of(new VariantRequest(null, null, 300_000L, stock, null))));
  }

  private void store(long accountId, long productId) {
    jdbcTemplate.update(
        "INSERT INTO recommendation"
            + " (account_id, product_id, rank_position, score, reason, generated_at)"
            + " VALUES (?, ?, 1, 1, 'Lý do thử', ?)",
        accountId,
        productId,
        LocalDateTime.now());
  }

  private void event(
      long accountId, long productId, String type, String placement, LocalDateTime at) {
    jdbcTemplate.update(
        "INSERT INTO product_event_log"
            + " (account_id, product_id, event_type, placement, source, rank_position, created_at)"
            + " VALUES (?, ?, ?, ?, 'BEHAVIOR', 1, ?)",
        accountId,
        productId,
        type,
        placement,
        at);
  }

  private long order(long accountId, String status, long total, LocalDateTime at) {
    long id = insertOrder(jdbcTemplate, accountId, status);
    jdbcTemplate.update(
        "UPDATE orders SET total_amount = ?, created_at = ? WHERE id = ?", total, at, id);
    return id;
  }

  private void reload() {
    entityManager.flush();
    entityManager.clear();
  }

  private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private ResultActions customer(MockHttpServletRequestBuilder request, long accountId)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }
}
