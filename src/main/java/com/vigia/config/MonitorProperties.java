package com.vigia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(Jwt jwt, Checker checker) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Checker(long tickMs, int timeoutSeconds, boolean blockPrivateAddresses) {
    }
}
