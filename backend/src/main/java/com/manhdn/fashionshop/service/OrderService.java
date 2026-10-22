package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.client.VnPayClient;
import com.manhdn.fashionshop.dto.CartResponse;
import com.manhdn.fashionshop.dto.CheckoutPreviewResponse;
import com.manhdn.fashionshop.dto.OrderResponse;
import com.manhdn.fashionshop.dto.OrderSummaryResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.PlaceOrderRequest;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Address;
import com.manhdn.fashionshop.entity.CartItem;
import com.manhdn.fashionshop.entity.Order;
import com.manhdn.fashionshop.entity.OrderItem;
import com.manhdn.fashionshop.entity.OrderStatus;
import com.manhdn.fashionshop.entity.PaymentMethod;
import com.manhdn.fashionshop.entity.ProductVariant;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.AddressRepository;
import com.manhdn.fashionshop.repository.CartItemRepository;
import com.manhdn.fashionshop.repository.OrderRepository;
import com.manhdn.fashionshop.repository.ProductVariantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đặt hàng, xem và hủy đơn của Khách hàng (US-10, US-11, US-20).
 *
 * <ul>
 *   <li>Đơn gồm toàn bộ giỏ hàng, giá lấy theo giá hiện tại; phí vận chuyển theo BR-35.
 *   <li>Chống bán vượt tồn kho (BR-08, BR-09, BR-14): khi xác nhận, các biến thể trong giỏ bị khóa
 *       dòng (SELECT ... FOR UPDATE, theo mã tăng dần) rồi mới kiểm tra và trừ tồn kho trong cùng
 *       giao dịch. Một dòng không đủ hàng thì từ chối cả đơn, nói rõ dòng nào còn bao nhiêu.
 *   <li>Bấm xác nhận nhiều lần với cùng mã định danh chỉ tạo 1 đơn (BR-10): lần sau trả lại đúng
 *       đơn đã tạo. Thao tác khóa dòng tài khoản trước, nên 2 yêu cầu cùng mã của một tài khoản
 *       chạy lần lượt.
 *   <li>Khách tự hủy được đơn Chờ xử lý hoặc Đang xử lý (BR-13); hủy thì hoàn tồn kho (BR-16).
 *   <li>Đặt và hủy đơn là hành động rõ ý: yêu cầu tính lại gợi ý sau 3 giây (BR-20).
 * </ul>
 *
 * <p>Hiện chỉ có thanh toán khi nhận hàng (COD); thanh toán online (VNPay) làm ở bước sau.
 */
@Service
public class OrderService {

  /** Phí vận chuyển đồng giá toàn quốc (BR-35). */
  public static final long SHIPPING_FEE = 30_000;

  /** Tổng tiền hàng từ mức này trở lên thì miễn phí vận chuyển (BR-35). */
  public static final long FREE_SHIPPING_THRESHOLD = 500_000;

  /** Số đơn mỗi trang tối đa ở danh sách đơn hàng. */
  public static final int MAX_PAGE_SIZE = 50;

  private final TransactionRetry transactionRetry;
  private final CartService cartService;
  private final AccountRepository accountRepository;
  private final AddressRepository addressRepository;
  private final CartItemRepository cartItemRepository;
  private final ProductVariantRepository variantRepository;
  private final OrderRepository orderRepository;
  private final RecomputeRequestService recomputeRequestService;
  private final RefundService refundService;
  private final VnPayClient vnPayClient;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public OrderService(
      TransactionRetry transactionRetry,
      CartService cartService,
      AccountRepository accountRepository,
      AddressRepository addressRepository,
      CartItemRepository cartItemRepository,
      ProductVariantRepository variantRepository,
      OrderRepository orderRepository,
      RecomputeRequestService recomputeRequestService,
      RefundService refundService,
      VnPayClient vnPayClient,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.cartService = cartService;
    this.accountRepository = accountRepository;
    this.addressRepository = addressRepository;
    this.cartItemRepository = cartItemRepository;
    this.variantRepository = variantRepository;
    this.orderRepository = orderRepository;
    this.recomputeRequestService = recomputeRequestService;
    this.refundService = refundService;
    this.vnPayClient = vnPayClient;
    this.clock = clock;
  }

  /**
   * Phí vận chuyển của đơn có tổng tiền hàng này (BR-35): 30.000đ, miễn phí từ 500.000đ.
   *
   * @param subtotal tổng tiền hàng, trước giảm giá
   * @return phí vận chuyển
   */
  public static long shippingFeeFor(long subtotal) {
    return subtotal >= FREE_SHIPPING_THRESHOLD ? 0 : SHIPPING_FEE;
  }

  /**
   * Tóm tắt đơn hàng trước khi xác nhận (US-10): giỏ hàng theo giá và tồn kho hiện tại (đã tự loại
   * dòng không còn mua được, BR-11), phí vận chuyển và tổng thanh toán.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @return tóm tắt đơn hàng; giỏ rỗng thì không có dòng nào và tổng bằng 0
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  public CheckoutPreviewResponse preview(long accountId) {
    CartResponse cart = cartService.getCart(accountId);
    long subtotal = cart.subtotal();
    long shippingFee = cart.items().isEmpty() ? 0 : shippingFeeFor(subtotal);
    return new CheckoutPreviewResponse(
        cart.items(),
        cart.notices(),
        subtotal,
        shippingFee,
        0,
        subtotal + shippingFee,
        vnPayClient.configured());
  }

  /**
   * Đặt toàn bộ giỏ hàng thành một đơn (US-10): trừ tồn kho, xóa giỏ hàng, đơn ở trạng thái Chờ xử
   * lý. Gửi lại cùng mã định danh thì trả lại đơn đã tạo, không làm gì thêm (BR-10).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param request mã định danh, phương thức thanh toán, địa chỉ và tổng tiền khách đã thấy
   * @return đơn hàng và cờ cho biết đơn vừa được tạo hay đã có từ lần gửi trước
   * @throws NoSuchElementException nếu tài khoản hoặc địa chỉ không có
   * @throws InvalidRequestException nếu chọn thanh toán online (chưa hỗ trợ)
   * @throws ConflictException nếu mã định danh đã thuộc tài khoản khác, giỏ hàng rỗng, có dòng
   *     ngừng bán hoặc không đủ tồn kho (BR-09), hay tổng tiền đã đổi so với lúc xem trước
   */
  public PlaceOrderResult placeOrder(long accountId, PlaceOrderRequest request) {
    return transactionRetry.execute(() -> place(accountId, request));
  }

  /**
   * Danh sách đơn hàng của tài khoản, mới nhất trước (US-11).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param page số trang, bắt đầu từ 0
   * @param size số đơn mỗi trang, từ 1 đến {@link #MAX_PAGE_SIZE}
   * @return một trang đơn hàng
   */
  @Transactional(readOnly = true)
  public PageResponse<OrderSummaryResponse> list(long accountId, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Trang bắt đầu từ 0, mỗi trang từ 1 đến %d đơn".formatted(MAX_PAGE_SIZE));
    }
    return PageResponse.from(
        orderRepository.findByAccountIdOrderByCreatedAtDescIdDesc(
            accountId, PageRequest.of(page, size)),
        OrderSummaryResponse::from);
  }

  /**
   * Chi tiết một đơn hàng của tài khoản (US-11).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param orderId mã đơn hàng
   * @return chi tiết đơn
   * @throws NoSuchElementException nếu đơn không có hoặc thuộc tài khoản khác
   */
  @Transactional(readOnly = true)
  public OrderResponse get(long accountId, long orderId) {
    return OrderResponse.from(findOwn(accountId, orderId));
  }

  /**
   * Khách tự hủy đơn (US-20): chỉ khi đơn còn Chờ xử lý hoặc Đang xử lý (BR-13); hoàn tồn kho
   * (BR-16). Hủy đơn đã hủy thì không làm gì, trả lại đơn.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param orderId mã đơn hàng
   * @return đơn sau khi hủy
   * @throws NoSuchElementException nếu đơn không có hoặc thuộc tài khoản khác
   * @throws ConflictException nếu đơn đã Đang giao hoặc Đã giao
   */
  public OrderResponse cancel(long accountId, long orderId) {
    return transactionRetry.execute(() -> cancelOwn(accountId, orderId));
  }

  private PlaceOrderResult place(long accountId, PlaceOrderRequest request) {
    // Khóa tài khoản trước mọi truy vấn khác (thứ tự khóa cố định: tài khoản rồi biến thể).
    final Account account = lockAccount(accountId);
    String key = request.idempotencyKey().strip();
    Optional<Order> previous = orderRepository.findByIdempotencyKey(key);
    if (previous.isPresent()) {
      if (!previous.get().getAccount().getId().equals(accountId)) {
        throw new ConflictException("Mã định danh lần đặt hàng đã được dùng, hãy tải lại trang");
      }
      return new PlaceOrderResult(OrderResponse.from(previous.get()), false);
    }
    if (request.paymentMethod() == PaymentMethod.ONLINE && !vnPayClient.configured()) {
      throw new InvalidRequestException("Cửa hàng chưa bật thanh toán online qua VNPay");
    }
    final Address address =
        addressRepository
            .findByIdAndAccountId(request.addressId(), accountId)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy địa chỉ giao hàng"));

    List<Long> variantIds = cartItemRepository.findVariantIdsOf(accountId);
    if (variantIds.isEmpty()) {
      throw new ConflictException("Giỏ hàng đang trống");
    }
    // Khóa các biến thể trước khi đọc tồn kho: đơn khác cùng mua biến thể này phải chờ (BR-08).
    variantRepository.findAllForUpdate(variantIds);
    List<CartItem> lines = cartItemRepository.findCartOf(accountId);
    List<String> problems = new ArrayList<>();
    for (CartItem line : lines) {
      String problem = problemOf(line);
      if (problem != null) {
        problems.add(problem);
      }
    }
    if (!problems.isEmpty()) {
      throw new ConflictException(
          "Không đặt được đơn hàng: %s. Hãy xem lại giỏ hàng."
              .formatted(String.join("; ", problems)));
    }

    long subtotal =
        lines.stream().mapToLong(line -> line.getVariant().getPrice() * line.getQuantity()).sum();
    long shippingFee = shippingFeeFor(subtotal);
    if (request.expectedTotal() != subtotal + shippingFee) {
      throw new ConflictException(
          "Giá sản phẩm vừa thay đổi, tổng thanh toán mới là %sđ. Hãy xem lại đơn hàng."
              .formatted(
                  String.format(Locale.ROOT, "%,d", subtotal + shippingFee).replace(',', '.')));
    }

    LocalDateTime now = LocalDateTime.now(clock);
    Order order = Order.create(account, key, address, request.paymentMethod(), shippingFee, now);
    for (CartItem line : lines) {
      ProductVariant variant = line.getVariant();
      order.addItem(new OrderItem(variant, line.getQuantity()));
      variant.decreaseStock(line.getQuantity());
    }
    orderRepository.save(order);
    cartItemRepository.deleteAll(lines);
    requestRecompute(accountId);
    return new PlaceOrderResult(OrderResponse.from(order), true);
  }

  /** Lý do dòng giỏ hàng không đặt được (BR-09), hoặc null nếu đặt được. */
  private static String problemOf(CartItem line) {
    ProductVariant variant = line.getVariant();
    String name = displayName(variant);
    if (variant.getProduct().isDeleted()) {
      return name + " đã ngừng bán";
    }
    if (variant.getStock() == 0) {
      return name + " đã hết hàng";
    }
    if (variant.getStock() < line.getQuantity()) {
      return "%s chỉ còn %d sản phẩm".formatted(name, variant.getStock());
    }
    return null;
  }

  /** Tên sản phẩm kèm nhãn phân loại trong ngoặc, vd "Giày A (Đen – 42)". */
  private static String displayName(ProductVariant variant) {
    String label = variant.label();
    String name = variant.getProduct().getName();
    return label == null ? name : "%s (%s)".formatted(name, label);
  }

  private OrderResponse cancelOwn(long accountId, long orderId) {
    lockAccount(accountId);
    Order order = findOwn(accountId, orderId);
    if (order.getStatus() == OrderStatus.CANCELLED) {
      return OrderResponse.from(order);
    }
    if (!order.getStatus().customerCancellable()) {
      throw new ConflictException(
          "Đơn hàng đã rời kho nên không thể tự hủy qua hệ thống. Hãy liên hệ cửa hàng nếu cần hỗ"
              + " trợ.");
    }
    restoreStock(order);
    cancelWithPayment(order, LocalDateTime.now(clock));
    requestRecompute(accountId);
    return OrderResponse.from(order);
  }

  /**
   * Hoàn tồn kho của mọi dòng đơn (BR-16). Khóa các biến thể theo mã tăng dần như lúc đặt hàng;
   * người gọi phải khóa tài khoản đặt đơn trước.
   */
  void restoreStock(Order order) {
    List<Long> variantIds =
        order.getItems().stream().map(item -> item.getVariant().getId()).distinct().toList();
    variantRepository.findAllForUpdate(variantIds);
    for (OrderItem item : order.getItems()) {
      item.getVariant().increaseStock(item.getQuantity());
    }
  }

  /**
   * Chuyển đơn sang Đã hủy kèm phần thanh toán: đơn ONLINE chưa trả tiền thì thanh toán chuyển
   * FAILED, đơn đã trả tiền thì khởi tạo hoàn tiền (BR-28). Người gọi đã hoàn tồn kho.
   */
  void cancelWithPayment(Order order, LocalDateTime now) {
    if (order.awaitingPayment()) {
      order.markPaymentFailed();
    }
    order.cancel(now);
    refundService.startIfPaid(order);
  }

  private Order findOwn(long accountId, long orderId) {
    return orderRepository
        .findByIdAndAccountId(orderId, accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đơn hàng"));
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

  /**
   * Kết quả đặt hàng.
   *
   * @param order đơn hàng
   * @param created true nếu đơn vừa được tạo, false nếu đã có từ lần gửi trước cùng mã định danh
   */
  public record PlaceOrderResult(OrderResponse order, boolean created) {}
}
