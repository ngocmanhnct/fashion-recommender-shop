package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.SegmentProfileResponse;
import com.manhdn.fashionshop.dto.SegmentProfileResponse.Bucket;
import com.manhdn.fashionshop.dto.SegmentProfileResponse.CareCustomer;
import com.manhdn.fashionshop.dto.SegmentProfileResponse.CategoryShare;
import com.manhdn.fashionshop.entity.CustomerSegment;
import com.manhdn.fashionshop.repository.AccountSegmentRepository;
import com.manhdn.fashionshop.repository.CustomerSegmentRepository;
import com.manhdn.fashionshop.repository.SegmentProfileRepository;
import com.manhdn.fashionshop.repository.SegmentProfileRepository.CustomerPurchases;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hồ sơ một nhóm khách hàng cho Quản trị viên (US-25): mô tả nhóm bằng các con số dễ hành động hơn
 * R, F, M (doanh thu, giá trị đơn, nhịp mua, gắn bó, danh mục, giới tính, độ tuổi) và chỉ ra khách
 * cần chăm sóc (BR-43). Chỉ đọc; nhóm của khách do ml-service ghi (BR-23).
 */
@Service
public class SegmentProfileService {

  /** Số danh mục mua nhiều nhất trả về. */
  static final int TOP_CATEGORIES = 5;

  /** Số khách cần chăm sóc trả về (quá nhịp mua nhiều nhất trước). */
  static final int CARE_LIST_SIZE = 20;

  /** BR-43: quá nhịp mua khi số ngày chưa mua vượt 1,5 lần nhịp mua riêng... */
  static final double CARE_FACTOR = 1.5;

  /** ...và vượt ít nhất 30 ngày, để khách mua dồn nhiều đơn trong vài ngày không bị báo nhầm. */
  static final long CARE_MIN_DAYS = 30;

  static final List<String> GENDERS = List.of("MALE", "FEMALE", "OTHER", "UNKNOWN");

  static final List<String> AGE_GROUPS =
      List.of("UNDER_18", "18_24", "25_34", "35_44", "45_54", "55_PLUS", "UNKNOWN");

  private static final double SECONDS_PER_DAY = 86_400.0;

  private final CustomerSegmentRepository customerSegmentRepository;
  private final AccountSegmentRepository accountSegmentRepository;
  private final SegmentProfileRepository profileRepository;
  private final Clock clock;
  private final ZoneId zone;

  /** Nhận các repository và đồng hồ qua constructor. */
  public SegmentProfileService(
      CustomerSegmentRepository customerSegmentRepository,
      AccountSegmentRepository accountSegmentRepository,
      SegmentProfileRepository profileRepository,
      Clock clock) {
    this.customerSegmentRepository = customerSegmentRepository;
    this.accountSegmentRepository = accountSegmentRepository;
    this.profileRepository = profileRepository;
    this.clock = clock;
    this.zone = clock.getZone();
  }

  /**
   * Hồ sơ của nhóm có mã code.
   *
   * @param code mã nhóm, vd VIP
   * @return số liệu của nhóm tại thời điểm gọi
   * @throws NoSuchElementException nếu không có nhóm này
   */
  @Transactional(readOnly = true)
  public SegmentProfileResponse profile(String code) {
    CustomerSegment segment =
        customerSegmentRepository
            .findByCode(code)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy nhóm khách hàng"));
    long segmentId = segment.getId();
    LocalDateTime now = LocalDateTime.now(clock);

    Map<Long, Long> revenueBySegment = profileRepository.revenueBySegment();
    long totalRevenue = revenueBySegment.values().stream().mapToLong(Long::longValue).sum();
    long revenue = revenueBySegment.getOrDefault(segmentId, 0L);
    Map<String, Long> genders = profileRepository.genderCounts(segmentId);
    long customers = genders.values().stream().mapToLong(Long::longValue).sum();

    List<CustomerPurchases> purchases = profileRepository.purchases(segmentId);
    long orders = purchases.stream().mapToLong(CustomerPurchases::orders).sum();
    List<CustomerPurchases> repeat = purchases.stream().filter(p -> p.orders() > 1).toList();
    Double avgInterval =
        repeat.isEmpty()
            ? null
            : round1(repeat.stream().mapToDouble(this::intervalDays).average().orElse(0));
    double avgTenure =
        round1(
            purchases.stream().mapToDouble(p -> days(p.firstOrderAt(), now)).average().orElse(0));
    List<CareCustomer> care =
        needingCare(repeat, now).stream().map(p -> careCustomer(p, now)).toList();
    long avgOrderValue = orders == 0 ? 0 : Math.round((double) revenue / orders);

    long totalQuantity = profileRepository.totalQuantity(segmentId);
    List<CategoryShare> categories =
        profileRepository.topCategories(segmentId, TOP_CATEGORIES).stream()
            .map(
                c ->
                    new CategoryShare(c.name(), c.quantity(), percent(c.quantity(), totalQuantity)))
            .toList();

    return new SegmentProfileResponse(
        segment.getCode(),
        segment.getName(),
        segment.getDescription(),
        customers,
        percent(customers, accountSegmentRepository.count()),
        revenue,
        percent(revenue, totalRevenue),
        orders,
        avgOrderValue,
        avgInterval,
        repeat.size(),
        avgTenure,
        categories,
        buckets(GENDERS, genders, customers),
        buckets(AGE_GROUPS, profileRepository.ageCounts(segmentId, now.toLocalDate()), customers),
        care.size(),
        care.stream().limit(CARE_LIST_SIZE).toList(),
        SegmentPlaybook.suggest(segment.getCode(), avgOrderValue));
  }

  /**
   * Mã tài khoản của mọi khách cần chăm sóc trong nhóm (BR-43), để tặng mã (US-26).
   *
   * @param segmentId mã số nhóm
   * @return khách quá nhịp mua nhiều nhất trước
   */
  @Transactional(readOnly = true)
  public List<Long> careAccountIds(long segmentId) {
    List<CustomerPurchases> repeat =
        profileRepository.purchases(segmentId).stream().filter(p -> p.orders() > 1).toList();
    return needingCare(repeat, LocalDateTime.now(clock)).stream()
        .map(CustomerPurchases::accountId)
        .toList();
  }

  /** Khách mua từ 2 đơn đang cần chăm sóc, quá nhịp mua nhiều nhất trước. */
  private List<CustomerPurchases> needingCare(List<CustomerPurchases> repeat, LocalDateTime now) {
    return repeat.stream()
        .filter(p -> needsCare(p, now))
        .sorted(
            Comparator.comparingDouble((CustomerPurchases p) -> overdueRatio(p, now)).reversed())
        .toList();
  }

  /** Nhịp mua riêng của khách mua từ 2 đơn: số ngày trung bình giữa 2 đơn liên tiếp. */
  private double intervalDays(CustomerPurchases p) {
    return days(p.firstOrderAt(), p.lastOrderAt()) / (p.orders() - 1);
  }

  /** BR-43: khách mua từ 2 đơn, chưa mua lại quá 1,5 lần nhịp riêng và quá 30 ngày. */
  private boolean needsCare(CustomerPurchases p, LocalDateTime now) {
    double since = days(p.lastOrderAt(), now);
    return since > Math.max(CARE_FACTOR * intervalDays(p), CARE_MIN_DAYS);
  }

  private double overdueRatio(CustomerPurchases p, LocalDateTime now) {
    return days(p.lastOrderAt(), now) / Math.max(intervalDays(p), 1);
  }

  private CareCustomer careCustomer(CustomerPurchases p, LocalDateTime now) {
    return new CareCustomer(
        p.accountId(),
        p.fullName(),
        p.email(),
        p.orders(),
        p.lastOrderAt(),
        round1(intervalDays(p)),
        (long) Math.floor(days(p.lastOrderAt(), now)));
  }

  private static List<Bucket> buckets(List<String> keys, Map<String, Long> counts, long total) {
    return keys.stream()
        .map(
            key -> {
              long count = counts.getOrDefault(key, 0L);
              return new Bucket(key, count, percent(count, total));
            })
        .toList();
  }

  /** Số ngày (có phần lẻ) giữa 2 mốc giờ địa phương, tính theo múi giờ của đồng hồ. */
  private double days(LocalDateTime from, LocalDateTime to) {
    return Duration.between(from.atZone(zone), to.atZone(zone)).toSeconds() / SECONDS_PER_DAY;
  }

  private static double percent(long part, long total) {
    return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
  }

  private static double round1(double value) {
    return Math.round(value * 10) / 10.0;
  }
}
