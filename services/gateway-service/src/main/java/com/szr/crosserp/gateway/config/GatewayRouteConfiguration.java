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
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder, GatewayRouteProperties properties) {
        return builder.routes()
            .route("legacy-api", route -> route
                .path("/api/**")
                .uri(properties.legacyBackendUri()))
            .build();
    }
}
