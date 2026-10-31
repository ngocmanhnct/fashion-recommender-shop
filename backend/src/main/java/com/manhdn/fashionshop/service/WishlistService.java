package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.ProductCardResponse;
import com.manhdn.fashionshop.dto.WishlistItemResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Product;
import com.manhdn.fashionshop.entity.RecomputeTask;
import com.manhdn.fashionshop.entity.WishlistItem;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import com.manhdn.fashionshop.repository.WishlistItemRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách yêu thích (US-14): khách hàng thêm, bỏ và xem các sản phẩm muốn mua sau.
 *
 * <ul>
 *   <li>Ghi ở cấp sản phẩm, không phải biến thể; thêm lại sản phẩm đã có hoặc bỏ sản phẩm không có
 *       trong danh sách thì không làm gì (gọi lại nhiều lần vẫn đúng).
 *   <li>Tối đa {@link #MAX_ITEMS} sản phẩm. Sản phẩm đã ngừng bán (xóa mềm) không thêm được và
 *       không hiện trong danh sách nhưng dòng vẫn giữ trong CSDL.
 *   <li>Mỗi lần danh sách thật sự đổi thì yêu cầu tính lại gợi ý sau 3 giây (BR-20): ml-service coi
 *       sản phẩm yêu thích là tín hiệu mạnh hơn lượt xem, yếu hơn thêm vào giỏ.
 *   <li>Thao tác ghi chạy qua {@link TransactionRetry} và khóa dòng tài khoản trước, như giỏ hàng.
 * </ul>
 */
@Service
public class WishlistService {

  /** Số sản phẩm tối đa trong danh sách yêu thích của 1 tài khoản. */
  public static final int MAX_ITEMS = 100;

  private final TransactionRetry transactionRetry;
  private final AccountRepository accountRepository;
  private final ProductRepository productRepository;
  private final WishlistItemRepository wishlistItemRepository;
  private final RecomputeRequestService recomputeRequestService;
  private final Clock clock;

  /** Nhận các phụ thuộc qua constructor. */
  public WishlistService(
      TransactionRetry transactionRetry,
      AccountRepository accountRepository,
      ProductRepository productRepository,
      WishlistItemRepository wishlistItemRepository,
      RecomputeRequestService recomputeRequestService,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.accountRepository = accountRepository;
    this.productRepository = productRepository;
    this.wishlistItemRepository = wishlistItemRepository;
    this.recomputeRequestService = recomputeRequestService;
    this.clock = clock;
  }

  /**
   * Các sản phẩm còn bán trong danh sách yêu thích, thêm sau đứng trước. Sản phẩm hết hàng vẫn hiện
   * (thẻ báo hết hàng) để khách biết khi nào có lại.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @return danh sách sản phẩm kèm lúc thêm
   */
  @Transactional(readOnly = true)
  public List<WishlistItemResponse> list(long accountId) {
    return wishlistItemRepository.findListOf(accountId).stream()
        .map(
            item ->
                new WishlistItemResponse(
                    ProductCardResponse.from(item.getProduct()), item.getCreatedAt()))
        .toList();
  }

  /**
   * Mã các sản phẩm còn bán trong danh sách yêu thích, để giao diện tô nút yêu thích.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @return mã sản phẩm tăng dần
   */
  @Transactional(readOnly = true)
  public List<Long> productIds(long accountId) {
    return wishlistItemRepository.findProductIdsOf(accountId);
  }

  /**
   * Thêm sản phẩm vào danh sách yêu thích; đã có thì không làm gì.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param productId mã sản phẩm
   * @return mã các sản phẩm trong danh sách sau khi thêm
   * @throws NoSuchElementException nếu tài khoản không có, hoặc sản phẩm không có hay đã ngừng bán
   * @throws ConflictException nếu danh sách đã đủ {@link #MAX_ITEMS} sản phẩm
   */
  public List<Long> add(long accountId, long productId) {
    return transactionRetry.execute(
        () -> {
          Account account = lockAccount(accountId);
          Product product =
              productRepository
                  .findById(productId)
                  .filter(p -> !p.isDeleted())
                  .orElseThrow(() -> new NoSuchElementException("Không tìm thấy sản phẩm"));
          if (wishlistItemRepository.findByAccountIdAndProductId(accountId, productId).isEmpty()) {
            if (wishlistItemRepository.countByAccountId(accountId) >= MAX_ITEMS) {
              throw new ConflictException(
                  "Danh sách yêu thích đã đủ "
                      + MAX_ITEMS
                      + " sản phẩm, hãy bỏ bớt trước khi thêm");
            }
            wishlistItemRepository.save(
                new WishlistItem(account, product, LocalDateTime.now(clock)));
            requestRecompute(accountId);
          }
          return wishlistItemRepository.findProductIdsOf(accountId);
        });
  }

  /**
   * Bỏ sản phẩm khỏi danh sách yêu thích; không có trong danh sách thì không làm gì.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param productId mã sản phẩm
   * @return mã các sản phẩm trong danh sách sau khi bỏ
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   */
  public List<Long> remove(long accountId, long productId) {
    return transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          Optional<WishlistItem> item =
              wishlistItemRepository.findByAccountIdAndProductId(accountId, productId);
          if (item.isPresent()) {
            wishlistItemRepository.delete(item.get());
            wishlistItemRepository.flush();
            requestRecompute(accountId);
          }
          return wishlistItemRepository.findProductIdsOf(accountId);
        });
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
}
