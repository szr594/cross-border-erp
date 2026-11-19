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
        @org.springframework.beans.factory.annotation.Value("${IDENTITY_SERVICE_URL:}") String identityUrl,
        @org.springframework.beans.factory.annotation.Value("${ORDER_SERVICE_URL:}") String orderUrl,
        @org.springframework.beans.factory.annotation.Value("${PROCUREMENT_SERVICE_URL:}") String procurementUrl) {
        var routes = builder.routes();
        if (!identityUrl.isBlank()) {
            var uri = java.net.URI.create(identityUrl);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid identity service URL");
            routes.route("identity-api", route -> route.path("/api/auth/**").uri(uri));
        }
        if (!orderUrl.isBlank()) {
            var uri = java.net.URI.create(orderUrl);
            if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException("Invalid order service URL");
            // Warehouse-owned coverage remains in backend even after the order cutover.
            routes.route("order-inventory-coverage", route -> route.path("/api/orders/*/inventory-allocations")
                .uri(properties.legacyBackendUri()));
            routes.route("order-api", route -> route.path("/api/orders", "/api/orders/**", "/api/order-imports", "/api/order-imports/**").uri(uri));
        }
        if (!procurementUrl.isBlank()) {
            var uri=java.net.URI.create(procurementUrl);
            if(!java.util.Set.of("http","https").contains(uri.getScheme()) || uri.getHost()==null)throw new IllegalArgumentException("Invalid procurement service URL");
            routes.route("procurement-api",route->route.path("/api/suppliers","/api/suppliers/**","/api/purchase-orders","/api/purchase-orders/**").uri(uri));
        }
        return routes
            .route("legacy-api", route -> route
                .path("/api/**")
                .uri(properties.legacyBackendUri()))
            .build();
    }
    RouteLocator gatewayRoutes(RouteLocatorBuilder builder,GatewayRouteProperties properties,String identityUrl,String orderUrl){
        return gatewayRoutes(builder,properties,identityUrl,orderUrl,"");
    }

}
