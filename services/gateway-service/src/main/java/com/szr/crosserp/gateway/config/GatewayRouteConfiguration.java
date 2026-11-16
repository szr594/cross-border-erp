package com.szr.crosserp.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayRouteProperties.class)
public class GatewayRouteConfiguration {

    @Bean
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRouteProperties properties,
        @org.springframework.beans.factory.annotation.Value("${IDENTITY_SERVICE_URL:}") String identityUrl) {
        var routes = builder.routes();
        if (!identityUrl.isBlank()) {
            var uri = java.net.URI.create(identityUrl);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid identity service URL");
            routes.route("identity-api", route -> route.path("/api/auth/**").uri(uri));
        }
        return routes
            .route("legacy-api", route -> route
                .path("/api/**")
                .uri(properties.legacyBackendUri()))
            .build();
    }
}
