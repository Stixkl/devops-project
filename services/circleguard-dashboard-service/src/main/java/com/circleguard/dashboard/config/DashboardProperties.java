package com.circleguard.dashboard.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "circleguard")
public class DashboardProperties {

    private final PromotionService promotionService = new PromotionService();
    private final Features features = new Features();

    @Getter
    @Setter
    public static class PromotionService {
        private String url = "http://localhost:8088";
    }

    @Getter
    @Setter
    public static class Features {
        private boolean departmentStatsEnabled = true;
    }
}
