package com.szr.crosserp.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record GatewayAuthProperties(String jwtSecret) {
}
