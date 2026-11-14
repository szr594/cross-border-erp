package com.szr.crosserp.gateway.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.gateway")
public record GatewayRouteProperties(URI legacyBackendUri) {

    public GatewayRouteProperties {
        if (legacyBackendUri == null) {
            throw new IllegalArgumentException("app.gateway.legacy-backend-uri is required");
        }
        String scheme = legacyBackendUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("app.gateway.legacy-backend-uri must use http or https");
        }
    }
}
