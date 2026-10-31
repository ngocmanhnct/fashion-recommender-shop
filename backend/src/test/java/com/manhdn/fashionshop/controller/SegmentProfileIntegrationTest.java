package com.manhdn.fashionshop.controller;

import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrder;
import static com.manhdn.fashionshop.controller.OrderFixtures.insertOrderItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.service.CampaignService;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import com.manhdn.fashionshop.service.VoucherService;
import jakarta.persistence.EntityManager;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra API hồ sơ nhóm khách hàng (US-25), danh sách khách cần chăm sóc (BR-43) và chiến dịch
 * tặng mã riêng theo nhóm (US-26, BR-44) với MySQL thật; dữ liệu phân nhóm ghi thẳng bằng SQL như
 * ml-service. Mỗi test rollback ở cuối.
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
  @Autowired private VoucherService voucherService;
  @Autowired private CampaignService campaignService;
  @Autowired private EntityManager entityManager;

  private String marker;
  private long loyal;
  private long regular;
  private long silent;
  private long other;
  private long loyalSegment;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM account_segment");
    jdbcTemplate.update("DELETE FROM segment_entry");
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
    silent = customer("Đỗ Im " + marker, "im", null, null);
    // Khách nhóm khác, để tính tỷ lệ doanh thu và tỷ lệ khách.
    other = customer("Khách nhóm khác " + marker, "khac", null, null);
    delivered(other, 500_000, now.minusDays(5));

    long model =
        insert(
            "INSERT INTO segment_model (cluster_count, elbow_cluster_count, customer_count,"
                + " silhouette, log_recency_mean, log_recency_std, log_frequency_mean,"
                + " log_frequency_std, log_monetary_mean, log_monetary_std, trained_at)"
                + " VALUES (2, 2, 40, 0.42, 3, 1, 1, 0.5, 13, 1, '2026-10-28 02:00:00')");
    loyalSegment = segment(model, "LOYAL", "Khách trung thành");
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

  @Test
  void profileSuggestsWhatToDoFromTheGroupsOwnHabits() throws Exception {
    // Đơn trung bình 160.000đ: mã cho đơn từ 1,3 lần mức đó, làm tròn lên 250.000đ.
    admin(PROFILE + "LOYAL")
        .andExpect(jsonPath("$.suggestion.goal").value("Tăng giá trị mỗi đơn"))
        .andExpect(jsonPath("$.suggestion.target").value("ALL"))
        .andExpect(jsonPath("$.suggestion.discountType").value("FIXED"))
        .andExpect(jsonPath("$.suggestion.discountValue").value(50_000))
        .andExpect(jsonPath("$.suggestion.minOrderAmount").value(250_000))
        .andExpect(jsonPath("$.suggestion.validDays").value(14));
    admin(PROFILE + "NEW")
        .andExpect(jsonPath("$.suggestion.goal").value("Có đơn thứ hai"))
        .andExpect(jsonPath("$.suggestion.discountType").value("PERCENT"))
        .andExpect(jsonPath("$.suggestion.maxDiscountAmount").value(50_000))
        .andExpect(jsonPath("$.suggestion.minOrderAmount").doesNotExist());
  }

  @Test
  void giftingCareCustomersCreatesCodeOnlyTheyCanUse() throws Exception {
    String code = "CHAMSOC-" + marker;
    adminDo(campaign("LOYAL", "CARE", code.toLowerCase()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.segmentCode").value("LOYAL"))
        .andExpect(jsonPath("$.segmentName").value("Khách trung thành"))
        .andExpect(jsonPath("$.target").value("CARE"))
        .andExpect(jsonPath("$.recipientCount").value(1))
        .andExpect(jsonPath("$.voucher.code").value(code.toUpperCase()))
        .andExpect(jsonPath("$.voucher.recipientsOnly").value(true))
        .andExpect(jsonPath("$.buyers").value(0))
        .andExpect(jsonPath("$.revenuePerDiscount").doesNotExist());

    // Chỉ khách cần chăm sóc (Lan) được tặng; khách cùng nhóm khác thì không dùng được (BR-44).
    assertThat(voucherService.check(loyal, code, 300_000).discount()).isEqualTo(50_000);
    assertThat(voucherService.check(regular, code, 300_000).error())
        .isEqualTo("Mã này chỉ dành cho khách được tặng");
    customer(loyal, get("/api/vouchers/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].code").value(code.toUpperCase()))
        .andExpect(jsonPath("$[0].minOrderAmount").value(250_000));
    customer(regular, get("/api/vouchers/me")).andExpect(jsonPath("$.length()").value(0));

    // Đã dùng hết lượt của mình thì không còn trong "Ưu đãi của bạn".
    long order = delivered(loyal, 250_000, LocalDateTime.now());
    jdbcTemplate.update(
        "UPDATE orders SET voucher_id = (SELECT id FROM voucher WHERE code = ?) WHERE id = ?",
        code.toUpperCase(),
        order);
    customer(loyal, get("/api/vouchers/me")).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void campaignResultsCountOrdersPlacedWithTheCode() throws Exception {
    String code = ("CAMON-" + marker).toUpperCase();
    adminDo(campaign("LOYAL", "ALL", code)).andExpect(jsonPath("$.recipientCount").value(3));
    useCode(loyal, code, "PENDING", 300_000, 30_000);
    useCode(regular, code, "CANCELLED", 200_000, 20_000);

    admin("/api/admin/campaigns?segment=LOYAL")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].target").value("ALL"))
        .andExpect(jsonPath("$[0].buyers").value(1))
        .andExpect(jsonPath("$[0].conversionRate").value(33.3))
        .andExpect(jsonPath("$[0].orders").value(1))
        .andExpect(jsonPath("$[0].revenue").value(300_000))
        .andExpect(jsonPath("$[0].discount").value(30_000))
        .andExpect(jsonPath("$[0].revenuePerDiscount").value(10.0));
    admin("/api/admin/campaigns?segment=NEW").andExpect(jsonPath("$.length()").value(0));
    admin("/api/admin/campaigns").andExpect(jsonPath("$[0].voucher.code").value(code));
  }

  @Test
  void campaignNeedsRecipientsValidCodeAndAdmin() throws Exception {
    // Nhóm NEW không có khách mua từ 2 đơn nên không có khách cần chăm sóc.
    adminDo(campaign("NEW", "CARE", "KHONGAI-" + marker))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Nhóm hiện không có khách cần chăm sóc"));
    adminDo(campaign("KHONG_CO", "ALL", "MAT-" + marker)).andExpect(status().isNotFound());
    adminDo(campaign("NEW", "ALL", "x")).andExpect(status().isBadRequest());
    adminDo(campaign("NEW", "ALL", "DUP-" + marker)).andExpect(status().isCreated());
    adminDo(campaign("NEW", "ALL", "DUP-" + marker)).andExpect(status().isConflict());
    customer(loyal, campaign("NEW", "ALL", "KHACH-" + marker)).andExpect(status().isForbidden());
    customer(loyal, get("/api/admin/campaigns")).andExpect(status().isForbidden());
    admin("/api/admin/campaigns?segment=" + "X".repeat(21)).andExpect(status().isBadRequest());
  }

  @Test
  void autoOfferGoesOnlyToCustomersWhoEnterTheGroupAfterItStarts() throws Exception {
    String code = ("CHAOMUNG-" + marker).toUpperCase();
    long id =
        idOf(
            adminDo(autoCampaign("LOYAL", code, "7"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.target").value("AUTO"))
                .andExpect(jsonPath("$.autoEnabled").value(true))
                .andExpect(jsonPath("$.validDays").value(7))
                .andExpect(jsonPath("$.recipientCount").value(0)));
    startedAMinuteAgo(id);

    // Khách đã ở trong nhóm lúc bật không phải khách mới vào.
    assertThat(campaignService.grantAutoOffers()).isZero();
    jdbcTemplate.update(
        "UPDATE account_segment SET segment_id = ? WHERE account_id = ?", loyalSegment, other);
    assertThat(campaignService.grantAutoOffers()).isEqualTo(1);
    assertThat(campaignService.grantAutoOffers()).isZero();
    // Các request thật chạy ở giao dịch riêng: bỏ bản entity cũ để đọc lại số khách đã nhận.
    entityManager.clear();

    assertThat(voucherService.check(other, code, 300_000).discount()).isEqualTo(50_000);
    assertThat(voucherService.check(loyal, code, 300_000).error())
        .isEqualTo("Mã này chỉ dành cho khách được tặng");
    // Hạn riêng 7 ngày kể từ lúc nhận, sớm hơn hạn 14 ngày của mã.
    String personalDay = LocalDate.now(VoucherService.ZONE).plusDays(7).toString();
    customer(other, get("/api/vouchers/me"))
        .andExpect(jsonPath("$[0].code").value(code))
        .andExpect(jsonPath("$[0].validUntil").value(startsWith(personalDay)));
    admin("/api/admin/campaigns?segment=LOYAL").andExpect(jsonPath("$[0].recipientCount").value(1));

    // Quá hạn riêng thì không dùng được nữa, dù mã còn hạn.
    jdbcTemplate.update(
        "UPDATE voucher_recipient SET expires_at = ? WHERE account_id = ?",
        LocalDateTime.now(VoucherService.ZONE).minusDays(1),
        other);
    assertThat(voucherService.check(other, code, 300_000).error())
        .isEqualTo("Mã giảm giá đã hết hạn");
    customer(other, get("/api/vouchers/me")).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void autoOfferNeedsDaysRunsOneAtATimeAndStops() throws Exception {
    adminDo(autoCampaign("LOYAL", "THIEU-" + marker, null))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Chưa nhập số ngày khách được dùng mã"));
    adminDo(autoCampaign("LOYAL", "KHONG-" + marker, "0")).andExpect(status().isBadRequest());
    long id =
        idOf(
            adminDo(autoCampaign("LOYAL", "TUDONG-" + marker, "7"))
                .andExpect(status().isCreated()));
    adminDo(autoCampaign("LOYAL", "TUDONG2-" + marker, "7")).andExpect(status().isConflict());

    adminDo(post("/api/admin/campaigns/" + id + "/stop"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.autoEnabled").value(false));
    // Request dừng chạy ở giao dịch riêng: ghi xuống CSDL trước khi câu tặng mã (JDBC) đọc.
    entityManager.flush();
    startedAMinuteAgo(id);
    jdbcTemplate.update(
        "UPDATE account_segment SET segment_id = ? WHERE account_id = ?", loyalSegment, other);
    assertThat(campaignService.grantAutoOffers()).isZero();

    // Dừng rồi thì bật được chương trình mới; chỉ dừng được chương trình tự tặng.
    adminDo(autoCampaign("LOYAL", "TUDONG3-" + marker, "7")).andExpect(status().isCreated());
    long manual =
        idOf(adminDo(campaign("LOYAL", "ALL", "TAY-" + marker)).andExpect(status().isCreated()));
    adminDo(post("/api/admin/campaigns/" + manual + "/stop")).andExpect(status().isBadRequest());
    adminDo(post("/api/admin/campaigns/0/stop")).andExpect(status().isNotFound());
    customer(loyal, post("/api/admin/campaigns/" + id + "/stop")).andExpect(status().isForbidden());
  }

  // ---------- Tiện ích ----------

  /** Lùi lúc bật chương trình và lúc vào nhóm của khách hiện có, như thể đã bật 1 phút trước. */
  private void startedAMinuteAgo(long campaignId) {
    jdbcTemplate.update("UPDATE segment_entry SET entered_at = entered_at - INTERVAL 2 MINUTE");
    jdbcTemplate.update(
        "UPDATE segment_campaign SET created_at = created_at - INTERVAL 1 MINUTE WHERE id = ?",
        campaignId);
  }

  private long idOf(ResultActions result) throws Exception {
    String body = result.andReturn().getResponse().getContentAsString();
    return ((Number) JsonPath.read(body, "$.id")).longValue();
  }

  private static MockHttpServletRequestBuilder autoCampaign(
      String segment, String code, String validDays) {
    LocalDateTime now = LocalDateTime.now(VoucherService.ZONE).truncatedTo(ChronoUnit.MINUTES);
    return post(PROFILE + segment + "/campaigns")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            ("{\"target\": \"AUTO\", \"validDays\": %s, \"voucher\": {\"code\": \"%s\","
                    + " \"discountType\": \"FIXED\", \"discountValue\": 50000,"
                    + " \"validFrom\": \"%s\", \"validUntil\": \"%s\","
                    + " \"usageLimitPerAccount\": 1}}")
                .formatted(validDays, code, now.minusDays(1), now.plusDays(14)));
  }

  private static MockHttpServletRequestBuilder campaign(
      String segment, String target, String code) {
    LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    return post(PROFILE + segment + "/campaigns")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            ("{\"target\": \"%s\", \"voucher\": {\"code\": \"%s\", \"discountType\": \"FIXED\","
                    + " \"discountValue\": 50000, \"minOrderAmount\": 250000,"
                    + " \"validFrom\": \"%s\", \"validUntil\": \"%s\","
                    + " \"usageLimitPerAccount\": 1}}")
                .formatted(target, code, now.minusDays(1), now.plusDays(14)));
  }

  private void useCode(long accountId, String code, String status, long total, long discount) {
    long order = insertOrder(jdbcTemplate, accountId, status);
    jdbcTemplate.update(
        "UPDATE orders SET voucher_id = (SELECT id FROM voucher WHERE code = ?),"
            + " total_amount = ?, discount_amount = ? WHERE id = ?",
        code,
        total,
        discount,
        order);
  }

  private ResultActions adminDo(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  private ResultActions customer(long accountId, MockHttpServletRequestBuilder request)
      throws Exception {
    return mockMvc.perform(
        request.with(
            jwt()
                .jwt(j -> j.subject(String.valueOf(accountId)))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
  }

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
