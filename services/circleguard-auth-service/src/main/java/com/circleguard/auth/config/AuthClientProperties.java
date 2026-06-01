package com.circleguard.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "circleguard")
public class AuthClientProperties {

    private final IdentityService identityService = new IdentityService();

    @Getter
    @Setter
    public static class IdentityService {
        private String url = "http://localhost:8083";
    }
}
