package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.RecommendationCtrResponse;
import com.manhdn.fashionshop.dto.RecommendationCtrResponse.Row;
import com.manhdn.fashionshop.dto.RecommendationEventRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductEventLog;
import com.manhdn.fashionshop.entity.ProductEventType;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.ProductEventLogRepository;
import com.manhdn.fashionshop.repository.ProductEventLogRepository.EventCount;
import com.manhdn.fashionshop.repository.ProductRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhật ký lượt hiện, lượt bấm của sản phẩm gợi ý (product_event_log, Mục 3.14) và tỷ lệ bấm (CTR)
 * cho Quản trị viên. Tỷ lệ bấm = số lượt bấm ÷ số lượt hiện × 100%.
 */
@Service
public class RecommendationEventService {

  /** Số ngày mặc định của khoảng thống kê (tính cả hôm nay). */
  public static final int DEFAULT_DAYS = 30;

  private static final List<ProductEventType> RECOMMENDATION_EVENTS =
      List.of(ProductEventType.RECOMMENDATION_SHOWN, ProductEventType.RECOMMENDATION_CLICKED);

  private final ProductEventLogRepository eventLogRepository;
  private final ProductRepository productRepository;
  private final AccountRepository accountRepository;
  private final Clock clock;

  /** Nhận repository và đồng hồ qua constructor. */
  public RecommendationEventService(
      ProductEventLogRepository eventLogRepository,
      ProductRepository productRepository,
      AccountRepository accountRepository,
      Clock clock) {
    this.eventLogRepository = eventLogRepository;
    this.productRepository = productRepository;
    this.accountRepository = accountRepository;
    this.clock = clock;
  }

  /**
   * Ghi một lượt hiện (cả khối gợi ý) hoặc một lượt bấm. Mỗi sản phẩm là một dòng, kèm vị trí,
   * nguồn và thứ hạng. Mã sản phẩm không tồn tại thì bỏ qua (danh sách có thể cũ hơn dữ liệu).
   *
   * @param accountId mã tài khoản khách hàng; null khi chưa đăng nhập
   * @param request loại sự kiện, vị trí và các sản phẩm
   */
  @Transactional
  public void record(Long accountId, RecommendationEventRequest request) {
    ProductEventType type =
        "SHOWN".equals(request.type())
            ? ProductEventType.RECOMMENDATION_SHOWN
            : ProductEventType.RECOMMENDATION_CLICKED;
    Map<Long, Product> products =
        productRepository
            .findAllById(
                request.items().stream()
                    .map(RecommendationEventRequest.Item::productId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    Account account = accountId == null ? null : accountRepository.getReferenceById(accountId);
    LocalDateTime now = LocalDateTime.now(clock);
    List<ProductEventLog> events = new ArrayList<>();
    for (RecommendationEventRequest.Item item : request.items()) {
      Product product = products.get(item.productId());
      if (product != null) {
        events.add(
            new ProductEventLog(
                account, product, type, request.placement(), item.source(), item.rank(), now));
      }
    }
    eventLogRepository.saveAll(events);
  }

  /**
   * Tỷ lệ bấm vào gợi ý từ ngày {@code from} đến hết ngày {@code to}, tổng và theo từng vị trí,
   * nguồn. Bỏ trống thì lấy {@value #DEFAULT_DAYS} ngày gần nhất.
   *
   * @param from ngày đầu, null = {@code to} lùi {@value #DEFAULT_DAYS} - 1 ngày
   * @param to ngày cuối, null = hôm nay
   * @return số lượt hiện, lượt bấm và tỷ lệ bấm
   * @throws InvalidRequestException nếu ngày đầu sau ngày cuối
   */
  @Transactional(readOnly = true)
  public RecommendationCtrResponse ctr(LocalDate from, LocalDate to) {
    LocalDate end = to == null ? LocalDate.now(clock) : to;
    LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
    if (start.isAfter(end)) {
      throw new InvalidRequestException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
    }
    List<EventCount> counts =
        eventLogRepository.countByGroup(
            RECOMMENDATION_EVENTS, start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    Map<String, long[]> groups = new TreeMap<>();
    long[] total = new long[2];
    for (EventCount c : counts) {
      int column = c.getEventType() == ProductEventType.RECOMMENDATION_SHOWN ? 0 : 1;
      String key = c.getPlacement() + "\u0000" + c.getSource();
      groups.computeIfAbsent(key, k -> new long[2])[column] += c.getTotal();
      total[column] += c.getTotal();
    }
    List<Row> rows = new ArrayList<>();
    groups.forEach(
        (key, value) -> {
          String[] parts = key.split("\u0000", -1);
          rows.add(Row.of(parts[0], parts[1], value[0], value[1]));
        });
    return new RecommendationCtrResponse(start, end, Row.of(null, null, total[0], total[1]), rows);
  }
}
