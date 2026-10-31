package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CustomerDetailResponse;
import com.manhdn.fashionshop.dto.CustomerSummaryResponse;
import com.manhdn.fashionshop.dto.OrderSummaryResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.SegmentOverviewResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.AccountSegment;
import com.manhdn.fashionshop.entity.CustomerSegment;
import com.manhdn.fashionshop.entity.Role;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.AccountSegmentRepository;
import com.manhdn.fashionshop.repository.AccountSegmentRepository.SegmentStats;
import com.manhdn.fashionshop.repository.CustomerSegmentRepository;
import com.manhdn.fashionshop.repository.OrderRepository;
import com.manhdn.fashionshop.repository.OrderRepository.OrderTotals;
import com.manhdn.fashionshop.repository.SegmentModelRepository;
import com.manhdn.fashionshop.repository.SegmentProfileRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiệp vụ xem khách hàng (US-18) và kết quả phân nhóm khách hàng (US-19) cho Quản trị viên. Chỉ
 * đọc: nhóm do ml-service tính và ghi (BR-23).
 */
@Service
public class CustomerAdminService {

  /** Giá trị lọc "chưa được phân nhóm" (BR-24) của tham số segment. */
  public static final String UNSEGMENTED = "NONE";

  /** Thứ tự hiện các nhóm: giá trị cao trước. Mã lạ (nếu ml-service thêm) đứng cuối. */
  static final List<String> SEGMENT_ORDER =
      List.of("VIP", "LOYAL", "SAVER", "NEW", "AT_RISK", "LOST");

  private static final Pattern SEGMENT_CODE = Pattern.compile("[A-Z_]{1,20}");

  private final AccountRepository accountRepository;
  private final AccountSegmentRepository accountSegmentRepository;
  private final CustomerSegmentRepository customerSegmentRepository;
  private final SegmentModelRepository segmentModelRepository;
  private final OrderRepository orderRepository;
  private final SegmentProfileRepository segmentProfileRepository;

  /** Nhận các repository qua constructor. */
  public CustomerAdminService(
      AccountRepository accountRepository,
      AccountSegmentRepository accountSegmentRepository,
      CustomerSegmentRepository customerSegmentRepository,
      SegmentModelRepository segmentModelRepository,
      OrderRepository orderRepository,
      SegmentProfileRepository segmentProfileRepository) {
    this.accountRepository = accountRepository;
    this.accountSegmentRepository = accountSegmentRepository;
    this.customerSegmentRepository = customerSegmentRepository;
    this.segmentModelRepository = segmentModelRepository;
    this.orderRepository = orderRepository;
    this.segmentProfileRepository = segmentProfileRepository;
  }

  /**
   * Danh sách khách hàng, mới đăng ký trước (US-18).
   *
   * @param keyword một phần họ tên hoặc email; trống là không lọc
   * @param segment mã nhóm (vd VIP), {@link #UNSEGMENTED} là khách chưa được phân nhóm; trống là
   *     mọi khách
   * @param page số trang, bắt đầu từ 0
   * @param size số khách mỗi trang, từ 1 đến {@link OrderService#MAX_PAGE_SIZE}
   * @return một trang khách hàng
   * @throws InvalidRequestException nếu tham số sai
   */
  @Transactional(readOnly = true)
  public PageResponse<CustomerSummaryResponse> list(
      String keyword, String segment, int page, int size) {
    checkPage(page, size);
    String cleaned = keyword == null ? "" : keyword.strip();
    if (cleaned.length() > OrderAdminService.MAX_KEYWORD_LENGTH) {
      throw new InvalidRequestException(
          "Từ khóa tối đa %d ký tự".formatted(OrderAdminService.MAX_KEYWORD_LENGTH));
    }
    String code = segment == null || segment.isBlank() ? null : segment.strip();
    if (code != null && !SEGMENT_CODE.matcher(code).matches()) {
      throw new InvalidRequestException("Mã nhóm không hợp lệ");
    }
    boolean unsegmented = UNSEGMENTED.equals(code);
    return PageResponse.from(
        accountRepository.searchCustomers(
            cleaned.isEmpty() ? null : cleaned,
            unsegmented ? null : code,
            unsegmented,
            PageRequest.of(page, size)),
        CustomerSummaryResponse::from);
  }

  /**
   * Chi tiết một khách hàng kèm nhóm hiện tại (US-18, US-19).
   *
   * @param accountId mã tài khoản
   * @return thông tin, thống kê đơn và nhóm của khách
   * @throws NoSuchElementException nếu không có khách hàng này
   */
  @Transactional(readOnly = true)
  public CustomerDetailResponse get(long accountId) {
    Account account = findCustomer(accountId);
    OrderTotals totals = orderRepository.totalsForAccount(accountId);
    CustomerDetailResponse.SegmentInfo segment =
        accountSegmentRepository
            .findWithSegment(accountId)
            .map(CustomerAdminService::segmentInfo)
            .orElse(null);
    return new CustomerDetailResponse(
        account.getId(),
        account.getFullName(),
        account.getEmail(),
        account.getGender(),
        account.getBirthdate(),
        account.getCreatedAt(),
        totals.getOrders(),
        totals.getDelivered(),
        totals.getDeliveredAmount(),
        segment);
  }

  /**
   * Lịch sử đơn hàng của một khách, mới nhất trước (US-18).
   *
   * @param accountId mã tài khoản
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, từ 1 đến {@link OrderService#MAX_PAGE_SIZE}
   * @return một trang đơn hàng
   * @throws NoSuchElementException nếu không có khách hàng này
   * @throws InvalidRequestException nếu tham số trang sai
   */
  @Transactional(readOnly = true)
  public PageResponse<OrderSummaryResponse> orders(long accountId, int page, int size) {
    checkPage(page, size);
    findCustomer(accountId);
    return PageResponse.from(
        orderRepository.findByAccountIdOrderByCreatedAtDescIdDesc(
            accountId, PageRequest.of(page, size)),
        OrderSummaryResponse::from);
  }

  /**
   * Kết quả phân nhóm khách hàng (US-19): lần huấn luyện gần nhất, các nhóm kèm số khách, R, F, M
   * trung bình hiện tại và doanh thu (US-25), và số khách chưa được phân nhóm (BR-24).
   *
   * @return tổng quan phân nhóm; chưa huấn luyện lần nào thì model null và segments rỗng
   */
  @Transactional(readOnly = true)
  public SegmentOverviewResponse segments() {
    Map<Long, SegmentStats> stats =
        accountSegmentRepository.statsBySegment().stream()
            .collect(Collectors.toMap(SegmentStats::getSegmentId, Function.identity()));
    long segmented = stats.values().stream().mapToLong(SegmentStats::getCustomers).sum();
    Map<Long, Long> revenue = segmentProfileRepository.revenueBySegment();
    long totalRevenue = revenue.values().stream().mapToLong(Long::longValue).sum();
    List<SegmentOverviewResponse.SegmentStats> segments =
        customerSegmentRepository.findAll().stream()
            .sorted(Comparator.comparingInt(CustomerAdminService::displayOrder))
            .map(
                segment ->
                    segmentStats(
                        segment,
                        stats.get(segment.getId()),
                        segmented,
                        revenue.getOrDefault(segment.getId(), 0L),
                        totalRevenue))
            .toList();
    SegmentOverviewResponse.ModelInfo model =
        segmentModelRepository
            .findFirstByOrderByIdDesc()
            .map(
                m ->
                    new SegmentOverviewResponse.ModelInfo(
                        m.getClusterCount(),
                        m.getElbowClusterCount(),
                        m.getCustomerCount(),
                        m.getSilhouette(),
                        m.getTrainedAt()))
            .orElse(null);
    long customers = accountRepository.countByRole(Role.CUSTOMER);
    return new SegmentOverviewResponse(
        model, segments, segmented, Math.max(0, customers - segmented));
  }

  private Account findCustomer(long accountId) {
    return accountRepository
        .findById(accountId)
        .filter(a -> a.getRole() == Role.CUSTOMER)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy khách hàng"));
  }

  private static void checkPage(int page, int size) {
    if (page < 0 || size < 1 || size > OrderService.MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Trang bắt đầu từ 0, mỗi trang từ 1 đến %d khách".formatted(OrderService.MAX_PAGE_SIZE));
    }
  }

  private static int displayOrder(CustomerSegment segment) {
    int index = SEGMENT_ORDER.indexOf(segment.getCode());
    return index < 0 ? SEGMENT_ORDER.size() : index;
  }

  private static CustomerDetailResponse.SegmentInfo segmentInfo(AccountSegment row) {
    CustomerSegment segment = row.getSegment();
    return new CustomerDetailResponse.SegmentInfo(
        segment.getCode(),
        segment.getName(),
        segment.getDescription(),
        row.getRfmRecency(),
        row.getRfmFrequency(),
        row.getRfmMonetary(),
        row.getComputedAt());
  }

  private static SegmentOverviewResponse.SegmentStats segmentStats(
      CustomerSegment segment,
      SegmentStats stats,
      long segmented,
      long revenue,
      long totalRevenue) {
    long count = stats == null ? 0 : stats.getCustomers();
    double share = percent(count, segmented);
    return new SegmentOverviewResponse.SegmentStats(
        segment.getCode(),
        segment.getName(),
        segment.getDescription(),
        count,
        share,
        stats == null ? 0 : round1(stats.getRecency()),
        stats == null ? 0 : round1(stats.getFrequency()),
        stats == null ? 0 : Math.round(stats.getMonetary()),
        revenue,
        percent(revenue, totalRevenue),
        SegmentPlaybook.goalOf(segment.getCode()));
  }

  private static double percent(long part, long total) {
    return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
  }

  private static double round1(Double value) {
    return value == null ? 0 : Math.round(value * 10) / 10.0;
  }
}
