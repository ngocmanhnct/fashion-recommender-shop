package com.manhdn.fashionshop.service;

import static com.manhdn.fashionshop.repository.ProductSpecifications.forSale;
import static com.manhdn.fashionshop.repository.ProductSpecifications.inStock;

import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.RecommendationsResponse;
import com.manhdn.fashionshop.dto.RelatedProductResponse;
import com.manhdn.fashionshop.entity.Recommendation;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.ProductViewLogRepository;
import com.manhdn.fashionshop.repository.RecommendationRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gợi ý cá nhân hóa ở trang chủ (US-13; BR-19, BR-20).
 *
 * <p>Danh sách được ml-service tính sẵn và ghi vào bảng recommendation mỗi khi tài khoản có hành
 * động mới; mở trang chủ chỉ đọc bảng này, không gọi ml-service (BR-20). Sản phẩm đã ngừng bán hoặc
 * hết hàng từ lúc tính bị bỏ qua. Tài khoản chưa có danh sách (chưa xem, chưa mua, chưa làm khảo
 * sát) nhận sản phẩm bán chạy thay thế.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

  /** Số sản phẩm của khối "Gợi ý cho bạn" ở trang chủ. */
  public static final int HOME_LIMIT = 8;

  /** Danh sách theo dữ liệu hành vi (đã đạt ngưỡng BR-19). */
  public static final String SOURCE_BEHAVIOR = "BEHAVIOR";

  /** Danh sách theo khảo sát lần đầu (chưa đạt ngưỡng BR-19). */
  public static final String SOURCE_SURVEY = "SURVEY";

  /** Chưa có danh sách riêng: sản phẩm bán chạy. */
  public static final String SOURCE_POPULAR = "POPULAR";

  private final RecommendationRepository recommendationRepository;
  private final ProductRepository productRepository;
  private final ProductViewLogRepository viewLogRepository;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public RecommendationService(
      RecommendationRepository recommendationRepository,
      ProductRepository productRepository,
      ProductViewLogRepository viewLogRepository,
      Clock clock) {
    this.recommendationRepository = recommendationRepository;
    this.productRepository = productRepository;
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
   * @param accountId mã tài khoản khách hàng
   * @return nguồn và danh sách sản phẩm kèm lý do
   */
  public RecommendationsResponse forAccount(long accountId) {
    List<Recommendation> rows = recommendationRepository.findForAccount(accountId);
    Map<Long, ProductCardResponse> cards =
        AvailableProducts.byId(
            productRepository, rows.stream().map(r -> r.getProduct().getId()).toList());
    List<RelatedProductResponse> products = new ArrayList<>();
    for (Recommendation row : rows) {
      ProductCardResponse card = cards.get(row.getProduct().getId());
      if (card != null && products.size() < HOME_LIMIT) {
        products.add(new RelatedProductResponse(card, row.getReason()));
      }
    }
    if (products.isEmpty()) {
      return new RecommendationsResponse(SOURCE_POPULAR, bestSellers());
    }
    return new RecommendationsResponse(
        hasEnoughBehaviour(accountId) ? SOURCE_BEHAVIOR : SOURCE_SURVEY, products);
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
                    ProductCardResponse.from(p), ProductCatalogService.BEST_SELLER))
        .toList();
  }
}
