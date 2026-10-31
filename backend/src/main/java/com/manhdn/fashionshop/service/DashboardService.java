package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AbTestResultResponse;
import com.manhdn.fashionshop.dto.DashboardResponse;
import com.manhdn.fashionshop.dto.DashboardResponse.AbTestSummary;
import com.manhdn.fashionshop.dto.DashboardResponse.Day;
import com.manhdn.fashionshop.dto.DashboardResponse.Recommendations;
import com.manhdn.fashionshop.dto.DashboardResponse.StatusCount;
import com.manhdn.fashionshop.dto.DashboardResponse.Summary;
import com.manhdn.fashionshop.dto.DashboardResponse.TopProduct;
import com.manhdn.fashionshop.dto.RecommendationCtrResponse.Row;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.ProductEventType;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AbExperimentRepository;
import com.manhdn.fashionshop.repository.DashboardRepository;
import com.manhdn.fashionshop.repository.DashboardRepository.DayRow;
import com.manhdn.fashionshop.repository.ProductEventLogRepository;
import com.manhdn.fashionshop.repository.ProductEventLogRepository.EventCount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Trang tổng quan của Quản trị viên (US-28): doanh thu theo ngày, số đơn theo trạng thái, sản phẩm
 * bán chạy và hiệu quả gợi ý trong một khoảng ngày. Ngày tính theo giờ Việt Nam, kể cả khi máy chủ
 * chạy giờ UTC (Docker).
 */
@Service
public class DashboardService {

  /** Số ngày mặc định của khoảng (tính cả hôm nay). */
  public static final int DEFAULT_DAYS = 30;

  /** Khoảng dài nhất được xem một lần. */
  public static final int MAX_DAYS = 366;

  /** Số sản phẩm bán chạy hiển thị. */
  public static final int TOP_PRODUCTS = 10;

  /** Lượt bấm gợi ý trong chừng này ngày trước lúc đặt thì tiền hàng được tính là từ gợi ý. */
  public static final int RECOMMENDATION_WINDOW_DAYS = 7;

  private static final ZoneId VIETNAM = VoucherService.ZONE;
  private static final List<ProductEventType> RECOMMENDATION_EVENTS =
      List.of(ProductEventType.RECOMMENDATION_SHOWN, ProductEventType.RECOMMENDATION_CLICKED);

  private final DashboardRepository dashboardRepository;
  private final ProductEventLogRepository eventLogRepository;
  private final AbExperimentRepository experimentRepository;
  private final AbTestService abTestService;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public DashboardService(
      DashboardRepository dashboardRepository,
      ProductEventLogRepository eventLogRepository,
      AbExperimentRepository experimentRepository,
      AbTestService abTestService,
      Clock clock) {
    this.dashboardRepository = dashboardRepository;
    this.eventLogRepository = eventLogRepository;
    this.experimentRepository = experimentRepository;
    this.abTestService = abTestService;
    this.clock = clock;
  }

  /**
   * Số liệu tổng quan từ đầu ngày {@code from} đến hết ngày {@code to} (giờ Việt Nam). Bỏ trống thì
   * lấy {@value #DEFAULT_DAYS} ngày gần nhất tính đến hôm nay.
   *
   * <p>Doanh thu chỉ tính đơn Đã giao, theo ngày đặt đơn (BR-47). Số đơn theo trạng thái tính mọi
   * đơn đặt trong khoảng. Tiền hàng từ gợi ý là thành tiền các dòng đơn Đã giao mà khách đã bấm sản
   * phẩm đó ở một khối gợi ý trong {@value #RECOMMENDATION_WINDOW_DAYS} ngày trước lúc đặt.
   *
   * @param from ngày đầu, null = {@code to} lùi {@value #DEFAULT_DAYS} - 1 ngày
   * @param to ngày cuối, null = hôm nay
   * @return số tổng, từng ngày, đơn theo trạng thái, bán chạy, hiệu quả gợi ý và thử nghiệm A/B
   * @throws InvalidRequestException nếu ngày đầu sau ngày cuối hoặc khoảng dài hơn {@value
   *     #MAX_DAYS} ngày
   */
  @Transactional(readOnly = true)
  public DashboardResponse overview(LocalDate from, LocalDate to) {
    LocalDate end = to == null ? LocalDate.now(clock.withZone(VIETNAM)) : to;
    LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
    if (start.isAfter(end)) {
      throw new InvalidRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
    }
    if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
      throw new InvalidRequestException("Chỉ xem được tối đa " + MAX_DAYS + " ngày mỗi lần");
    }
    LocalDateTime startAt = storedTime(start);
    LocalDateTime endAt = storedTime(end.plusDays(1));

    List<DayRow> rows = dashboardRepository.daily(startAt, endAt, shiftSeconds(startAt));
    Map<LocalDate, DayRow> byDay =
        rows.stream().collect(Collectors.toMap(DayRow::day, Function.identity()));
    List<Day> days = new ArrayList<>();
    for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
      DayRow row = byDay.get(d);
      days.add(
          row == null
              ? new Day(d, 0, 0, 0)
              : new Day(d, row.revenue(), row.delivered(), row.orders()));
    }
    long revenue = rows.stream().mapToLong(DayRow::revenue).sum();
    long delivered = rows.stream().mapToLong(DayRow::delivered).sum();
    long orders = rows.stream().mapToLong(DayRow::orders).sum();
    long goods = rows.stream().mapToLong(DayRow::goods).sum();
    long recommended =
        dashboardRepository.recommendedRevenue(startAt, endAt, RECOMMENDATION_WINDOW_DAYS);
    Summary summary =
        new Summary(
            revenue,
            delivered,
            orders,
            delivered == 0 ? null : revenue / delivered,
            dashboardRepository.newCustomers(startAt, endAt),
            goods,
            recommended,
            goods == 0 ? null : Math.round(recommended * 10000.0 / goods) / 100.0);

    Map<String, Long> statusCounts = dashboardRepository.ordersByStatus(startAt, endAt);
    List<StatusCount> byStatus = new ArrayList<>();
    for (OrderStatus status : OrderStatus.values()) {
      byStatus.add(new StatusCount(status.name(), statusCounts.getOrDefault(status.name(), 0L)));
    }
    List<TopProduct> top =
        dashboardRepository.topProducts(startAt, endAt, TOP_PRODUCTS).stream()
            .map(r -> new TopProduct(r.productId(), r.name(), r.quantity(), r.revenue()))
            .toList();

    return new DashboardResponse(
        start, end, summary, days, byStatus, top, recommendations(startAt, endAt), runningAbTest());
  }

  /** Lượt hiện, lượt bấm gợi ý trong khoảng: tổng, theo vị trí và theo nguồn. */
  private Recommendations recommendations(LocalDateTime startAt, LocalDateTime endAt) {
    Map<String, long[]> byPlacement = new TreeMap<>();
    Map<String, long[]> bySource = new TreeMap<>();
    long[] total = new long[2];
    for (EventCount c : eventLogRepository.countByGroup(RECOMMENDATION_EVENTS, startAt, endAt)) {
      int column = c.getEventType() == ProductEventType.RECOMMENDATION_SHOWN ? 0 : 1;
      byPlacement.computeIfAbsent(String.valueOf(c.getPlacement()), k -> new long[2])[column] +=
          c.getTotal();
      bySource.computeIfAbsent(String.valueOf(c.getSource()), k -> new long[2])[column] +=
          c.getTotal();
      total[column] += c.getTotal();
    }
    return new Recommendations(
        Row.of(null, null, total[0], total[1]),
        byPlacement.entrySet().stream()
            .map(e -> Row.of(e.getKey(), null, e.getValue()[0], e.getValue()[1]))
            .toList(),
        bySource.entrySet().stream()
            .map(e -> Row.of(null, e.getKey(), e.getValue()[0], e.getValue()[1]))
            .toList());
  }

  /** Tóm tắt thử nghiệm A/B đang chạy, null khi không có. */
  private AbTestSummary runningAbTest() {
    return experimentRepository
        .findFirstByEndedAtIsNullOrderByIdDesc()
        .map(
            experiment -> {
              AbTestResultResponse result = abTestService.result(experiment.getId());
              return new AbTestSummary(
                  experiment.getId(),
                  experiment.getName(),
                  experiment.getStartedAt(),
                  result.treatment().users(),
                  result.treatment().clickRate(),
                  result.control().users(),
                  result.control().clickRate());
            })
        .orElse(null);
  }

  /** Đầu ngày {@code date} theo giờ Việt Nam, đổi sang giờ lưu trong CSDL (giờ JVM). */
  private LocalDateTime storedTime(LocalDate date) {
    return date.atStartOfDay(VIETNAM).withZoneSameInstant(clock.getZone()).toLocalDateTime();
  }

  /** Số giây cộng vào giờ lưu trong CSDL để ra giờ Việt Nam (0 khi máy chủ chạy giờ Việt Nam). */
  private long shiftSeconds(LocalDateTime storedTime) {
    Instant instant = storedTime.atZone(clock.getZone()).toInstant();
    return (long) VIETNAM.getRules().getOffset(instant).getTotalSeconds()
        - clock.getZone().getRules().getOffset(instant).getTotalSeconds();
  }
}
