package com.manhdn.fashionshop.service;

import static com.manhdn.fashionshop.repository.ProductSpecifications.forSale;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inStock;

import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.RecentActivityResponse;
import com.manhdn.fashionshop.dto.RecommendationSource;
import com.manhdn.fashionshop.dto.RecommendationsResponse;
import com.manhdn.fashionshop.dto.RelatedProductResponse;
import com.manhdn.fashionshop.entity.Recommendation;
import com.manhdn.fashionshop.repository.CartItemRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ProductViewLogRepository;
import com.manhdn.fashionshop.repository.RecommendationRepository;
import com.manhdn.fashionshop.repository.RecommendationRepository.LatestActivity;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gợi ý cá nhân hóa ở trang chủ (US-13; BR-19, BR-20).
 *
 * <p>Danh sách được ml-service tính sẵn và ghi vào bảng recommendation mỗi khi tài khoản có hành
 * động mới; danh sách chính chỉ đọc bảng này (BR-20). Sản phẩm đã ngừng bán hoặc hết hàng từ lúc
 * tính bị bỏ qua. Tài khoản chưa có danh sách (chưa xem, chưa mua, chưa làm khảo sát) nhận sản phẩm
 * bán chạy thay thế.
 *
 * <p>Để khách thấy ngay tác động của việc vừa làm, khối có thêm hàng "Vì bạn vừa xem / vừa thêm vào
 * giỏ": sản phẩm liên quan của lần xem hoặc thêm giỏ gần nhất trong {@link #RECENT_WINDOW_HOURS}
 * giờ, tính lúc mở trang như khối sản phẩm liên quan (US-12). Sản phẩm đã có ở hàng này không lặp
 * lại ở danh sách chính.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

  /** Số sản phẩm của khối "Gợi ý cho bạn" ở trang chủ. */
  public static final int HOME_LIMIT = 8;

  /** Danh sách theo dữ liệu hành vi (đã đạt ngưỡng BR-19). */
  public static final String SOURCE_BEHAVIOR = RecommendationSource.BEHAVIOR;

  /** Danh sách theo khảo sát lần đầu (chưa đạt ngưỡng BR-19). */
  public static final String SOURCE_SURVEY = RecommendationSource.SURVEY;

  /** Chưa có danh sách riêng: sản phẩm bán chạy. */
  public static final String SOURCE_POPULAR = RecommendationSource.POPULAR;

  /** Hàng "Vì bạn vừa xem / vừa thêm vào giỏ" chỉ xét hành động trong chừng này giờ. */
  public static final int RECENT_WINDOW_HOURS = 24;

  private final RecommendationRepository recommendationRepository;
  private final ProductRepository productRepository;
  private final ProductViewLogRepository viewLogRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductCatalogService catalogService;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public RecommendationService(
      RecommendationRepository recommendationRepository,
      ProductRepository productRepository,
      ProductViewLogRepository viewLogRepository,
      CartItemRepository cartItemRepository,
      ProductCatalogService catalogService,
      Clock clock) {
    this.recommendationRepository = recommendationRepository;
    this.productRepository = productRepository;
    this.cartItemRepository = cartItemRepository;
    this.catalogService = catalogService;
    this.viewLogRepository = viewLogRepository;
    this.clock = clock;
  }

  /**
   * Khối "Gợi ý cho bạn" của một khách hàng: tối đa {@link #HOME_LIMIT} sản phẩm đang bán, còn
   * hàng, theo thứ hạng ml-service đã tính, mỗi sản phẩm kèm lý do. Nguồn là BEHAVIOR khi tài khoản
   * đạt ngưỡng BR-19 (có đơn đã giao hoặc đã xem ít nhất 5 sản phẩm trong 30 ngày), SURVEY khi chưa
   * đạt (danh sách tính từ khảo sát). Không có danh sách hoặc mọi sản phẩm trong đó đã hết hàng thì
   * trả sản phẩm bán chạy với nguồn POPULAR.
   *
   * <p>Kèm hàng "Vì bạn vừa xem / vừa thêm vào giỏ" (null nếu 24 giờ qua không xem, không thêm giỏ
   * sản phẩm nào còn bán), thời điểm tính danh sách và cờ {@code updating} cho biết ml-service còn
   * đang tính lại (frontend tải lại sau vài giây để khách thấy danh sách mới).
   *
   * @param accountId mã tài khoản khách hàng
   * @return nguồn, danh sách sản phẩm kèm lý do, hàng hành động gần nhất và trạng thái cập nhật
   */
  public RecommendationsResponse forAccount(long accountId) {
    RecentActivityResponse recent = recentActivity(accountId);
    Set<Long> inRecent =
        recent == null
            ? Set.of()
            : recent.products().stream().map(p -> p.product().id()).collect(Collectors.toSet());
    boolean updating = recommendationRepository.countPendingRecompute(accountId) > 0;

    List<Recommendation> rows = recommendationRepository.findForAccount(accountId);
    Map<Long, ProductCardResponse> cards =
        AvailableProducts.byId(
            productRepository, rows.stream().map(r -> r.getProduct().getId()).toList());
    List<Recommendation> shown = new ArrayList<>();
    for (Recommendation row : rows) {
      Long productId = row.getProduct().getId();
      if (cards.containsKey(productId)
          && !inRecent.contains(productId)
          && shown.size() < HOME_LIMIT) {
        shown.add(row);
      }
    }
    if (shown.isEmpty()) {
      List<RelatedProductResponse> popular =
          bestSellers().stream().filter(p -> !inRecent.contains(p.product().id())).toList();
      return new RecommendationsResponse(SOURCE_POPULAR, popular, recent, null, updating);
    }
    String source = hasEnoughBehaviour(accountId) ? SOURCE_BEHAVIOR : SOURCE_SURVEY;
    List<RelatedProductResponse> products =
        shown.stream()
            .map(
                row ->
                    new RelatedProductResponse(
                        cards.get(row.getProduct().getId()), row.getReason(), source))
            .toList();
    long secondsAgo =
        Math.max(
            0,
            Duration.between(shown.get(0).getGeneratedAt(), LocalDateTime.now(clock)).toSeconds());
    return new RecommendationsResponse(source, products, recent, secondsAgo, updating);
  }

  /**
   * Sản phẩm liên quan của lần xem hoặc thêm giỏ gần nhất trong {@link #RECENT_WINDOW_HOURS} giờ,
   * bỏ sản phẩm đang có trong giỏ và phần lấp bằng bán chạy (không liên quan tới hành động đó);
   * null nếu không có hành động nào, sản phẩm đó đã ngừng bán hoặc không còn sản phẩm liên quan.
   */
  private RecentActivityResponse recentActivity(long accountId) {
    LocalDateTime since = LocalDateTime.now(clock).minusHours(RECENT_WINDOW_HOURS);
    Optional<LatestActivity> latest = recommendationRepository.findLatestActivity(accountId, since);
    if (latest.isEmpty()) {
      return null;
    }
    Long productId = latest.get().getProductId();
    List<RelatedProductResponse> related;
    try {
      related = catalogService.related(productId);
    } catch (NoSuchElementException e) {
      return null;
    }
    Set<Long> inCart = new HashSet<>();
    cartItemRepository
        .findCartOf(accountId)
        .forEach(c -> inCart.add(c.getVariant().getProduct().getId()));
    List<RelatedProductResponse> products =
        related.stream()
            .filter(p -> !RecommendationSource.POPULAR.equals(p.source()))
            .filter(p -> !inCart.contains(p.product().id()))
            .toList();
    if (products.isEmpty()) {
      return null;
    }
    ProductCardResponse anchor = catalogService.getCard(productId);
    return new RecentActivityResponse(anchor, latest.get().getKind(), products);
  }

  /** Ngưỡng "đủ dữ liệu hành vi" của BR-19, cùng cách đếm với ProductViewService và ml-service. */
  private boolean hasEnoughBehaviour(long accountId) {
    if (viewLogRepository.countDeliveredOrders(accountId) > 0) {
      return true;
    }
    LocalDateTime since = LocalDateTime.now(clock).minusDays(ProductViewService.VIEW_WINDOW_DAYS);
    return viewLogRepository.countDistinctProductsSince(accountId, since)
        >= ProductViewService.VIEW_THRESHOLD;
  }

  private List<RelatedProductResponse> bestSellers() {
    return productRepository
        .findBy(
            forSale().and(inStock()),
            q -> q.sortBy(ProductCatalogService.POPULAR_ORDER).limit(HOME_LIMIT).all())
        .stream()
        .map(
            p ->
                new RelatedProductResponse(
                    ProductCardResponse.from(p),
                    ProductCatalogService.BEST_SELLER,
                    RecommendationSource.POPULAR))
        .toList();
  }
}
