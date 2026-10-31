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
import com.manhdn.fashionshop.repository.SegmentProfileRepository;
import com.manhdn.fashionshop.repository.VoucherRecipientRepository;
import java.time.Clock;
import java.time.LocalDateTime;
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
  private final VoucherService voucherService;
  private final Clock clock;

  /** Nhận các repository, service và đồng hồ qua constructor. */
  public CampaignService(
      CustomerSegmentRepository customerSegmentRepository,
      SegmentProfileRepository profileRepository,
      SegmentProfileService segmentProfileService,
      SegmentCampaignRepository campaignRepository,
      VoucherRecipientRepository recipientRepository,
      VoucherService voucherService,
      Clock clock) {
    this.customerSegmentRepository = customerSegmentRepository;
    this.profileRepository = profileRepository;
    this.segmentProfileService = segmentProfileService;
    this.campaignRepository = campaignRepository;
    this.recipientRepository = recipientRepository;
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
   * @throws ConflictException nếu không có khách nào để tặng hoặc trùng mã
   * @throws InvalidRequestException nếu điều kiện của mã mâu thuẫn
   */
  @Transactional
  public CampaignResponse create(String segmentCode, CampaignRequest request) {
    CustomerSegment segment = segment(segmentCode);
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
