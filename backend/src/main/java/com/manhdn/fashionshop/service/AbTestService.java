package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AbTestRequest;
import com.manhdn.fashionshop.dto.AbTestResponse;
import com.manhdn.fashionshop.dto.AbTestResultResponse;
import com.manhdn.fashionshop.dto.AbTestResultResponse.Comparison;
import com.manhdn.fashionshop.dto.AbTestResultResponse.Group;
import com.manhdn.fashionshop.entity.AbExperiment;
import com.manhdn.fashionshop.entity.AbVariant;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AbAssignmentRepository;
import com.manhdn.fashionshop.repository.AbAssignmentRepository.VariantStats;
import com.manhdn.fashionshop.repository.AbExperimentRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.ToLongFunction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thử nghiệm A/B cho gợi ý ở trang chủ (US-27, BR-46).
 *
 * <p>Khi có thử nghiệm đang chạy, khách đủ điều kiện (có danh sách gợi ý riêng do ml-service tính)
 * được chia vào nhóm lúc mở trang chủ lần đầu: TREATMENT thấy gợi ý như bình thường, CONTROL thấy
 * sản phẩm bán chạy. Nhóm được chọn bằng băm SHA-256 của mã thử nghiệm và mã tài khoản, nên ngẫu
 * nhiên giữa các khách nhưng cố định với từng khách, và được lưu lại để thống kê. Kết quả so sánh
 * tỷ lệ theo khách (bấm gợi ý, đặt hàng, mua sản phẩm đã bấm) bằng kiểm định z hai tỷ lệ.
 */
@Service
@Transactional(readOnly = true)
public class AbTestService {

  /** Mỗi nhóm cần ít nhất chừng này khách trước khi kết luận. */
  public static final int MIN_USERS_PER_GROUP = 30;

  /** Mức ý nghĩa của kiểm định. */
  public static final double SIGNIFICANCE = 0.05;

  private final AbExperimentRepository experimentRepository;
  private final AbAssignmentRepository assignmentRepository;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public AbTestService(
      AbExperimentRepository experimentRepository,
      AbAssignmentRepository assignmentRepository,
      Clock clock) {
    this.experimentRepository = experimentRepository;
    this.assignmentRepository = assignmentRepository;
    this.clock = clock;
  }

  /**
   * Bắt đầu một thử nghiệm. Mỗi lúc chỉ chạy 1 thử nghiệm.
   *
   * @param request tên và tỷ lệ khách vào nhóm gợi ý
   * @return thử nghiệm vừa tạo
   * @throws ConflictException nếu đang có thử nghiệm chạy
   */
  @Transactional
  public AbTestResponse start(AbTestRequest request) {
    if (experimentRepository.findFirstByEndedAtIsNullOrderByIdDesc().isPresent()) {
      throw new ConflictException(
          "Đang có thử nghiệm chạy. Dừng thử nghiệm đó trước khi bắt đầu thử nghiệm mới");
    }
    AbExperiment experiment =
        experimentRepository.save(
            new AbExperiment(request.name().strip(), request.treatmentPercent(), now()));
    return AbTestResponse.from(experiment);
  }

  /**
   * Dừng một thử nghiệm: từ lúc này không chia nhóm nữa, mọi khách thấy gợi ý như bình thường. Kết
   * quả chỉ tính hành động trước lúc dừng.
   *
   * @param id mã thử nghiệm
   * @return thử nghiệm sau khi dừng
   * @throws NoSuchElementException nếu không có thử nghiệm này
   * @throws InvalidRequestException nếu thử nghiệm đã dừng
   */
  @Transactional
  public AbTestResponse stop(long id) {
    AbExperiment experiment = find(id);
    if (!experiment.isRunning()) {
      throw new InvalidRequestException("Thử nghiệm đã dừng");
    }
    experiment.stop(now());
    return AbTestResponse.from(experiment);
  }

  /**
   * Danh sách thử nghiệm, mới nhất trước.
   *
   * @return các thử nghiệm
   */
  public List<AbTestResponse> list() {
    return experimentRepository.findAllByOrderByIdDesc().stream()
        .map(AbTestResponse::from)
        .toList();
  }

  /**
   * Kết quả của một thử nghiệm đến lúc dừng (hoặc đến bây giờ nếu còn chạy).
   *
   * @param id mã thử nghiệm
   * @return số liệu 2 nhóm và phép so sánh
   * @throws NoSuchElementException nếu không có thử nghiệm này
   */
  public AbTestResultResponse result(long id) {
    AbExperiment experiment = find(id);
    LocalDateTime end = experiment.isRunning() ? now().plusSeconds(1) : experiment.getEndedAt();
    Map<AbVariant, VariantStats> stats = assignmentRepository.stats(id, end);
    VariantStats treatment = stats.get(AbVariant.TREATMENT);
    VariantStats control = stats.get(AbVariant.CONTROL);
    return new AbTestResultResponse(
        AbTestResponse.from(experiment),
        Group.of(treatment),
        Group.of(control),
        compare(treatment, control, VariantStats::usersClicked),
        compare(treatment, control, VariantStats::usersOrdered),
        compare(treatment, control, VariantStats::usersBoughtClicked),
        MIN_USERS_PER_GROUP);
  }

  /**
   * Nhóm của khách trong thử nghiệm đang chạy. Khách đã được chia giữ nguyên nhóm; khách chưa chia
   * chỉ được chia khi đủ điều kiện.
   *
   * @param accountId mã tài khoản khách hàng
   * @param eligible khách có danh sách gợi ý riêng để so với sản phẩm bán chạy
   * @return nhóm của khách; rỗng khi không có thử nghiệm chạy hoặc khách chưa được chia và không đủ
   *     điều kiện
   */
  @Transactional
  public Optional<AbVariant> variantFor(long accountId, boolean eligible) {
    Optional<AbExperiment> running = experimentRepository.findFirstByEndedAtIsNullOrderByIdDesc();
    if (running.isEmpty()) {
      return Optional.empty();
    }
    AbExperiment experiment = running.get();
    Optional<AbVariant> existing = assignmentRepository.find(experiment.getId(), accountId);
    if (existing.isPresent() || !eligible) {
      return existing;
    }
    AbVariant variant = variantOf(experiment.getId(), accountId, experiment.getTreatmentPercent());
    return Optional.of(assignmentRepository.assign(experiment.getId(), accountId, variant, now()));
  }

  /**
   * Nhóm của một khách theo băm: 4 byte đầu của SHA-256("ab:{thử nghiệm}:{tài khoản}") chia lấy dư
   * 100, nhỏ hơn tỷ lệ nhóm gợi ý thì vào TREATMENT.
   *
   * @param experimentId mã thử nghiệm
   * @param accountId mã tài khoản
   * @param treatmentPercent phần trăm khách vào nhóm gợi ý
   * @return nhóm của khách
   */
  public static AbVariant variantOf(long experimentId, long accountId, int treatmentPercent) {
    try {
      byte[] hash =
          MessageDigest.getInstance("SHA-256")
              .digest(("ab:" + experimentId + ":" + accountId).getBytes(StandardCharsets.UTF_8));
      int bucket = Integer.remainderUnsigned(ByteBuffer.wrap(hash).getInt(), 100);
      return bucket < treatmentPercent ? AbVariant.TREATMENT : AbVariant.CONTROL;
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("JVM không có SHA-256", e);
    }
  }

  private static Comparison compare(
      VariantStats treatment, VariantStats control, ToLongFunction<VariantStats> count) {
    long n1 = treatment.users();
    long n2 = control.users();
    if (n1 == 0 || n2 == 0) {
      return new Comparison(null, null, "NOT_ENOUGH_DATA");
    }
    long x1 = count.applyAsLong(treatment);
    long x2 = count.applyAsLong(control);
    double difference = Math.round(((double) x1 / n1 - (double) x2 / n2) * 10000) / 100.0;
    double p = TwoProportionTest.probabilityOfEqualRates(x1, n1, x2, n2);
    String verdict;
    if (Math.min(n1, n2) < MIN_USERS_PER_GROUP) {
      verdict = "NOT_ENOUGH_DATA";
    } else if (p >= SIGNIFICANCE) {
      verdict = "NO_DIFFERENCE";
    } else {
      verdict = difference > 0 ? "TREATMENT_BETTER" : "CONTROL_BETTER";
    }
    return new Comparison(difference, Math.round(p * 10000) / 10000.0, verdict);
  }

  private AbExperiment find(long id) {
    return experimentRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thử nghiệm"));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
  }
}
