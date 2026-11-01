package com.szr.crosserp.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    String jwtSecret,
    long accessTokenMinutes,
    long refreshTokenDays,
    int maxLoginFailures,
    long lockMinutes,
    String refreshCookieName,
    boolean secureCookie
) {
}

