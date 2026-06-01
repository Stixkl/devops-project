package com.circleguard.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth")
public class NotificationProperties {

    private final Api api = new Api();

    @Getter
    @Setter
    public static class Api {
        private String url = "http://circleguard-auth-service:8080";
    }
}
