package com.manhdn.fashionshop.service;

import static com.manhdn.fashionshop.repository.ProductSpecifications.forSale;
import static com.manhdn.fashionshop.repository.ProductSpecifications.hasBrand;
import static com.manhdn.fashionshop.repository.ProductSpecifications.hasVariantPriceBetween;
import static com.manhdn.fashionshop.repository.ProductSpecifications.idNotIn;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inCategories;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inStock;
import static com.manhdn.fashionshop.repository.ProductSpecifications.ofBrand;
import static com.manhdn.fashionshop.repository.ProductSpecifications.unratedOrRatingAtLeast;

import com.manhdn.fashionshop.dto.BrandResponse;
import com.manhdn.fashionshop.dto.CategoryResponse;
import com.manhdn.fashionshop.dto.MySurveyResponse;
import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.RelatedProductResponse;
import com.manhdn.fashionshop.dto.SurveyAnswer;
import com.manhdn.fashionshop.dto.SurveyEvaluationResponse;
import com.manhdn.fashionshop.dto.SurveyOptionResponse;
import com.manhdn.fashionshop.dto.SurveyProfileResponse;
import com.manhdn.fashionshop.dto.SurveyQuestionResponse;
import com.manhdn.fashionshop.dto.SurveyResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.entity.BrandLoyalty;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.entity.Gender;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.entity.ShoppingStyle;
import com.manhdn.fashionshop.entity.SurveyAnswerType;
import com.manhdn.fashionshop.entity.SurveyOption;
import com.manhdn.fashionshop.entity.SurveyPurpose;
import com.manhdn.fashionshop.entity.SurveyQuestion;
import com.manhdn.fashionshop.entity.SurveyResult;
import com.manhdn.fashionshop.entity.SurveyStatus;
import com.manhdn.fashionshop.entity.UserPreference;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.BrandRepository;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.SurveyQuestionRepository;
import com.manhdn.fashionshop.repository.SurveyResultRepository;
import com.manhdn.fashionshop.repository.UserPreferenceRepository;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Khảo sát lần đầu dạng rẽ nhánh theo phong cách mua sắm (US-23, BR-37, BR-38, BR-41, BR-42).
 *
 * <p>Cây câu hỏi lưu trong CSDL (migration V5). Client gửi các câu trả lời theo đúng thứ tự đã đi
 * qua cây; service đi lại cây từ câu đầu tiên để kiểm tra và dựng hồ sơ mua sắm: mục đích, phong
 * cách, danh mục (từ dịp sử dụng, lọc theo đồ nam hoặc nữ), thương hiệu, khoảng giá, ngưỡng đánh
 * giá. Kết quả nhánh mua cho bản thân được lưu (user_preference, survey_result) và yêu cầu tính lại
 * gợi ý (BR-20); kết quả nhánh tìm quà chỉ dùng để gợi ý ngay, không lưu (BR-42).
 *
 * <p>Sản phẩm gợi ý ngay sau khảo sát xếp theo quy tắc của từng phong cách; bước xếp hạng lại bằng
 * dịch vụ học máy (công thức 1.4) làm ở Tuần 8.
 */
@Service
public class SurveyService {

  /** Mã câu hỏi đầu tiên. */
  public static final String START_QUESTION = "Q1";

  /** Số sản phẩm gợi ý trả về sau khảo sát. */
  public static final int RECOMMENDATION_LIMIT = 8;

  /** Số thương hiệu tối đa hiện ở câu chọn thương hiệu. */
  public static final int BRAND_CHOICE_LIMIT = 15;

  static final String STATUS_NONE = "NONE";
  static final String REASON_PRICE = "Giá tốt trong tầm giá bạn chọn";
  static final String REASON_FAVORITE_BRAND = "Thương hiệu bạn tin dùng";
  static final String REASON_FITS_NEEDS = "Hợp với nhu cầu của bạn";
  static final String REASON_TRENDING = "Đang bán chạy trong danh mục bạn quan tâm";
  static final String REASON_GIFT_BRAND = "Món quà có thương hiệu, dễ tặng";
  static final String REASON_GIFT_FITS = "Hợp với người nhận";
  static final String REASON_CATEGORY_BEST_SELLER = "Bán chạy trong danh mục bạn quan tâm";
  static final String REASON_BEST_SELLER = "Sản phẩm bán chạy";

  private static final String MALE_SUFFIX = " nam";
  private static final String FEMALE_SUFFIX = " nữ";
  private static final Sort PRICE_ASC_ORDER = Sort.by(Order.asc("minPrice"), Order.asc("id"));
  private static final Sort QUALITY_ORDER =
      Sort.by(
          Order.desc("ratingAverage"),
          Order.desc("soldCount"),
          Order.asc("categoryRank"),
          Order.asc("id"));
  private static final TypeReference<List<SurveyAnswer>> ANSWER_LIST = new TypeReference<>() {};

  private final SurveyQuestionRepository questionRepository;
  private final SurveyResultRepository resultRepository;
  private final UserPreferenceRepository preferenceRepository;
  private final CategoryRepository categoryRepository;
  private final BrandRepository brandRepository;
  private final ProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final RecomputeRequestService recomputeRequestService;
  private final TransactionRetry transactionRetry;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public SurveyService(
      SurveyQuestionRepository questionRepository,
      SurveyResultRepository resultRepository,
      UserPreferenceRepository preferenceRepository,
      CategoryRepository categoryRepository,
      BrandRepository brandRepository,
      ProductRepository productRepository,
      AccountRepository accountRepository,
      RecomputeRequestService recomputeRequestService,
      TransactionRetry transactionRetry,
      JsonMapper jsonMapper,
      Clock clock) {
    this.questionRepository = questionRepository;
    this.resultRepository = resultRepository;
    this.preferenceRepository = preferenceRepository;
    this.categoryRepository = categoryRepository;
    this.brandRepository = brandRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.recomputeRequestService = recomputeRequestService;
    this.transactionRetry = transactionRetry;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  /**
   * Toàn bộ cây câu hỏi để client hiển thị và rẽ nhánh.
   *
   * @return câu hỏi đầu tiên và mọi câu hỏi kèm đáp án
   */
  @Transactional(readOnly = true)
  public SurveyResponse getSurvey() {
    Tree tree = loadTree();
    List<SurveyQuestionResponse> questions =
        tree.byId.values().stream().map(q -> toResponse(q, tree)).toList();
    return new SurveyResponse(START_QUESTION, questions);
  }

  /**
   * Các thương hiệu cho câu chọn thương hiệu: thương hiệu có sản phẩm đang bán trong các danh mục
   * suy ra từ những câu đã trả lời, nhiều sản phẩm trước, tối đa {@link #BRAND_CHOICE_LIMIT}.
   *
   * @param answers các câu trả lời từ câu đầu tiên đến ngay trước câu chọn thương hiệu
   * @return danh sách thương hiệu, có thể rỗng
   * @throws InvalidRequestException nếu câu trả lời sai hoặc chưa đi tới câu chọn thương hiệu
   */
  @Transactional(readOnly = true)
  public List<BrandResponse> brandChoices(List<SurveyAnswer> answers) {
    Walk walk = walk(loadTree(), answers);
    if (walk.next == null || walk.next.getAnswerType() != SurveyAnswerType.BRAND) {
      throw new InvalidRequestException("Câu tiếp theo không phải câu chọn thương hiệu");
    }
    return brandsIn(categoryIds(walk)).stream().map(BrandResponse::from).toList();
  }

  /**
   * Dựng hồ sơ mua sắm và sản phẩm gợi ý từ câu trả lời, không lưu gì. Dùng cho cả hai nhánh, cho
   * khách chưa đăng nhập và cho trang chủ của khách đã làm khảo sát.
   *
   * @param answers câu trả lời của một lần làm hết khảo sát
   * @return hồ sơ và tối đa {@link #RECOMMENDATION_LIMIT} sản phẩm kèm lý do
   * @throws InvalidRequestException nếu câu trả lời sai hoặc chưa trả lời hết
   */
  @Transactional(readOnly = true)
  public SurveyEvaluationResponse evaluate(List<SurveyAnswer> answers) {
    Profile profile = completedProfile(answers);
    return new SurveyEvaluationResponse(profile.toResponse(), recommend(profile));
  }

  /**
   * Khảo sát của tài khoản: trạng thái, câu trả lời và hồ sơ của lần làm gần nhất.
   *
   * @param accountId mã tài khoản Khách hàng
   * @return NONE nếu chưa làm và chưa bỏ qua
   */
  @Transactional(readOnly = true)
  public MySurveyResponse getMine(long accountId) {
    return resultRepository
        .findById(accountId)
        .map(this::toMine)
        .orElseGet(() -> new MySurveyResponse(STATUS_NONE, List.of(), null));
  }

  /**
   * Lưu kết quả khảo sát nhánh mua cho bản thân: ghi đè sở thích cũ (làm lại khảo sát, BR-37), lưu
   * phong cách mua sắm và yêu cầu tính lại gợi ý sau vài giây (BR-20).
   *
   * @param accountId mã tài khoản Khách hàng
   * @param answers câu trả lời của một lần làm hết khảo sát
   * @return khảo sát của tài khoản sau khi lưu
   * @throws InvalidRequestException nếu câu trả lời sai, chưa trả lời hết hoặc là nhánh tìm quà
   */
  public MySurveyResponse saveMine(long accountId, List<SurveyAnswer> answers) {
    return transactionRetry.execute(() -> save(accountId, answers));
  }

  /**
   * Ghi nhận khách bỏ qua khảo sát để không tự hiện lại (BR-37). Tài khoản đã làm khảo sát thì giữ
   * nguyên kết quả.
   *
   * @param accountId mã tài khoản Khách hàng
   * @return khảo sát của tài khoản
   */
  public MySurveyResponse skipMine(long accountId) {
    return transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          SurveyResult result =
              resultRepository
                  .findById(accountId)
                  .orElseGet(
                      () ->
                          resultRepository.save(
                              new SurveyResult(
                                  accountId, SurveyStatus.SKIPPED, LocalDateTime.now(clock))));
          return toMine(result);
        });
  }

  private MySurveyResponse save(long accountId, List<SurveyAnswer> answers) {
    Account account = lockAccount(accountId);
    Profile profile = completedProfile(answers);
    if (profile.purpose == SurveyPurpose.GIFT) {
      throw new InvalidRequestException(
          "Kết quả tìm quà tặng chỉ dùng để gợi ý, không lưu vào sở thích của bạn");
    }
    LocalDateTime now = LocalDateTime.now(clock);
    preferenceRepository.deleteByAccountId(accountId);
    for (Category category : profile.categories) {
      preferenceRepository.save(
          new UserPreference(
              account, category, profile.brand, profile.priceMin, profile.priceMax, now));
    }
    SurveyResult result =
        resultRepository
            .findById(accountId)
            .orElseGet(() -> new SurveyResult(accountId, SurveyStatus.COMPLETED, now));
    result.complete(
        profile.style,
        profile.brandLoyalty,
        profile.minRating,
        jsonMapper.writeValueAsString(answers),
        now);
    resultRepository.save(result);
    recomputeRequestService.request(
        accountId, RecomputeTask.RECOMMENDATION, RecomputeRequestService.STRONG_ACTION_DELAY);
    return new MySurveyResponse(SurveyStatus.COMPLETED.name(), answers, profile.toResponse());
  }

  private MySurveyResponse toMine(SurveyResult result) {
    if (result.getStatus() != SurveyStatus.COMPLETED || result.getAnswers() == null) {
      return new MySurveyResponse(result.getStatus().name(), List.of(), null);
    }
    List<SurveyAnswer> answers = jsonMapper.readValue(result.getAnswers(), ANSWER_LIST);
    SurveyProfileResponse profile;
    try {
      profile = completedProfile(answers).toResponse();
    } catch (InvalidRequestException e) {
      // Cây câu hỏi đã đổi sau lần làm khảo sát: còn trạng thái, khách có thể làm lại.
      profile = null;
    }
    return new MySurveyResponse(result.getStatus().name(), answers, profile);
  }

  private Account lockAccount(long accountId) {
    return accountRepository
        .findWithLockById(accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
  }

  // ---------------------------------------------------------------------------------------------
  // Đi qua cây câu hỏi
  // ---------------------------------------------------------------------------------------------

  private Tree loadTree() {
    List<SurveyQuestion> questions = questionRepository.findAllByOrderByIdAsc();
    return new Tree(questions);
  }

  private Profile completedProfile(List<SurveyAnswer> answers) {
    Walk walk = walk(loadTree(), answers);
    if (answers.isEmpty() || walk.next != null) {
      throw new InvalidRequestException("Bạn chưa trả lời hết khảo sát");
    }
    List<Category> categories = categoriesOf(walk);
    Brand brand =
        walk.brandId == null
            ? null
            : brandRepository
                .findById(walk.brandId)
                .orElseThrow(() -> new InvalidRequestException("Không tìm thấy thương hiệu"));
    long priceMin = walk.priceMin == null ? 0 : walk.priceMin;
    Long priceMax = walk.priceMax;
    if (priceMax != null && Boolean.TRUE.equals(walk.priceFlexible)) {
      priceMax = priceMax * 13 / 10;
    }
    ShoppingStyle style = walk.purpose == SurveyPurpose.GIFT ? ShoppingStyle.GIFT : walk.style;
    return new Profile(
        walk.purpose,
        style,
        categories,
        brand,
        walk.brandLoyalty,
        priceMin,
        priceMax,
        walk.minRating);
  }

  /** Đi cây câu hỏi từ câu đầu tiên theo các câu trả lời, gộp thông tin của từng đáp án. */
  private Walk walk(Tree tree, List<SurveyAnswer> answers) {
    Walk walk = new Walk();
    SurveyQuestion current = tree.byCode.get(START_QUESTION);
    for (SurveyAnswer answer : answers) {
      if (current == null) {
        throw new InvalidRequestException("Khảo sát đã kết thúc nhưng vẫn còn câu trả lời");
      }
      if (!current.getCode().equals(answer.questionCode())) {
        throw new InvalidRequestException(
            "Câu trả lời không đúng thứ tự, cần trả lời câu " + current.getCode());
      }
      Long nextId =
          current.getAnswerType() == SurveyAnswerType.BRAND
              ? applyBrand(current, answer, walk)
              : applyOptions(current, answer, walk);
      current = nextId == null ? null : tree.byId.get(nextId);
    }
    walk.next = current;
    return walk;
  }

  private Long applyBrand(SurveyQuestion question, SurveyAnswer answer, Walk walk) {
    if (!answer.chosenCodes().isEmpty()) {
      throw new InvalidRequestException("Câu chọn thương hiệu không có mã đáp án");
    }
    if (answer.brandId() != null) {
      boolean offered =
          brandsIn(categoryIds(walk)).stream().anyMatch(b -> b.getId().equals(answer.brandId()));
      if (!offered) {
        throw new InvalidRequestException("Thương hiệu không thuộc các danh mục bạn quan tâm");
      }
      walk.brandId = answer.brandId();
    }
    return question.getNextQuestionId();
  }

  private Long applyOptions(SurveyQuestion question, SurveyAnswer answer, Walk walk) {
    List<String> codes = answer.chosenCodes();
    int max = question.getAnswerType() == SurveyAnswerType.SINGLE ? 1 : question.getMaxChoices();
    if (codes.isEmpty() || codes.size() > max) {
      throw new InvalidRequestException(
          max == 1
              ? "Câu " + question.getCode() + " cần chọn đúng 1 đáp án"
              : "Câu " + question.getCode() + " cần chọn từ 1 đến " + max + " đáp án");
    }
    if (new HashSet<>(codes).size() != codes.size()) {
      throw new InvalidRequestException("Đáp án bị chọn trùng");
    }
    Map<String, SurveyOption> options =
        question.getOptions().stream()
            .collect(Collectors.toMap(SurveyOption::getCode, Function.identity()));
    Long nextId = null;
    for (int i = 0; i < codes.size(); i++) {
      SurveyOption option = options.get(codes.get(i));
      if (option == null) {
        throw new InvalidRequestException(
            "Đáp án " + codes.get(i) + " không có trong câu " + question.getCode());
      }
      walk.apply(option);
      if (i == 0) {
        nextId = option.getNextQuestionId();
      }
    }
    return nextId;
  }

  /** Danh mục ứng với các dịp đã chọn, bỏ danh mục của giới tính còn lại. */
  private List<Category> categoriesOf(Walk walk) {
    Set<String> names = new LinkedHashSet<>();
    for (String name : walk.categoryNames) {
      boolean excluded =
          (walk.gender == Gender.MALE && name.endsWith(FEMALE_SUFFIX))
              || (walk.gender == Gender.FEMALE && name.endsWith(MALE_SUFFIX));
      if (!excluded) {
        names.add(name);
      }
    }
    return names.isEmpty() ? List.of() : categoryRepository.findByNameInOrderByIdAsc(names);
  }

  private List<Long> categoryIds(Walk walk) {
    return categoriesOf(walk).stream().map(Category::getId).toList();
  }

  private List<Brand> brandsIn(List<Long> categoryIds) {
    return categoryIds.isEmpty()
        ? List.of()
        : brandRepository.findTopInCategories(categoryIds, PageRequest.of(0, BRAND_CHOICE_LIMIT));
  }

  // ---------------------------------------------------------------------------------------------
  // Gợi ý theo phong cách (tạm thời theo quy tắc, đến khi có xếp hạng của dịch vụ học máy)
  // ---------------------------------------------------------------------------------------------

  private List<RelatedProductResponse> recommend(Profile profile) {
    List<Long> categoryIds = profile.categories.stream().map(Category::getId).toList();
    Specification<Product> inInterest = forSale().and(inStock()).and(inCategories(categoryIds));
    Specification<Product> matching =
        inInterest.and(
            hasVariantPriceBetween(
                profile.priceMin > 0 ? profile.priceMin : null, profile.priceMax));
    Picks picks = new Picks();
    switch (profile.style) {
      case SAVER -> picks.add(matching, PRICE_ASC_ORDER, REASON_PRICE);
      case BRAND -> {
        if (profile.brand != null) {
          picks.add(
              matching.and(ofBrand(profile.brand.getId())),
              ProductCatalogService.POPULAR_ORDER,
              REASON_FAVORITE_BRAND);
        }
        picks.add(matching, ProductCatalogService.POPULAR_ORDER, REASON_FITS_NEEDS);
      }
      case QUALITY -> {
        double threshold = profile.minRating == null ? 0 : profile.minRating.doubleValue();
        picks.add(matching.and(unratedOrRatingAtLeast(threshold)), QUALITY_ORDER, null);
      }
      case TREND -> picks.add(matching, ProductCatalogService.POPULAR_ORDER, REASON_TRENDING);
      default -> {
        // GIFT: quà có thương hiệu trước.
        picks.add(matching.and(hasBrand()), ProductCatalogService.POPULAR_ORDER, REASON_GIFT_BRAND);
        picks.add(matching, ProductCatalogService.POPULAR_ORDER, REASON_GIFT_FITS);
      }
    }
    picks.add(inInterest, ProductCatalogService.POPULAR_ORDER, REASON_CATEGORY_BEST_SELLER);
    picks.add(forSale().and(inStock()), ProductCatalogService.POPULAR_ORDER, REASON_BEST_SELLER);
    return picks.result;
  }

  /** Lý do cho Người cầu toàn: điểm đánh giá nếu có, chưa có thì nói là bán chạy. */
  private static String qualityReason(Product product) {
    Double rating = product.getRatingAverage();
    return rating == null
        ? REASON_CATEGORY_BEST_SELLER
        : "Được đánh giá " + String.valueOf(rating).replace('.', ',') + " sao";
  }

  /** Danh sách gợi ý đang dựng: thêm theo từng tầng đến khi đủ, không lặp sản phẩm. */
  private final class Picks {
    private final List<RelatedProductResponse> result = new ArrayList<>();
    private final Set<Long> chosen = new HashSet<>();

    /** Thêm sản phẩm thỏa điều kiện theo thứ tự cho trước; lý do null = lý do theo đánh giá. */
    void add(Specification<Product> filter, Sort order, String reason) {
      int missing = RECOMMENDATION_LIMIT - result.size();
      if (missing <= 0) {
        return;
      }
      Specification<Product> spec = filter.and(idNotIn(Set.copyOf(chosen)));
      for (Product p : productRepository.findBy(spec, q -> q.sortBy(order).limit(missing).all())) {
        chosen.add(p.getId());
        result.add(
            new RelatedProductResponse(
                ProductCardResponse.from(p), reason == null ? qualityReason(p) : reason));
      }
    }
  }

  private static SurveyQuestionResponse toResponse(SurveyQuestion q, Tree tree) {
    List<SurveyOptionResponse> options =
        q.getOptions().stream()
            .map(o -> new SurveyOptionResponse(o.getCode(), o.getLabel(), tree.codeOf(o)))
            .toList();
    return new SurveyQuestionResponse(
        q.getCode(),
        q.getContent(),
        q.getHint(),
        q.getAnswerType(),
        q.getMaxChoices(),
        tree.codeOf(q.getNextQuestionId()),
        options);
  }

  /** Cây câu hỏi đã nạp, tra theo mã và theo id. */
  private static final class Tree {
    private final Map<Long, SurveyQuestion> byId = new LinkedHashMap<>();
    private final Map<String, SurveyQuestion> byCode = new HashMap<>();

    Tree(List<SurveyQuestion> questions) {
      for (SurveyQuestion q : questions) {
        byId.put(q.getId(), q);
        byCode.put(q.getCode(), q);
      }
    }

    String codeOf(SurveyOption option) {
      return codeOf(option.getNextQuestionId());
    }

    String codeOf(Long questionId) {
      SurveyQuestion q = questionId == null ? null : byId.get(questionId);
      return q == null ? null : q.getCode();
    }
  }

  /** Thông tin gộp được khi đi qua cây câu hỏi. */
  private static final class Walk {
    private SurveyPurpose purpose;
    private Gender gender;
    private ShoppingStyle style;
    private final Set<String> categoryNames = new LinkedHashSet<>();
    private Long priceMin;
    private Long priceMax;
    private Boolean priceFlexible;
    private Long brandId;
    private BrandLoyalty brandLoyalty;
    private BigDecimal minRating;
    private SurveyQuestion next;

    void apply(SurveyOption option) {
      if (option.getPurpose() != null) {
        purpose = option.getPurpose();
      }
      if (option.getGender() != null) {
        gender = option.getGender();
      }
      if (option.getStyle() != null) {
        style = option.getStyle();
      }
      categoryNames.addAll(option.getCategoryNames().stream().sorted().toList());
      if (option.getPriceMin() != null || option.getPriceMax() != null) {
        priceMin = option.getPriceMin();
        priceMax = option.getPriceMax();
      }
      if (option.getPriceFlexible() != null) {
        priceFlexible = option.getPriceFlexible();
      }
      if (option.getBrandLoyalty() != null) {
        brandLoyalty = option.getBrandLoyalty();
      }
      if (option.getMinRating() != null) {
        minRating = option.getMinRating();
      }
    }
  }

  /** Hồ sơ mua sắm của một lần làm hết khảo sát. */
  private record Profile(
      SurveyPurpose purpose,
      ShoppingStyle style,
      List<Category> categories,
      Brand brand,
      BrandLoyalty brandLoyalty,
      long priceMin,
      Long priceMax,
      BigDecimal minRating) {

    SurveyProfileResponse toResponse() {
      return new SurveyProfileResponse(
          purpose,
          style,
          style.displayName(),
          summary(),
          categories.stream().map(CategoryResponse::from).toList(),
          BrandResponse.from(brand),
          brandLoyalty,
          priceMin,
          priceMax,
          minRating);
    }

    private String summary() {
      String head = "Bạn là " + style.displayName() + ": ";
      return switch (style) {
        case SAVER -> head + "ưu tiên món đáng tiền" + pricePhrase() + ".";
        case BRAND ->
            brand == null
                ? head + "ưu tiên sản phẩm có thương hiệu" + pricePhrase() + "."
                : head
                    + "ưu tiên "
                    + brand.getName()
                    + (brandLoyalty == BrandLoyalty.OPEN ? " và sẵn sàng thử thương hiệu mới" : "")
                    + pricePhrase()
                    + ".";
        case QUALITY ->
            head
                + "ưu tiên món bền, "
                + (minRating == null
                    ? "chất lượng tốt"
                    : "được đánh giá từ "
                        + minRating.stripTrailingZeros().toPlainString().replace('.', ',')
                        + " sao")
                + pricePhrase()
                + ".";
        case TREND -> head + "ưu tiên món đang được nhiều người mua" + pricePhrase() + ".";
        case GIFT -> "Bạn đang tìm quà: ưu tiên món có thương hiệu, dễ tặng" + pricePhrase() + ".";
      };
    }

    private String pricePhrase() {
      if (priceMin <= 0 && priceMax == null) {
        return "";
      }
      if (priceMin <= 0) {
        return ", giá dưới " + vnd(priceMax);
      }
      if (priceMax == null) {
        return ", giá từ " + vnd(priceMin) + " trở lên";
      }
      return ", giá từ " + vnd(priceMin) + " đến " + vnd(priceMax);
    }

    private static String vnd(long amount) {
      NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
      return format.format(amount) + "đ";
    }
  }
}
