package com.manhdn.fashionshop.config;

import com.manhdn.fashionshop.service.CatalogSeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Lúc khởi động, nạp danh mục sản phẩm mẫu từ dữ liệu Tiki nếu cửa hàng chưa có sản phẩm nào, để
 * máy dev, Docker Compose và máy chủ dùng thử đều có sẵn hàng mà không phải nhập tay. Tắt bằng
 * {@code CATALOG_SEED_ENABLED=false}; test tắt sẵn trong cấu hình riêng của test.
 */
@Component
public class CatalogSeedInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CatalogSeedInitializer.class);

  private final CatalogSeedService catalogSeedService;
  private final boolean enabled;
  private final Resource location;

  /** Nhận service nạp dữ liệu, cờ bật tắt và vị trí file JSON từ cấu hình. */
  public CatalogSeedInitializer(
      CatalogSeedService catalogSeedService,
      @Value("${app.catalog-seed.enabled:true}") boolean enabled,
      @Value("${app.catalog-seed.location:classpath:seed/tiki-catalog.json}") Resource location) {
    this.catalogSeedService = catalogSeedService;
    this.enabled = enabled;
    this.location = location;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      return;
    }
    int count = catalogSeedService.seedIfEmpty(location);
    if (count > 0) {
      log.info("Đã nạp {} sản phẩm mẫu từ {}", count, location.getDescription());
    }
  }
}
