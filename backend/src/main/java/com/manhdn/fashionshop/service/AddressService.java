package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.AddressRequest;
import com.manhdn.fashionshop.dto.AddressResponse;
import com.manhdn.fashionshop.entity.Account;
import com.manhdn.fashionshop.entity.Address;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.AccountRepository;
import com.manhdn.fashionshop.repository.AddressRepository;
import com.manhdn.fashionshop.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sổ địa chỉ giao hàng của Khách hàng (US-09, Mục 3.2).
 *
 * <ul>
 *   <li>Mỗi tài khoản lưu tối đa {@link #MAX_ADDRESSES} địa chỉ.
 *   <li>Sổ có địa chỉ thì luôn có đúng 1 địa chỉ mặc định: địa chỉ đầu tiên tự là mặc định, đặt địa
 *       chỉ khác làm mặc định thì bỏ mặc định của địa chỉ cũ, xóa địa chỉ mặc định thì địa chỉ thêm
 *       gần nhất còn lại thành mặc định.
 *   <li>Xóa địa chỉ không ảnh hưởng đơn đã đặt: đơn giữ bản sao người nhận và địa chỉ (BR-29), chỉ
 *       bỏ tham chiếu address_id.
 *   <li>Thao tác ghi khóa dòng tài khoản trước, nên 2 thao tác đồng thời trên cùng sổ chạy lần
 *       lượt.
 * </ul>
 */
@Service
public class AddressService {

  /** Số địa chỉ tối đa của một tài khoản. */
  public static final int MAX_ADDRESSES = 10;

  private final TransactionRetry transactionRetry;
  private final AccountRepository accountRepository;
  private final AddressRepository addressRepository;
  private final OrderRepository orderRepository;
  private final Clock clock;

  /** Nhận các thành phần cần thiết qua constructor. */
  public AddressService(
      TransactionRetry transactionRetry,
      AccountRepository accountRepository,
      AddressRepository addressRepository,
      OrderRepository orderRepository,
      Clock clock) {
    this.transactionRetry = transactionRetry;
    this.accountRepository = accountRepository;
    this.addressRepository = addressRepository;
    this.orderRepository = orderRepository;
    this.clock = clock;
  }

  /**
   * Sổ địa chỉ của tài khoản, địa chỉ mặc định đứng đầu.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @return các địa chỉ, rỗng nếu chưa có
   */
  @Transactional(readOnly = true)
  public List<AddressResponse> list(long accountId) {
    return addressRepository.findByAccountIdOrderByIsDefaultDescIdDesc(accountId).stream()
        .map(AddressResponse::from)
        .toList();
  }

  /**
   * Thêm địa chỉ mới. Địa chỉ đầu tiên của sổ luôn là mặc định.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param request người nhận, số điện thoại, địa chỉ và có đặt làm mặc định không
   * @return địa chỉ vừa thêm
   * @throws NoSuchElementException nếu tài khoản không còn tồn tại
   * @throws ConflictException nếu sổ đã đủ {@link #MAX_ADDRESSES} địa chỉ
   */
  public AddressResponse create(long accountId, AddressRequest request) {
    return transactionRetry.execute(
        () -> {
          Account account = lockAccount(accountId);
          long count = addressRepository.countByAccountId(accountId);
          if (count >= MAX_ADDRESSES) {
            throw new ConflictException(
                "Sổ địa chỉ tối đa %d địa chỉ, hãy xóa bớt trước khi thêm"
                    .formatted(MAX_ADDRESSES));
          }
          Address address =
              new Address(
                  account,
                  clean(request.recipientName()),
                  request.phone(),
                  clean(request.addressDetail()),
                  LocalDateTime.now(clock));
          addressRepository.save(address);
          if (count == 0 || request.isDefault()) {
            makeDefault(accountId, address);
          }
          return AddressResponse.from(address);
        });
  }

  /**
   * Sửa một địa chỉ. Đơn đã đặt với địa chỉ này không đổi (BR-29).
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param addressId mã địa chỉ
   * @param request thông tin mới; isDefault true thì đặt làm mặc định
   * @return địa chỉ sau khi sửa
   * @throws NoSuchElementException nếu địa chỉ không có hoặc không thuộc tài khoản này
   */
  public AddressResponse update(long accountId, long addressId, AddressRequest request) {
    return transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          Address address = findOwn(accountId, addressId);
          address.update(
              clean(request.recipientName()), request.phone(), clean(request.addressDetail()));
          if (request.isDefault()) {
            makeDefault(accountId, address);
          }
          return AddressResponse.from(address);
        });
  }

  /**
   * Xóa một địa chỉ. Nếu đó là địa chỉ mặc định thì địa chỉ thêm gần nhất còn lại thành mặc định.
   *
   * @param accountId mã tài khoản lấy từ JWT
   * @param addressId mã địa chỉ
   * @throws NoSuchElementException nếu địa chỉ không có hoặc không thuộc tài khoản này
   */
  public void delete(long accountId, long addressId) {
    transactionRetry.execute(
        () -> {
          lockAccount(accountId);
          Address address = findOwn(accountId, addressId);
          orderRepository.detachAddress(address.getId());
          addressRepository.delete(address);
          addressRepository.flush();
          if (address.isDefault()) {
            addressRepository.findByAccountIdOrderByIsDefaultDescIdDesc(accountId).stream()
                .findFirst()
                .ifPresent(next -> next.setDefault(true));
          }
          return null;
        });
  }

  /** Đặt địa chỉ làm mặc định và bỏ mặc định của mọi địa chỉ khác trong sổ. */
  private void makeDefault(long accountId, Address address) {
    for (Address other : addressRepository.findByAccountIdOrderByIsDefaultDescIdDesc(accountId)) {
      other.setDefault(other.getId().equals(address.getId()));
    }
  }

  private Address findOwn(long accountId, long addressId) {
    return addressRepository
        .findByIdAndAccountId(addressId, accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy địa chỉ"));
  }

  private Account lockAccount(long accountId) {
    return accountRepository
        .findWithLockById(accountId)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài khoản"));
  }

  /** Bỏ khoảng trắng thừa ở hai đầu và gộp khoảng trắng liên tiếp, lưu ở dạng NFC. */
  private static String clean(String text) {
    return TextNormalizer.nfc(text.strip().replaceAll("\\s+", " "));
  }
}
