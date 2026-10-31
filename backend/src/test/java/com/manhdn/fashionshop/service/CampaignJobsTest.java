package com.manhdn.fashionshop.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class CampaignJobsTest {

  @Test
  void eachRunGrantsAutoOffers() {
    CampaignService service = mock(CampaignService.class);
    when(service.grantAutoOffers()).thenReturn(2, 0);
    CampaignJobs jobs = new CampaignJobs(service);

    jobs.grantAutoOffers();
    jobs.grantAutoOffers();

    verify(service, times(2)).grantAutoOffers();
  }
}
