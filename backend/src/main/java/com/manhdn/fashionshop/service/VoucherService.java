package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.MyVoucherResponse;
import com.manhdn.fashionshop.dto.PageResponse;
import com.manhdn.fashionshop.dto.VoucherRequest;
import com.manhdn.fashionshop.dto.VoucherResponse;
import com.manhdn.fashionshop.entity.DiscountType;
import com.manhdn.fashionshop.entity.Voucher;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.OrderRepository;
import com.manhdn.fashionshop.repository.VoucherRecipientRepository;
import com.manhdn.fashionshop.repository.VoucherRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mã giảm giá (US-21, US-22, BR-30 đến BR-33): Quản trị viên tạo, sửa, bật tắt mã; đơn hàng kiểm
 * tra mã và tính số tiền giảm. Thời hạn của mã tính theo giờ Việt Nam, không theo múi giờ máy chủ
 * (máy chủ Docker chạy giờ UTC).
 */
@Service
public class VoucherService {

  /** Múi giờ của thời hạn mã giảm giá. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  /** Tổng thanh toán sau giảm giá không dưới mức này (BR-32). */
  public static final long MIN_ORDER_TOTAL = 5_000;

  /** Số mã mỗi trang tối đa ở trang quản trị. */
  public static final int MAX_PAGE_SIZE = 50;

  private static final String DUPLICATE_CODE = "Mã này đã có, hãy chọn mã khác";
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

  private final VoucherRepository voucherRepository;
  private final VoucherRecipientRepository recipientRepository;
  private final OrderRepository orderRepository;
  private final Clock clock;

  /** Nhận repository và đồng hồ qua constructor. */
  public VoucherService(
      VoucherRepository voucherRepository,
      VoucherRecipientRepository recipientRepository,
      OrderRepository orderRepository,
      Clock clock) {
    this.voucherRepository = voucherRepository;
    this.recipientRepository = recipientRepository;
    this.orderRepository = orderRepository;
    this.clock = clock;
  }

  /**
   * Danh sách mã cho Quản trị viên, mới tạo trước.
   *
   * @param keyword một phần mã (tối đa 50 ký tự), rỗng là mọi mã
   * @param page số trang, từ 0
   * @param size số mã mỗi trang, 1 đến {@link #MAX_PAGE_SIZE}
   * @return một trang mã kèm số lượt đã dùng, còn lại và tình trạng
   */
  @Transactional(readOnly = true)
  public PageResponse<VoucherResponse> list(String keyword, int page, int size) {
    if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
      throw new InvalidRequestException(
          "Trang bắt đầu từ 0, mỗi trang từ 1 đến %d mã".formatted(MAX_PAGE_SIZE));
    }
    String term = keyword == null ? "" : keyword.strip();
    if (term.length() > 50) {
      throw new InvalidRequestException("Từ khóa tối đa 50 ký tự");
    }
    LocalDateTime now = now();
    return PageResponse.from(
        voucherRepository.search(term, PageRequest.of(page, size)),
        v -> VoucherResponse.from(v, now));
  }

  /**
   * Tạo mã mới (US-22), đang bật.
   *
   * @param request điều kiện của mã
   * @return mã vừa tạo
   * @throws InvalidRequestException nếu điều kiện mâu thuẫn (vd phần trăm trên 100)
   * @throws ConflictException nếu trùng mã
   */
  @Transactional
  public VoucherResponse create(VoucherRequest request) {
    return VoucherResponse.from(createVoucher(request, false), now());
  }

  /**
   * Tạo mã đang bật và trả về entity, dùng chung cho mã công khai và mã tặng riêng (US-26). Phải
   * gọi trong giao dịch của người gọi.
   *
   * @param request điều kiện của mã
   * @param recipientsOnly true nếu chỉ khách được tặng dùng được (BR-44)
   * @return mã vừa lưu, đã có id
   * @throws InvalidRequestException nếu điều kiện mâu thuẫn
   * @throws ConflictException nếu trùng mã
   */
  @Transactional
  public Voucher createVoucher(VoucherRequest request, boolean recipientsOnly) {
    validate(request);
    String code = normalize(request.code());
    if (voucherRepository.existsByCode(code)) {
      throw new ConflictException(DUPLICATE_CODE);
    }
    Voucher voucher = new Voucher(code, now(), recipientsOnly);
    configure(voucher, request);
    try {
      return voucherRepository.saveAndFlush(voucher);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_CODE);
    }
  }

  /**
   * Mã riêng khách được tặng và đang dùng được (US-26): đang bật, trong thời hạn, còn lượt, khách
   * chưa dùng hết lượt của mình. Hết hạn sớm nhất đứng trước.
   *
   * @param accountId tài khoản khách
   * @return các mã còn dùng được, chưa xét đơn tối thiểu
   */
  @Transactional(readOnly = true)
  public List<MyVoucherResponse> myVouchers(long accountId) {
    List<Long> ids = recipientRepository.voucherIdsOf(accountId);
    if (ids.isEmpty()) {
      return List.of();
    }
    return voucherRepository.findAllById(ids).stream()
        .filter(v -> problemOf(v, accountId, Long.MAX_VALUE) == null)
        .sorted(Comparator.comparing(Voucher::getValidUntil))
        .map(MyVoucherResponse::from)
        .toList();
  }

  /**
   * Sửa mã (US-22). Đơn đã đặt giữ nguyên số tiền đã giảm; mã đã có người dùng thì không đổi được
   * chuỗi mã, để khách và đơn cũ không nhầm.
   *
   * @param id mã số
   * @param request điều kiện mới
   * @return mã sau khi sửa
   * @throws NoSuchElementException nếu không có mã
   * @throws ConflictException nếu đổi chuỗi mã của mã đã dùng hoặc trùng mã khác
   */
  @Transactional
  public VoucherResponse update(long id, VoucherRequest request) {
    validate(request);
    Voucher voucher = get(id);
    String code = normalize(request.code());
    if (!code.equals(voucher.getCode())) {
      if (voucher.getUsedCount() > 0) {
        throw new ConflictException("Mã đã có người dùng nên không đổi được chuỗi mã");
      }
      if (voucherRepository.existsByCodeAndIdNot(code, id)) {
        throw new ConflictException(DUPLICATE_CODE);
      }
      voucher.setCode(code);
    }
    configure(voucher, request);
    voucherRepository.flush();
    return VoucherResponse.from(voucher, now());
  }

  /**
   * Bật lại hoặc ngừng áp dụng mã trước hạn (US-22). Đơn đã đặt bằng mã không bị ảnh hưởng.
   *
   * @param id mã số
   * @param active true để bật, false để tắt
   * @return mã sau khi đổi
   * @throws NoSuchElementException nếu không có mã
   */
  @Transactional
  public VoucherResponse setActive(long id, boolean active) {
    Voucher voucher = get(id);
    voucher.setActive(active);
    return VoucherResponse.from(voucher, now());
  }

  /**
   * Kiểm tra mã cho đơn của khách và tính số tiền giảm (US-21, BR-30, BR-32). Không đổi gì trong
   * CSDL: lượt dùng chỉ được trừ khi đặt đơn.
   *
   * @param accountId tài khoản đặt hàng
   * @param rawCode mã khách nhập
   * @param subtotal tổng tiền hàng
   * @return mã và số tiền giảm thực nhận, hoặc lý do không dùng được
   */
  @Transactional(readOnly = true)
  public Check check(long accountId, String rawCode, long subtotal) {
    Voucher voucher = voucherRepository.findByCode(normalize(rawCode)).orElse(null);
    if (voucher == null) {
      return Check.rejected("Mã giảm giá không đúng");
    }
    String problem = problemOf(voucher, accountId, subtotal);
    if (problem != null) {
      return Check.rejected(problem);
    }
    return new Check(voucher, discountFor(voucher, subtotal), null);
  }

  /** Số tiền giảm thực nhận: không vượt tổng tiền hàng trừ mức sàn 5.000đ (BR-32). */
  static long discountFor(Voucher voucher, long subtotal) {
    long ceiling = Math.max(0, subtotal - MIN_ORDER_TOTAL);
    return Math.min(voucher.rawDiscountFor(subtotal), ceiling);
  }

  private String problemOf(Voucher voucher, long accountId, long subtotal) {
    if (voucher.isRecipientsOnly() && !recipientRepository.contains(voucher.getId(), accountId)) {
      return "Mã này chỉ dành cho khách được tặng";
    }
    LocalDateTime now = now();
    if (!voucher.isActive()) {
      return "Mã giảm giá đã ngừng áp dụng";
    }
    if (now.isBefore(voucher.getValidFrom())) {
      return "Mã giảm giá áp dụng từ " + TIME.format(voucher.getValidFrom());
    }
    if (now.isAfter(voucher.getValidUntil())) {
      return "Mã giảm giá đã hết hạn";
    }
    if (!voucher.hasTotalUsesLeft()) {
      return "Mã giảm giá đã hết lượt dùng";
    }
    Integer perAccount = voucher.getUsageLimitPerAccount();
    if (perAccount != null
        && orderRepository.countVoucherUses(accountId, voucher.getId()) >= perAccount) {
      return "Bạn đã dùng hết lượt của mã này";
    }
    Long min = voucher.getMinOrderAmount();
    if (min != null && subtotal < min) {
      return "Mã áp dụng cho đơn từ %sđ, đơn của bạn còn thiếu %sđ"
          .formatted(money(min), money(min - subtotal));
    }
    return null;
  }

  private static void validate(VoucherRequest request) {
    if (request.discountType() == DiscountType.PERCENT && request.discountValue() > 100) {
      throw new InvalidRequestException("Giảm theo phần trăm chỉ từ 1 đến 100%");
    }
    if (request.discountType() == DiscountType.FIXED && request.maxDiscountAmount() != null) {
      throw new InvalidRequestException("Trần giảm chỉ dùng cho mã giảm theo phần trăm");
    }
    if (!request.validUntil().isAfter(request.validFrom())) {
      throw new InvalidRequestException("Thời điểm hết hạn phải sau thời điểm bắt đầu");
    }
    if (request.usageLimitTotal() != null
        && request.usageLimitPerAccount() != null
        && request.usageLimitPerAccount() > request.usageLimitTotal()) {
      throw new InvalidRequestException("Lượt dùng mỗi khách không được nhiều hơn tổng lượt dùng");
    }
  }

  private static void configure(Voucher voucher, VoucherRequest request) {
    voucher.configure(
        request.discountType(),
        request.discountValue(),
        request.minOrderAmount() == null || request.minOrderAmount() == 0
            ? null
            : request.minOrderAmount(),
        request.maxDiscountAmount(),
        request.validFrom(),
        request.validUntil(),
        request.usageLimitTotal(),
        request.usageLimitPerAccount());
  }

  private Voucher get(long id) {
    return voucherRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy mã giảm giá"));
  }

  private LocalDateTime now() {
    return LocalDateTime.now(clock.withZone(ZONE));
  }

  /** Mã lưu dạng chữ hoa, bỏ khoảng trắng hai đầu. */
  static String normalize(String code) {
    return code.strip().toUpperCase(Locale.ROOT);
  }

  private static String money(long amount) {
    return String.format(Locale.ROOT, "%,d", amount).replace(',', '.');
  }

  /**
   * Kết quả kiểm tra mã cho một đơn.
   *
   * @param voucher mã dùng được, null nếu không
   * @param discount số tiền giảm thực nhận
   * @param error lý do không dùng được, null nếu dùng được
   */
  public record Check(Voucher voucher, long discount, String error) {

    static Check rejected(String error) {
      return new Check(null, 0, error);
    }

    /** Mã dùng được cho đơn này không. */
    public boolean ok() {
      return error == null;
    }
  }
}
