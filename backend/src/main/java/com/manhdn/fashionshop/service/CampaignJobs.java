package com.manhdn.fashionshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Việc chạy định kỳ của chương trình tự tặng mã cho khách mới vào nhóm (US-24), chỉ chạy khi bật
 * lịch (xem SchedulingConfig). Khách đổi nhóm khi ml-service xếp lại nhóm (sau đơn Đã giao, mỗi
 * đêm), nên quét mỗi phút là đủ gần thời gian thực.
 */
@Component
public class CampaignJobs {

  private static final Logger LOG = LoggerFactory.getLogger(CampaignJobs.class);

  private final CampaignService campaignService;

  /** Nhận CampaignService qua constructor. */
  public CampaignJobs(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

  /** Tặng mã cho khách vừa vào nhóm có chương trình tự tặng, mặc định mỗi phút. */
  @Scheduled(
      initialDelayString = "${app.campaign.auto-interval:1m}",
      fixedDelayString = "${app.campaign.auto-interval:1m}")
  public void grantAutoOffers() {
    int granted = campaignService.grantAutoOffers();
    if (granted > 0) {
      LOG.info("Tự tặng mã cho {} khách mới vào nhóm", granted);
    }
  }
}
