package com.manhdn.fashionshop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.manhdn.fashionshop.dto.ProductCreateRequest;
import com.manhdn.fashionshop.dto.VariantRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.service.BrandService;
import com.manhdn.fashionshop.service.CategoryService;
import com.manhdn.fashionshop.service.ProductService;
import java.util.ArrayList;
import java.util.List;
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
 * Kiểm tra API khảo sát lần đầu dạng rẽ nhánh (US-23, BR-37, BR-38, BR-41, BR-42) với MySQL thật và
 * cây câu hỏi nạp bằng migration V5.
 *
 * <p>Không dùng {@code @Transactional}: yêu cầu tính lại gợi ý chỉ được ghi ngay trước khi giao
 * dịch commit. Dữ liệu test tạo ra được xóa ở {@link #cleanUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SurveyIntegrationTest {

  private static final String SCHOOL_BAG = "Balo laptop";
  private static final String MEN_SHOES = "Giày thể thao nam";
  private static final String WOMEN_SHOES = "Giày thể thao nữ";

  @Autowired private MockMvc mockMvc;
  @Autowired private CategoryService categoryService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private BrandService brandService;
  @Autowired private ProductService productService;
  @Autowired private AccountRepository accountRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedJdbcTemplate;

  private final List<Long> accountIds = new ArrayList<>();
  private final List<Long> productIds = new ArrayList<>();
  private final List<Long> categoryIds = new ArrayList<>();
  private final List<Long> brandIds = new ArrayList<>();
  private long bags;
  private long menShoes;
  private long womenShoes;

  @BeforeEach
  void createCategories() {
    bags = category(SCHOOL_BAG);
    menShoes = category(MEN_SHOES);
    womenShoes = category(WOMEN_SHOES);
  }

  @AfterEach
  void cleanUp() {
    deleteWhere("DELETE FROM recompute_request WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM user_preference WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM survey_result WHERE account_id IN (:ids)", accountIds);
    deleteWhere("DELETE FROM product_variant WHERE product_id IN (:ids)", productIds);
    deleteWhere("DELETE FROM product WHERE id IN (:ids)", productIds);
    deleteWhere("DELETE FROM brand WHERE id IN (:ids)", brandIds);
    deleteWhere("DELETE FROM category WHERE id IN (:ids)", categoryIds);
    deleteWhere("DELETE FROM account WHERE id IN (:ids)", accountIds);
  }

  @Test
  void surveyTreeIsPublicAndBranches() throws Exception {
    mockMvc
        .perform(get("/api/survey"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startQuestionCode").value("Q1"))
        .andExpect(jsonPath("$.questions.length()").value(18))
        .andExpect(jsonPath("$.questions[0].code").value("Q1"))
        .andExpect(jsonPath("$.questions[0].options[0].code").value("SELF_MALE"))
        .andExpect(jsonPath("$.questions[0].options[0].nextQuestionCode").value("N1"))
        .andExpect(jsonPath("$.questions[0].options[1].nextQuestionCode").value("F1"))
        .andExpect(jsonPath("$.questions[0].options[2].code").value("GIFT"))
        .andExpect(jsonPath("$.questions[0].options[2].nextQuestionCode").value("G1"))
        .andExpect(jsonPath("$.questions[1].answerType").value("MULTIPLE"))
        .andExpect(jsonPath("$.questions[1].maxChoices").value(3))
        .andExpect(jsonPath("$.questions[5].code").value("B1"))
        .andExpect(jsonPath("$.questions[5].answerType").value("BRAND"))
        .andExpect(jsonPath("$.questions[5].nextQuestionCode").value("B2"))
        .andExpect(jsonPath("$.questions[5].options.length()").value(0))
        .andExpect(jsonPath("$.questions[4].options[2].nextQuestionCode").doesNotExist())
        .andExpect(jsonPath("$.questions[12].code").value("N1"))
        .andExpect(jsonPath("$.questions[12].answerType").value("MULTIPLE"))
        .andExpect(jsonPath("$.questions[12].options[0].nextQuestionCode").value("AM"))
        .andExpect(jsonPath("$.questions[14].options[0].nextQuestionCode").value("Q2"))
        .andExpect(jsonPath("$.questions[1].options[5].code").value("PARTY"))
        .andExpect(jsonPath("$.questions[1].options[5].nextQuestionCode").value("Q3"));
  }

  @Test
  void womenNeverGetMenProductsEvenWhenFillingWithBestSellers() throws Exception {
    long menBag = product(bags, null, 300_000L, "Balo nam công sở");
    long womenBag = product(bags, null, 320_000L, "Balo nữ năng động");
    long unisexBag = product(bags, null, 340_000L, "Balo unisex đi học");
    long menShoe = product(menShoes, null, 100_000L, "Giày chạy bộ");
    String answers =
        path(
            "Q1",
            "SELF_FEMALE",
            "F1",
            "BACKPACK",
            "AF",
            "SPORTY",
            "Q2",
            "SCHOOL",
            "Q3",
            "TREND",
            "P1",
            "OVER_1M");

    mockMvc
        .perform(evaluate(answers))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.products[?(@.product.id == " + womenBag + ")]").exists())
        .andExpect(jsonPath("$.products[?(@.product.id == " + unisexBag + ")]").exists())
        .andExpect(jsonPath("$.products[?(@.product.id == " + menBag + ")]").isEmpty())
        .andExpect(jsonPath("$.products[?(@.product.id == " + menShoe + ")]").isEmpty());

    long account = account();
    perform(account, put("/api/survey/me"), "{\"answers\":" + answers + "}")
        .andExpect(status().isOk());
    assertThat(
            count(
                "SELECT COUNT(*) FROM survey_result WHERE account_id = ? AND gender = 'FEMALE'"
                    + " AND keywords LIKE '%năng động%' AND keywords LIKE '%đi học%'",
                account))
        .isEqualTo(1);
  }

  @Test
  void occasionNarrowsEachChosenItemTypeButNeverDropsOne() throws Exception {
    long handbags = category("Túi xách nữ");
    long totes = category("Túi tote nữ");
    String answers =
        path(
            "Q1",
            "SELF_FEMALE",
            "F1",
            "SNEAKER",
            "F1+",
            "BAG",
            "AF",
            "ELEGANT",
            "Q2",
            "WORK",
            "Q3",
            "TREND",
            "P1",
            "OVER_1M");

    mockMvc
        .perform(evaluate(answers))
        .andExpect(status().isOk())
        // Giày thể thao không hợp dịp đi làm nhưng khách đã chọn nên vẫn giữ.
        .andExpect(jsonPath("$.profile.categories[?(@.id == " + womenShoes + ")]").exists())
        .andExpect(jsonPath("$.profile.categories[?(@.id == " + handbags + ")]").exists())
        .andExpect(jsonPath("$.profile.categories[?(@.id == " + totes + ")]").isEmpty())
        .andExpect(jsonPath("$.profile.categories[?(@.id == " + menShoes + ")]").isEmpty());
  }

  @Test
  void saverPathKeepsMenCategoriesStretchesBudgetAndSortsByPrice() throws Exception {
    long cheapShoes = product(menShoes, null, 250_000L);
    long bag = product(bags, null, 300_000L);
    long expensiveBag = product(bags, null, 900_000L);
    product(womenShoes, null, 260_000L);

    mockMvc
        .perform(evaluate(saverAnswers()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.purpose").value("SELF"))
        .andExpect(jsonPath("$.profile.style").value("SAVER"))
        .andExpect(jsonPath("$.profile.styleName").value("Người mua tiết kiệm"))
        .andExpect(
            jsonPath("$.profile.summary")
                .value(
                    "Bạn là Người mua tiết kiệm: ưu tiên món đáng tiền,"
                        + " giá từ 200.000đ đến 650.000đ."))
        .andExpect(jsonPath("$.profile.priceMin").value(200_000))
        .andExpect(jsonPath("$.profile.priceMax").value(650_000))
        .andExpect(jsonPath("$.profile.categories[?(@.name == '" + WOMEN_SHOES + "')]").isEmpty())
        .andExpect(jsonPath("$.profile.categories[?(@.name == '" + MEN_SHOES + "')]").exists())
        .andExpect(jsonPath("$.products[0].product.id").value(cheapShoes))
        .andExpect(jsonPath("$.products[0].reason").value("Giá tốt trong tầm giá bạn chọn"))
        .andExpect(jsonPath("$.products[1].product.id").value(bag))
        .andExpect(jsonPath("$.products[2].product.id").value(expensiveBag))
        .andExpect(jsonPath("$.products[2].reason").value("Bán chạy trong danh mục bạn quan tâm"));
  }

  @Test
  void brandPathOffersBrandsOfChosenCategoriesAndPutsChosenBrandFirst() throws Exception {
    long brand = brand();
    product(bags, null, 300_000L);
    long branded = product(bags, brand, 400_000L);
    String beforeBrand =
        path("Q1", "SELF_FEMALE", "F1", "ANY", "AF", "ANY", "Q2", "SCHOOL", "Q3", "BRAND");

    mockMvc
        .perform(publicPost("/api/survey/brands", "{\"answers\":" + beforeBrand + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == " + brand + ")]").exists());
    mockMvc
        .perform(
            publicPost("/api/survey/brands", "{\"answers\":[" + answer("Q1", "SELF_FEMALE") + "]}"))
        .andExpect(status().isBadRequest());

    String answers =
        beforeBrand.substring(0, beforeBrand.length() - 1)
            + ",{\"questionCode\":\"B1\",\"brandId\":"
            + brand
            + "},"
            + answer("B2", "OPEN")
            + ","
            + answer("P1", "200K_500K")
            + "]";
    mockMvc
        .perform(evaluate(answers))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.style").value("BRAND"))
        .andExpect(jsonPath("$.profile.brand.id").value(brand))
        .andExpect(jsonPath("$.profile.brandLoyalty").value("OPEN"))
        .andExpect(jsonPath("$.products[0].product.id").value(branded))
        .andExpect(jsonPath("$.products[0].reason").value("Thương hiệu bạn tin dùng"));

    long otherBrand = brand();
    String foreign = answers.replace("\"brandId\":" + brand, "\"brandId\":" + otherBrand);
    mockMvc
        .perform(evaluate(foreign))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Thương hiệu không thuộc các danh mục bạn quan tâm"));
  }

  @Test
  void giftPathIsEvaluatedButNeverSaved() throws Exception {
    String gift = path("Q1", "GIFT", "G1", "FEMALE", "GF", "ANY", "G2", "SCHOOL", "G3", "OVER_1M");
    mockMvc
        .perform(evaluate(gift))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.purpose").value("GIFT"))
        .andExpect(jsonPath("$.profile.style").value("GIFT"))
        .andExpect(jsonPath("$.profile.priceMin").value(1_000_000))
        .andExpect(jsonPath("$.profile.priceMax").doesNotExist())
        .andExpect(jsonPath("$.profile.categories[?(@.name == '" + MEN_SHOES + "')]").isEmpty());

    long account = account();
    perform(account, put("/api/survey/me"), "{\"answers\":" + gift + "}")
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Kết quả tìm quà tặng chỉ dùng để gợi ý, không lưu vào sở thích của bạn"));
    assertThat(count("SELECT COUNT(*) FROM survey_result WHERE account_id = ?", account)).isZero();
  }

  @Test
  void answersMustFollowTheTree() throws Exception {
    mockMvc
        .perform(evaluate("[" + answer("Q2", "SCHOOL") + "]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Câu trả lời không đúng thứ tự, cần trả lời câu Q1"));
    mockMvc
        .perform(evaluate("[" + answer("Q1", "SELF_MALE") + "]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Bạn chưa trả lời hết khảo sát"));
    mockMvc
        .perform(evaluate("[" + answer("Q1", "NOPE") + "]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Đáp án NOPE không có trong câu Q1"));
    mockMvc
        .perform(evaluate("[{\"questionCode\":\"Q1\",\"optionCodes\":[\"SELF_MALE\",\"GIFT\"]}]"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Câu Q1 cần chọn đúng 1 đáp án"));
    String beforeOccasion = path("Q1", "SELF_MALE", "N1", "ANY", "AM", "ANY");
    String fourOccasions =
        beforeOccasion.substring(0, beforeOccasion.length() - 1)
            + ",{\"questionCode\":\"Q2\",\"optionCodes\":"
            + "[\"SCHOOL\",\"WORK\",\"TRAVEL\",\"SPORT\"]}]";
    mockMvc
        .perform(evaluate(fourOccasions))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Câu Q2 cần chọn từ 1 đến 3 đáp án"));
    String saver = saverAnswers();
    String extra = saver.substring(0, saver.length() - 1) + "," + answer("P1", "OVER_1M") + "]";
    mockMvc
        .perform(evaluate(extra))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Khảo sát đã kết thúc nhưng vẫn còn câu trả lời"));
    mockMvc.perform(publicPost("/api/survey/evaluate", "{}")).andExpect(status().isBadRequest());
  }

  @Test
  void savingStoresPreferencesRequestsRecomputeAndRedoReplacesThem() throws Exception {
    long account = account();
    perform(account, get("/api/survey/me"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NONE"))
        .andExpect(jsonPath("$.profile").doesNotExist());

    perform(account, put("/api/survey/me"), "{\"answers\":" + saverAnswers() + "}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.profile.style").value("SAVER"));

    assertThat(
            count(
                "SELECT COUNT(*) FROM user_preference WHERE account_id = ? AND category_id IN (?, ?)"
                    + " AND price_min = 200000 AND price_max = 650000",
                account,
                bags,
                menShoes))
        .isEqualTo(2);
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_preference WHERE account_id = ? AND category_id = ?",
                account,
                womenShoes))
        .isZero();
    assertThat(
            count(
                "SELECT COUNT(*) FROM survey_result WHERE account_id = ? AND status = 'COMPLETED'"
                    + " AND style = 'SAVER'",
                account))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM recompute_request WHERE account_id = ?"
                    + " AND task_type = 'RECOMMENDATION'"
                    + " AND due_at <= TIMESTAMPADD(SECOND, 3, UTC_TIMESTAMP(3))",
                account))
        .isEqualTo(1);

    perform(account, get("/api/survey/me"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.answers.length()").value(7))
        .andExpect(jsonPath("$.answers[0].questionCode").value("Q1"))
        .andExpect(jsonPath("$.profile.priceMax").value(650_000));

    String trend =
        path(
            "Q1",
            "SELF_FEMALE",
            "F1",
            "ANY",
            "AF",
            "ANY",
            "Q2",
            "SPORT",
            "Q3",
            "TREND",
            "P1",
            "OVER_1M");
    perform(account, put("/api/survey/me"), "{\"answers\":" + trend + "}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.style").value("TREND"));
    assertThat(count("SELECT COUNT(*) FROM user_preference WHERE account_id = ?", account))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT COUNT(*) FROM user_preference WHERE account_id = ? AND category_id = ?"
                    + " AND price_min = 1000000 AND price_max IS NULL",
                account,
                womenShoes))
        .isEqualTo(1);

    perform(account, post("/api/survey/me/skip"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"));
  }

  @Test
  void skippingIsRememberedSoTheSurveyDoesNotShowAgain() throws Exception {
    long account = account();
    perform(account, post("/api/survey/me/skip"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"));
    perform(account, post("/api/survey/me/skip"), null)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SKIPPED"));
    perform(account, get("/api/survey/me"), null)
        .andExpect(jsonPath("$.status").value("SKIPPED"))
        .andExpect(jsonPath("$.answers.length()").value(0));
    assertThat(count("SELECT COUNT(*) FROM survey_result WHERE account_id = ?", account))
        .isEqualTo(1);
  }

  @Test
  void ownSurveyNeedsCustomerToken() throws Exception {
    mockMvc.perform(get("/api/survey/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/survey/me/skip")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            get("/api/survey/me")
                .with(
                    jwt()
                        .jwt(j -> j.subject("1"))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
        .andExpect(status().isForbidden());
  }

  private static String saverAnswers() {
    return path(
        "Q1",
        "SELF_MALE",
        "N1",
        "ANY",
        "AM",
        "BASIC",
        "Q2",
        "SCHOOL",
        "Q3",
        "PRICE",
        "S1",
        "200K_500K",
        "S2",
        "BUY_ANYWAY");
  }

  /**
   * Danh sách câu trả lời từ các cặp (mã câu, mã đáp án); mã câu kết thúc bằng "+" thêm đáp án vào
   * câu ngay trước (câu chọn nhiều).
   */
  private static String path(String... pairs) {
    List<String> answers = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      String option = "\"" + pairs[i + 1] + "\"";
      if (pairs[i].endsWith("+")) {
        String last = answers.remove(answers.size() - 1);
        answers.add(last.substring(0, last.length() - 2) + "," + option + "]}");
      } else {
        answers.add("{\"questionCode\":\"" + pairs[i] + "\",\"optionCodes\":[" + option + "]}");
      }
    }
    return "[" + String.join(",", answers) + "]";
  }

  private static String answer(String question, String option) {
    return "{\"questionCode\":\"" + question + "\",\"optionCodes\":[\"" + option + "\"]}";
  }

  private MockHttpServletRequestBuilder evaluate(String answers) {
    return publicPost("/api/survey/evaluate", "{\"answers\":" + answers + "}");
  }

  private static MockHttpServletRequestBuilder publicPost(String url, String body) {
    return post(url).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private ResultActions perform(long accountId, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(body);
    }
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
                "khao-sat-" + UUID.randomUUID() + "@test.local",
                "khong-dung",
                "Khách thử",
                Role.CUSTOMER));
    accountIds.add(account.getId());
    return account.getId();
  }

  /** Danh mục có đúng tên trong cây câu hỏi; tạo mới nếu CSDL test chưa có. */
  private long category(String name) {
    return categoryRepository
        .findByName(name)
        .map(c -> c.getId())
        .orElseGet(() -> {
          long id = categoryService.create(name).id();
          categoryIds.add(id);
          return id;
        });
  }

  private long brand() {
    long id = brandService.create("Thương hiệu khảo sát " + UUID.randomUUID()).id();
    brandIds.add(id);
    return id;
  }

  private long product(long categoryId, Long brandId, long price) {
    return product(categoryId, brandId, price, "Sản phẩm khảo sát");
  }

  private long product(long categoryId, Long brandId, long price, String name) {
    long id =
        productService
            .create(
                new ProductCreateRequest(
                    name + " " + UUID.randomUUID(),
                    null,
                    null,
                    null,
                    categoryId,
                    brandId,
                    List.of(),
                    List.of(new VariantRequest(null, null, price, 5, null))))
            .id();
    productIds.add(id);
    return id;
  }

  private long count(String sql, Object... args) {
    return jdbcTemplate.queryForObject(sql, Long.class, args);
  }

  private void deleteWhere(String sql, List<Long> ids) {
    if (!ids.isEmpty()) {
      namedJdbcTemplate.update(sql, java.util.Map.of("ids", ids));
    }
  }
}
