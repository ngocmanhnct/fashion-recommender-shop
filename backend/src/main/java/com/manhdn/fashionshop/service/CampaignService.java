package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CampaignRequest;
import com.manhdn.fashionshop.dto.CampaignResponse;
import com.manhdn.fashionshop.dto.VoucherResponse;
import com.manhdn.fashionshop.entity.CampaignTarget;
import com.manhdn.fashionshop.entity.CustomerSegment;
import com.manhdn.fashionshop.entity.SegmentCampaign;
import com.manhdn.fashionshop.entity.Voucher;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.exception.InvalidRequestException;
import com.manhdn.fashionshop.repository.CustomerSegmentRepository;
import com.manhdn.fashionshop.repository.SegmentCampaignRepository;
import com.manhdn.fashionshop.repository.SegmentCampaignRepository.Outcome;
import com.manhdn.fashionshop.repository.SegmentEntryRepository;
import com.manhdn.fashionshop.repository.SegmentProfileRepository;
import com.manhdn.fashionshop.repository.VoucherRecipientRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chiến dịch ưu đãi theo nhóm khách hàng (US-26): Quản trị viên tặng mã giảm giá riêng cho cả một
 * nhóm hoặc cho khách cần chăm sóc của nhóm (BR-43), chỉ những khách đó dùng được mã (BR-44); sau
 * đó đo kết quả bằng đơn hàng dùng mã: bao nhiêu khách quay lại mua, doanh thu, tiền đã giảm.
 *
 * <p>Chương trình tự tặng (US-24, BR-45) là chiến dịch AUTO: chưa ai nhận lúc bật; mỗi lượt của
 * {@link #grantAutoOffers()} thêm các khách vừa vào nhóm vào danh sách nhận, mỗi khách dùng mã
 * trong số ngày đã đặt kể từ lúc nhận.
 */
@Service
public class CampaignService {

  /** Số chiến dịch trả về tối đa, mới nhất trước. */
  static final int MAX_CAMPAIGNS = 50;

  private final CustomerSegmentRepository customerSegmentRepository;
  private final SegmentProfileRepository profileRepository;
  private final SegmentProfileService segmentProfileService;
  private final SegmentCampaignRepository campaignRepository;
  private final VoucherRecipientRepository recipientRepository;
  private final SegmentEntryRepository entryRepository;
  private final VoucherService voucherService;
  private final Clock clock;

  /** Nhận các repository, service và đồng hồ qua constructor. */
  public CampaignService(
      CustomerSegmentRepository customerSegmentRepository,
      SegmentProfileRepository profileRepository,
      SegmentProfileService segmentProfileService,
      SegmentCampaignRepository campaignRepository,
      VoucherRecipientRepository recipientRepository,
      SegmentEntryRepository entryRepository,
      VoucherService voucherService,
      Clock clock) {
    this.customerSegmentRepository = customerSegmentRepository;
    this.profileRepository = profileRepository;
    this.segmentProfileService = segmentProfileService;
    this.campaignRepository = campaignRepository;
    this.recipientRepository = recipientRepository;
    this.entryRepository = entryRepository;
    this.voucherService = voucherService;
    this.clock = clock;
  }

  /**
   * Tặng mã riêng cho khách của nhóm: tạo mã chỉ người được tặng dùng được, ghi danh sách người
   * được tặng là khách thuộc nhóm (hoặc khách cần chăm sóc) ngay lúc này.
   *
   * @param segmentCode mã nhóm, vd AT_RISK
   * @param request đối tượng và điều kiện của mã
   * @return chiến dịch vừa tạo, kết quả bằng 0
   * @throws NoSuchElementException nếu không có nhóm này
   * @throws ConflictException nếu không có khách nào để tặng, trùng mã, hoặc nhóm đã có chương
   *     trình tự tặng đang chạy
   * @throws InvalidRequestException nếu điều kiện của mã mâu thuẫn hoặc tự tặng thiếu số ngày
   */
  @Transactional
  public CampaignResponse create(String segmentCode, CampaignRequest request) {
    CustomerSegment segment = segment(segmentCode);
    if (request.target() == CampaignTarget.AUTO) {
      return createAuto(segment, request);
    }
    List<Long> recipients =
        request.target() == CampaignTarget.ALL
            ? profileRepository.memberIds(segment.getId())
            : segmentProfileService.careAccountIds(segment.getId());
    if (recipients.isEmpty()) {
      throw new ConflictException(
          request.target() == CampaignTarget.ALL
              ? "Nhóm chưa có khách nào để tặng mã"
              : "Nhóm hiện không có khách cần chăm sóc");
    }
    Voucher voucher = voucherService.createVoucher(request.voucher(), true);
    recipientRepository.addAll(voucher.getId(), recipients);
    SegmentCampaign campaign =
        campaignRepository.save(
            new SegmentCampaign(
                segment.getCode(),
                segment.getName(),
                request.target(),
                voucher,
                recipients.size(),
                LocalDateTime.now(clock)));
    return response(campaign, null);
  }

  /**
   * Bật tự tặng: ghi trước nhóm hiện tại của mọi khách, để khách đang ở trong nhóm lúc bật không bị
   * coi là khách mới vào (chỉ khách vào sau lúc bật mới nhận mã).
   */
  private CampaignResponse createAuto(CustomerSegment segment, CampaignRequest request) {
    if (request.validDays() == null) {
      throw new InvalidRequestException("Chưa nhập số ngày khách được dùng mã");
    }
    if (campaignRepository.existsBySegmentCodeAndAutoEnabledTrue(segment.getCode())) {
      throw new ConflictException(
          "Nhóm đang có chương trình tự tặng mã. Dừng chương trình đó trước khi bật chương trình"
              + " mới");
    }
    LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    entryRepository.sync(now);
    Voucher voucher = voucherService.createVoucher(request.voucher(), true);
    SegmentCampaign campaign =
        campaignRepository.save(
            new SegmentCampaign(
                segment.getCode(),
                segment.getName(),
                CampaignTarget.AUTO,
                voucher,
                0,
                now,
                request.validDays()));
    return response(campaign, null);
  }

  /**
   * Dừng chương trình tự tặng (US-24): khách vào nhóm sau đó không nhận mã nữa, khách đã nhận vẫn
   * dùng được đến hạn riêng. Gọi lại nhiều lần vẫn đúng.
   *
   * @param id mã số chiến dịch
   * @return chiến dịch sau khi dừng, kèm kết quả
   * @throws NoSuchElementException nếu không có chiến dịch này
   * @throws InvalidRequestException nếu đây không phải chương trình tự tặng
   */
  @Transactional
  public CampaignResponse stopAuto(long id) {
    SegmentCampaign campaign =
        campaignRepository
            .findById(id)
            .orElseThrow(() -> new NoSuchElementException("Không tìm thấy chiến dịch"));
    if (campaign.getTarget() != CampaignTarget.AUTO) {
      throw new InvalidRequestException("Chỉ dừng được chương trình tự tặng mã");
    }
    campaign.stopAuto();
    Outcome outcome =
        campaignRepository.outcomes(List.of(campaign.getVoucher().getId())).stream()
            .findFirst()
            .orElse(null);
    return response(campaign, outcome);
  }

  /**
   * Một lượt tự tặng (US-24, BR-45): ghi lúc vào nhóm của khách vừa được phân nhóm hoặc đổi nhóm,
   * rồi tặng mã của các chương trình đang chạy cho khách vào nhóm sau lúc bật chương trình.
   *
   * @return số khách vừa được tặng
   */
  @Transactional
  public int grantAutoOffers() {
    entryRepository.sync(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    int granted =
        entryRepository.grantAutoOffers(
            LocalDateTime.now(clock.withZone(VoucherService.ZONE)).truncatedTo(ChronoUnit.SECONDS));
    if (granted > 0) {
      entryRepository.refreshAutoRecipientCounts();
    }
    return granted;
  }

  /**
   * Các chiến dịch kèm kết quả, mới nhất trước (tối đa {@link #MAX_CAMPAIGNS}).
   *
   * @param segmentCode mã nhóm; null hoặc rỗng là mọi nhóm
   * @return chiến dịch và kết quả tính đến lúc gọi
   */
  @Transactional(readOnly = true)
  public List<CampaignResponse> list(String segmentCode) {
    String code = segmentCode == null ? "" : segmentCode.strip();
    if (code.length() > 20) {
      throw new InvalidRequestException("Mã nhóm tối đa 20 ký tự");
    }
    List<SegmentCampaign> campaigns =
        campaignRepository.findLatest(code, PageRequest.of(0, MAX_CAMPAIGNS));
    if (campaigns.isEmpty()) {
      return List.of();
    }
    Map<Long, Outcome> outcomes =
        campaignRepository
            .outcomes(campaigns.stream().map(c -> c.getVoucher().getId()).toList())
            .stream()
            .collect(Collectors.toMap(Outcome::voucherId, Function.identity()));
    return campaigns.stream().map(c -> response(c, outcomes.get(c.getVoucher().getId()))).toList();
  }

  private CampaignResponse response(SegmentCampaign c, Outcome outcome) {
    long buyers = outcome == null ? 0 : outcome.buyers();
    long revenue = outcome == null || outcome.revenue() == null ? 0 : outcome.revenue();
    long discount = outcome == null || outcome.discount() == null ? 0 : outcome.discount();
    return new CampaignResponse(
        c.getId(),
        c.getSegmentCode(),
        c.getSegmentName(),
        c.getTarget(),
        c.getValidDays(),
        c.isAutoEnabled(),
        c.getCreatedAt(),
        c.getRecipientCount(),
        VoucherResponse.from(
            c.getVoucher(), LocalDateTime.now(clock.withZone(VoucherService.ZONE))),
        buyers,
        c.getRecipientCount() == 0 ? 0 : Math.round(buyers * 1000.0 / c.getRecipientCount()) / 10.0,
        outcome == null ? 0 : outcome.orders(),
        revenue,
        discount,
        discount == 0 ? null : Math.round(revenue * 10.0 / discount) / 10.0);
  }

  private CustomerSegment segment(String code) {
    return customerSegmentRepository
        .findByCode(code)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy nhóm khách hàng"));
  }
}
