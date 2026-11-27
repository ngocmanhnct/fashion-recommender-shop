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
import com.manhdn.fashionshop.repository.SegmentEntryRepository.AutoCampaign;
import com.manhdn.fashionshop.repository.SegmentProfileRepository;
import com.manhdn.fashionshop.repository.VoucherRecipientRepository;
import com.manhdn.fashionshop.repository.VoucherRecipientRepository.GroupBehavior;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chiến dịch ưu đãi theo nhóm khách hàng (US-24): Quản trị viên tặng mã giảm giá riêng cho cả một
 * nhóm hoặc cho khách cần chăm sóc của nhóm (BR-44), chỉ những khách đó dùng được mã (BR-43); sau
 * đó đo kết quả bằng đơn hàng dùng mã: bao nhiêu khách quay lại mua, doanh thu, tiền đã giảm.
 *
 * <p>Chương trình tự tặng (US-24, BR-43) là chiến dịch AUTO: chưa ai nhận lúc bật; mỗi lượt của
 * {@link #grantAutoOffers()} thêm các khách vừa vào nhóm vào danh sách nhận, mỗi khách dùng mã
 * trong số ngày đã đặt kể từ lúc nhận.
 *
 * <p>Đo hiệu quả (rà soát 12/11/2026, việc 3): mỗi chiến dịch có thể giữ lại ngẫu nhiên một phần
 * khách đủ điều kiện, không tặng mã (nhóm đối chứng). Kết quả so tỷ lệ mua của khách nhận mã với
 * khách bị giữ lại, và khi có lãi gộp thì ra lãi tăng thêm sau khi trừ tiền giảm. Tỷ số doanh thu
 * trên tiền giảm cũ vẫn trả về nhưng tính cả những khách vốn sẽ mua dù không có mã.
 *
 * <p>Chặn tặng hàng loạt (việc 4): chương trình tự tặng có trần số khách, và bỏ qua khách chỉ đổi
 * nhóm vì huấn luyện lại (xem {@link SegmentEntryRepository#sync}).
 */
@Service
public class CampaignService {

  /** Số chiến dịch trả về tối đa, mới nhất trước. */
  static final int MAX_CAMPAIGNS = 50;

  private final SecureRandom random = new SecureRandom();

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
    int holdoutPercent = holdoutPercent(request);
    List<Long> shuffled = new ArrayList<>(recipients);
    Collections.shuffle(shuffled, random);
    int holdoutCount = shuffled.size() * holdoutPercent / 100;
    List<Long> held = shuffled.subList(0, holdoutCount);
    List<Long> treated = shuffled.subList(holdoutCount, shuffled.size());
    Voucher voucher = voucherService.createVoucher(request.voucher(), true);
    recipientRepository.addAll(voucher.getId(), treated, false);
    if (!held.isEmpty()) {
      recipientRepository.addAll(voucher.getId(), held, true);
    }
    SegmentCampaign campaign =
        campaignRepository.save(
            new SegmentCampaign(
                    segment.getCode(),
                    segment.getName(),
                    request.target(),
                    voucher,
                    treated.size(),
                    LocalDateTime.now(clock))
                .withMeasurement(holdoutPercent, holdoutCount, null, request.grossMarginPercent()));
    return response(campaign, null, List.of());
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
                    request.validDays())
                .withMeasurement(
                    holdoutPercent(request),
                    0,
                    request.maxRecipients(),
                    request.grossMarginPercent()));
    return response(campaign, null, List.of());
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
    long voucherId = campaign.getVoucher().getId();
    Outcome outcome =
        campaignRepository.outcomes(List.of(voucherId)).stream().findFirst().orElse(null);
    return response(
        campaign, outcome, recipientRepository.behaviors(List.of(voucherId), vnAheadSeconds()));
  }

  /**
   * Một lượt tự tặng (US-24, BR-43): ghi lúc vào nhóm của khách vừa được phân nhóm hoặc đổi nhóm,
   * rồi tặng mã của các chương trình đang chạy cho khách vào nhóm sau lúc bật chương trình, không
   * quá trần số khách của chương trình. Mỗi khách đủ điều kiện có xác suất bằng tỷ lệ giữ lại để
   * vào nhóm đối chứng thay vì nhận mã.
   *
   * @return số khách vừa được tặng (không tính khách bị giữ lại)
   */
  @Transactional
  public int grantAutoOffers() {
    entryRepository.sync(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    LocalDateTime nowVn =
        LocalDateTime.now(clock.withZone(VoucherService.ZONE)).truncatedTo(ChronoUnit.SECONDS);
    int granted = 0;
    boolean changed = false;
    for (AutoCampaign campaign : entryRepository.activeAutoCampaigns(nowVn)) {
      int limit =
          campaign.maxRecipients() == null
              ? Integer.MAX_VALUE
              : campaign.maxRecipients() - campaign.taken();
      if (limit <= 0) {
        continue;
      }
      List<Long> eligible = entryRepository.eligibleForAuto(campaign, nowVn, limit);
      List<Long> treated = new ArrayList<>();
      List<Long> held = new ArrayList<>();
      for (Long accountId : eligible) {
        (random.nextInt(100) < campaign.holdoutPercent() ? held : treated).add(accountId);
      }
      entryRepository.addAutoRecipients(campaign.voucherId(), treated, false, nowVn);
      entryRepository.addAutoRecipients(campaign.voucherId(), held, true, nowVn);
      granted += treated.size();
      changed |= !eligible.isEmpty();
    }
    if (changed) {
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
    List<Long> voucherIds = campaigns.stream().map(c -> c.getVoucher().getId()).toList();
    Map<Long, Outcome> outcomes =
        campaignRepository.outcomes(voucherIds).stream()
            .collect(Collectors.toMap(Outcome::voucherId, Function.identity()));
    Map<Long, List<GroupBehavior>> behaviors =
        recipientRepository.behaviors(voucherIds, vnAheadSeconds()).stream()
            .collect(Collectors.groupingBy(GroupBehavior::voucherId));
    return campaigns.stream()
        .map(c ->
            response(
                c,
                outcomes.get(c.getVoucher().getId()),
                behaviors.getOrDefault(c.getVoucher().getId(), List.of())))
        .toList();
  }

  private static int holdoutPercent(CampaignRequest request) {
    return request.holdoutPercent() == null ? 0 : request.holdoutPercent();
  }

  /** Số giây giờ Việt Nam đi trước giờ JVM lúc này (giờ JVM của CSDL là giờ mặc định của máy). */
  private long vnAheadSeconds() {
    Instant now = clock.instant();
    return VoucherService.ZONE.getRules().getOffset(now).getTotalSeconds()
        - (long) ZoneId.systemDefault().getRules().getOffset(now).getTotalSeconds();
  }

  private CampaignResponse response(
      SegmentCampaign c, Outcome outcome, List<GroupBehavior> behaviors) {
    GroupBehavior treated = null;
    GroupBehavior held = null;
    for (GroupBehavior b : behaviors) {
      if (b.heldOut()) {
        held = b;
      } else {
        treated = b;
      }
    }
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
        discount == 0 ? null : Math.round(revenue * 10.0 / discount) / 10.0,
        c.getHoldoutPercent(),
        c.getHoldoutCount(),
        c.getMaxRecipients(),
        c.getGrossMarginPercent(),
        rate(treated),
        rate(held),
        rate(treated) == null || rate(held) == null
            ? null
            : Math.round((rate(treated) - rate(held)) * 10.0) / 10.0,
        incrementalProfit(treated, held, c.getGrossMarginPercent(), discount));
  }

  /** Tỷ lệ khách đã mua (%), làm tròn 1 chữ số; null khi nhóm không có khách. */
  private static Double rate(GroupBehavior group) {
    if (group == null || group.customers() == 0) {
      return null;
    }
    return Math.round(group.buyers() * 1000.0 / group.customers()) / 10.0;
  }

  /**
   * Lãi tăng thêm nhờ mã: tiền hàng của khách nhận mã trừ tiền hàng của khách bị giữ lại quy về
   * cùng số khách, nhân lãi gộp, trừ tiền đã giảm (rà soát 12/11/2026, P2).
   */
  static Long incrementalProfit(
      GroupBehavior treated, GroupBehavior held, Integer marginPercent, long discount) {
    if (treated == null || held == null || held.customers() == 0 || marginPercent == null) {
      return null;
    }
    double baseline = held.goods() * (double) treated.customers() / held.customers();
    return Math.round((treated.goods() - baseline) * marginPercent / 100.0 - discount);
  }

  private CustomerSegment segment(String code) {
    return customerSegmentRepository
        .findByCode(code)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy nhóm khách hàng"));
  }
}
