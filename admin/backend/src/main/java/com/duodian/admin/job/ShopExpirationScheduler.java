package com.duodian.admin.job;

import com.duodian.admin.service.ShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ShopExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ShopExpirationScheduler.class);

    private final ShopService shopService;

    public ShopExpirationScheduler(ShopService shopService) {
        this.shopService = shopService;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    public void refreshRemainingDaysDaily() {
        int updated = shopService.refreshRemainingDays();
        if (updated > 0) {
            log.info("Refreshed remaining days for {} shops", updated);
        }
    }
}
