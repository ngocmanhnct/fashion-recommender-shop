package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CartItemRequest;
import com.manhdn.fashionshop.dto.CartItemResponse;
import com.manhdn.fashionshop.dto.CartNotice;
import com.manhdn.fashionshop.dto.CartResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.CartItem;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.ProductEventLog;
import com.manhdn.fashionshop.entity.ProductEventType;
import com.manhdn.fashionshop.entity.ProductVariant;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.CartItemRepository;
import com.manhdn.fashionshop.repository.ProductEventLogRepository;
import com.manhdn.fashionshop.repository.ProductVariantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giỏ hàng (US-06, US-07, BR-06, BR-07, BR-11).
 *
 * <ul>
 *   <li>Giỏ hàng của Khách hàng đã đăng nhập lưu ở bảng cart_item; giỏ của khách chưa đăng nhập lưu
 *       ở trình duyệt, backend chỉ xem trước giá và tồn kho ({@link #preview}) và gộp vào giỏ của
 *       tài khoản sau khi đăng nhập ({@link #merge}).
 *   <li>Tồn kho chỉ được kiểm tra để tham khảo, không giữ chỗ (BR-08): thêm hoặc sửa vượt tồn kho
 *       hiện tại bị từ chối (409), nhưng tồn kho chỉ bị trừ khi xác nhận đơn hàng.
 *   <li>Mỗi lần đọc giỏ hàng, dòng của sản phẩm đã ngừng bán hoặc biến thể đã hết hàng bị loại,
 *       dòng nhiều hơn tồn kho bị giảm số lượng, kèm thông báo (BR-11).
 *   <li>Mọi thay đổi giỏ hàng là hành động rõ ý: yêu cầu tính lại gợi ý sau 3 giây (BR-20). Thêm
 *       vào giỏ được ghi vào product_event_log (ADD_TO_CART).
 *   <li>Thao tác ghi chạy qua {@link TransactionRetry} và khóa dòng tài khoản trước, nên các thao
 *       tác đồng thời trên cùng một giỏ (vd 2 tab) chạy lần lượt.
 * </ul>
 */
@Service
public class CartService {

  /** Số lượng tối đa của một dòng giỏ hàng (một biến thể). */
  public static final int MAX_QUANTITY = 99;

  /** Số dòng (biến thể khác nhau) tối đa trong một giỏ hàng. */
  public static final int MAX_LINES = 50;

  private final TransactionRetry transactionRetry;
  private final AccountRepository accountRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductVariantRepository variantRepository;
  private final ProductEventLogRepository eventLogRepository;
  private final RecomputeRequestService recomputeRequestService;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public CartService(
      TransactionRetry transactionRetry,
      AccountRepository accountRepository,
      CartItemRepository cartItemRepository,
      ProductVariantRepository variantRepository,
      ProductEventLogRepository eventLogRepository,
      RecomputeRequestService recomputeRequestService,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.accountRepository = accountRepository;
    this.cartItemRepository = cartItemRepository;
    this.variantRepository = variantRepository;
    this.eventLogRepository = eventLogRepository;
    this.recomputeRequestService = recomputeRequestService;
    this.clock = clock;
  }

  /**
   * Giỏ hàng của tài khoản theo giá và tồn kho hiện tại. Dòng không còn mua được bị loại, dòng
   * nhiều hơn tồn kho bị giảm số lượng; các thay đổi này nằm trong {@code notices} (BR-11).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @return giỏ hàng sau khi đã tự sửa
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  public CartResponse getCart(long accountId) {
    return transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          return currentCart(accountId, new ArrayList<>());
        });
  }

  /**
   * Thêm một biến thể vào giỏ hàng (US-06). Biến thể đã có trong giỏ thì cộng dồn số lượng.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param request biến thể và số lượng cần thêm
   * @return giỏ hàng sau khi thêm
   * @throws NoSuchElementException nếu tài khoản hoặc biến thể không có, hay sản phẩm đã ngừng bán
   * @throws ConflictException nếu biến thể hết hàng, tổng số lượng vượt tồn kho hiện tại hoặc vượt
   *     {@link #MAX_QUANTITY}, hay giỏ đã đủ {@link #MAX_LINES} dòng
   */
  public CartResponse addItem(long accountId, CartItemRequest request) {
    return transactionRetry.execute(() -> add(accountId, request.variantId(), request.quantity()));
  }

  /**
   * Đổi số lượng của một dòng giỏ hàng (US-07). Nếu sản phẩm vừa ngừng bán hoặc biến thể vừa hết
   * hàng thì dòng bị loại thay vì sửa, kèm thông báo.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param variantId mã biến thể của dòng cần sửa
   * @param quantity số lượng mới, từ 1 đến {@link #MAX_QUANTITY}
   * @return giỏ hàng sau khi sửa
   * @throws NoSuchElementException nếu tài khoản không có hoặc biến thể không có trong giỏ
   * @throws ConflictException nếu số lượng mới vượt tồn kho hiện tại
   */
  public CartResponse updateQuantity(long accountId, long variantId, int quantity) {
    return transactionRetry.execute(() -> update(accountId, variantId, quantity));
  }

  /**
   * Xóa một dòng khỏi giỏ hàng (US-07). Dòng không có trong giỏ thì không làm gì.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param variantId mã biến thể của dòng cần xóa
   * @return giỏ hàng sau khi xóa
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  public CartResponse removeItem(long accountId, long variantId) {
    return transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          List<CartItem> items = cartItemRepository.findCartOf(accountId);
          items.stream()
              .filter(item -> item.getVariant().getId() == variantId)
              .findFirst()
              .ifPresent(
                  item -> {
                    cartItemRepository.delete(item);
                    requestRecompute(accountId);
                  });
          return currentCart(accountId, new ArrayList<>());
        });
  }

  /**
   * Gộp giỏ hàng của trình duyệt vào giỏ của tài khoản ngay sau khi đăng nhập hoặc đăng ký (BR-07):
   * biến thể đã có thì cộng dồn số lượng nhưng không vượt tồn kho hiện tại, biến thể chưa có thì
   * thêm mới. Dòng không còn mua được bị bỏ qua; mọi dòng không gộp đủ đều có thông báo.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param lines các dòng giỏ hàng của trình duyệt
   * @return giỏ hàng của tài khoản sau khi gộp
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  public CartResponse merge(long accountId, List<CartItemRequest> lines) {
    return transactionRetry.execute(() -> mergeLines(accountId, lines));
  }

  /**
   * Xem trước giỏ hàng của khách chưa đăng nhập (BR-06) theo giá và tồn kho hiện tại, không ghi gì
   * vào CSDL. Các dòng trùng biến thể được cộng lại; dòng không còn mua được bị bỏ, dòng nhiều hơn
   * tồn kho bị giảm số lượng, kèm thông báo. Trình duyệt lưu lại đúng các dòng trả về.
   *
   * @param lines các dòng giỏ hàng của trình duyệt
   * @return giỏ hàng đã kiểm tra
   */
  @Transactional(readOnly = true)
  public CartResponse preview(List<CartItemRequest> lines) {
    Map<Long, Integer> wanted = sumByVariant(lines);
    Map<Long, ProductVariant> variants = variantsById(wanted.keySet());
    List<CartItemResponse> items = new ArrayList<>();
    List<CartNotice> notices = new ArrayList<>();
    for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
      ProductVariant variant = variants.get(line.getKey());
      CartNotice unavailable = unavailableNotice(line.getKey(), variant);
      if (unavailable != null) {
        notices.add(unavailable);
        continue;
      }
      int quantity = Math.min(line.getValue(), maxQuantityOf(variant));
      if (quantity < line.getValue()) {
        notices.add(reducedNotice(variant, line.getValue(), quantity));
      }
      items.add(toResponse(variant, quantity));
    }
    return toCart(items, notices);
  }

  private CartResponse add(long accountId, long variantId, int quantity) {
    // Khóa tài khoản trước mọi truy vấn khác (thứ tự khóa cố định).
    final Account account = lockAccount(accountId);
    ProductVariant variant =
        variantRepository
            .findById(variantId)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy phân loại sản phẩm"));
    Product product = variant.getProduct();
    if (product.isDeleted()) {
      throw new NoSuchElementException("Sản phẩm đã ngừng bán");
    }
    int stock = variant.getStock();
    if (stock == 0) {
      throw new ConflictException("Phân loại này đã hết hàng");
    }
    List<CartItem> items = cartItemRepository.findCartOf(accountId);
    CartItem existing = findLine(items, variantId);
    int inCart = existing == null ? 0 : existing.getQuantity();
    int total = inCart + quantity;
    if (total > MAX_QUANTITY) {
      throw new ConflictException(
          "Mỗi phân loại mua tối đa %d sản phẩm, giỏ hàng đã có %d"
              .formatted(MAX_QUANTITY, inCart));
    }
    if (total > stock) {
      throw new ConflictException(
          inCart == 0
              ? "Chỉ còn %d sản phẩm".formatted(stock)
              : "Chỉ còn %d sản phẩm, giỏ hàng đã có %d".formatted(stock, inCart));
    }
    LocalDateTime now = LocalDateTime.now(clock);
    if (existing != null) {
      existing.changeQuantity(total, now);
    } else {
      if (items.size() >= MAX_LINES) {
        throw new ConflictException(
            "Giỏ hàng tối đa %d sản phẩm khác nhau, hãy xóa bớt trước khi thêm"
                .formatted(MAX_LINES));
      }
      cartItemRepository.save(new CartItem(account, variant, quantity, now));
    }
    eventLogRepository.save(
        new ProductEventLog(account, product, ProductEventType.ADD_TO_CART, now));
    requestRecompute(accountId);
    return currentCart(accountId, new ArrayList<>());
  }

  private CartResponse update(long accountId, long variantId, int quantity) {
    lockAccount(accountId);
    CartItem item = findLine(cartItemRepository.findCartOf(accountId), variantId);
    if (item == null) {
      throw new NoSuchElementException("Sản phẩm không có trong giỏ hàng");
    }
    ProductVariant variant = item.getVariant();
    // Ngừng bán hoặc hết hàng: để bước đọc giỏ loại dòng này kèm thông báo (BR-11).
    if (!variant.getProduct().isDeleted() && variant.getStock() > 0) {
      if (quantity > variant.getStock()) {
        throw new ConflictException("Chỉ còn %d sản phẩm".formatted(variant.getStock()));
      }
      if (quantity != item.getQuantity()) {
        item.changeQuantity(quantity, LocalDateTime.now(clock));
        requestRecompute(accountId);
      }
    }
    return currentCart(accountId, new ArrayList<>());
  }

  private CartResponse mergeLines(long accountId, List<CartItemRequest> lines) {
    Account account = lockAccount(accountId);
    Map<Long, Integer> wanted = sumByVariant(lines);
    Map<Long, ProductVariant> variants = variantsById(wanted.keySet());
    List<CartItem> items = cartItemRepository.findCartOf(accountId);
    int lineCount = items.size();
    List<CartNotice> notices = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now(clock);
    boolean changed = false;
    for (Map.Entry<Long, Integer> line : wanted.entrySet()) {
      ProductVariant variant = variants.get(line.getKey());
      CartNotice unavailable = unavailableNotice(line.getKey(), variant);
      if (unavailable != null) {
        notices.add(unavailable);
        continue;
      }
      CartItem existing = findLine(items, variant.getId());
      int inCart = existing == null ? 0 : existing.getQuantity();
      int wantedTotal = inCart + line.getValue();
      int total = Math.min(wantedTotal, maxQuantityOf(variant));
      if (total < wantedTotal) {
        notices.add(reducedNotice(variant, wantedTotal, total));
      }
      if (existing != null) {
        if (total != inCart) {
          existing.changeQuantity(total, now);
          changed = true;
        }
      } else if (lineCount >= MAX_LINES) {
        notices.add(
            new CartNotice(
                variant.getId(),
                variant.getProduct().getName(),
                variant.label(),
                CartNotice.Type.NOT_MERGED_CART_FULL,
                "Giỏ hàng đã đủ %d sản phẩm khác nhau nên chưa thêm được sản phẩm này"
                    .formatted(MAX_LINES)));
      } else {
        cartItemRepository.save(new CartItem(account, variant, total, now));
        lineCount++;
        changed = true;
      }
    }
    if (changed) {
      requestRecompute(accountId);
    }
    return currentCart(accountId, notices);
  }

  /**
   * Đọc giỏ hàng của tài khoản, tự loại dòng không còn mua được và giảm dòng nhiều hơn tồn kho
   * (BR-11), thêm thông báo vào {@code notices}. Phải gọi sau khi đã khóa dòng tài khoản.
   */
  private CartResponse currentCart(long accountId, List<CartNotice> notices) {
    List<CartItemResponse> items = new ArrayList<>();
    boolean changed = false;
    LocalDateTime now = LocalDateTime.now(clock);
    for (CartItem item : cartItemRepository.findCartOf(accountId)) {
      ProductVariant variant = item.getVariant();
      CartNotice unavailable = unavailableNotice(variant.getId(), variant);
      if (unavailable != null) {
        cartItemRepository.delete(item);
        notices.add(unavailable);
        changed = true;
        continue;
      }
      int max = maxQuantityOf(variant);
      if (item.getQuantity() > max) {
        notices.add(reducedNotice(variant, item.getQuantity(), max));
        item.changeQuantity(max, now);
        changed = true;
      }
      items.add(toResponse(variant, item.getQuantity()));
    }
    if (changed) {
      requestRecompute(accountId);
    }
    return toCart(items, notices);
  }

  private Account lockAccount(long accountId) {
    return accountRepository
        .findWithLockById(accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
  }

  private void requestRecompute(long accountId) {
    recomputeRequestService.request(
        accountId, RecomputeTask.RECOMMENDATION, RecomputeRequestService.STRONG_ACTION_DELAY);
  }

  private static CartItem findLine(List<CartItem> items, long variantId) {
    return items.stream()
        .filter(item -> item.getVariant().getId() == variantId)
        .findFirst()
        .orElse(null);
  }

  /** Cộng số lượng các dòng trùng biến thể, giữ thứ tự xuất hiện đầu tiên. */
  private static Map<Long, Integer> sumByVariant(List<CartItemRequest> lines) {
    Map<Long, Integer> wanted = new LinkedHashMap<>();
    for (CartItemRequest line : lines) {
      wanted.merge(line.variantId(), line.quantity(), Integer::sum);
    }
    return wanted;
  }

  private Map<Long, ProductVariant> variantsById(Iterable<Long> ids) {
    return variantRepository.findAllById(ids).stream()
        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
  }

  /** Số lượng tối đa mua được của biến thể lúc này: tồn kho, nhưng không quá MAX_QUANTITY. */
  private static int maxQuantityOf(ProductVariant variant) {
    return Math.min(variant.getStock(), MAX_QUANTITY);
  }

  /** Thông báo nếu biến thể không còn mua được (không có, ngừng bán, hết hàng), không thì null. */
  private static CartNotice unavailableNotice(long variantId, ProductVariant variant) {
    if (variant == null) {
      return new CartNotice(
          variantId,
          null,
          null,
          CartNotice.Type.REMOVED_UNAVAILABLE,
          "Một sản phẩm không còn tồn tại nên đã được xóa khỏi giỏ hàng");
    }
    if (variant.getProduct().isDeleted()) {
      return new CartNotice(
          variantId,
          variant.getProduct().getName(),
          variant.label(),
          CartNotice.Type.REMOVED_UNAVAILABLE,
          "Sản phẩm đã ngừng bán nên đã được xóa khỏi giỏ hàng");
    }
    if (variant.getStock() == 0) {
      return new CartNotice(
          variantId,
          variant.getProduct().getName(),
          variant.label(),
          CartNotice.Type.REMOVED_OUT_OF_STOCK,
          "Sản phẩm đã hết hàng nên đã được xóa khỏi giỏ hàng");
    }
    return null;
  }

  private static CartNotice reducedNotice(ProductVariant variant, int from, int to) {
    String reason =
        variant.getStock() < from && variant.getStock() <= MAX_QUANTITY
            ? "Chỉ còn %d sản phẩm".formatted(variant.getStock())
            : "Mỗi phân loại mua tối đa %d sản phẩm".formatted(MAX_QUANTITY);
    return new CartNotice(
        variant.getId(),
        variant.getProduct().getName(),
        variant.label(),
        CartNotice.Type.QUANTITY_REDUCED,
        "%s nên số lượng đã giảm từ %d xuống %d".formatted(reason, from, to));
  }

  private static CartItemResponse toResponse(ProductVariant variant, int quantity) {
    Product product = variant.getProduct();
    long price = variant.getPrice();
    return new CartItemResponse(
        variant.getId(),
        product.getId(),
        product.getName(),
        variant.label(),
        product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl(),
        product.getCategory().getName(),
        price,
        quantity,
        variant.getStock(),
        price * quantity);
  }

  private static CartResponse toCart(List<CartItemResponse> items, List<CartNotice> notices) {
    int totalQuantity = items.stream().mapToInt(CartItemResponse::quantity).sum();
    long subtotal = items.stream().mapToLong(CartItemResponse::lineTotal).sum();
    return new CartResponse(List.copyOf(items), List.copyOf(notices), totalQuantity, subtotal);
  }
}
