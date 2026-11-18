package com.szr.crosserp.gateway.config;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import static org.assertj.core.api.Assertions.*;

class OrderRouteTest {
    private String destination(String path,String orderUrl) {
        var context=new StaticApplicationContext(); context.getBeanFactory().registerSingleton("pathPredicate", new org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory());
        var locator=new GatewayRouteConfiguration().gatewayRoutes(new RouteLocatorBuilder(context),
            new GatewayRouteProperties(URI.create("http://backend:8090")),"http://identity:8081",orderUrl);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get(path));
        return locator.getRoutes().filterWhen(route->route.getPredicate().apply(exchange)).next()
            .map(route->route.getUri().toString()).block(Duration.ofSeconds(5));
    }
    @Test void cutoverPreservesWarehouseOwnershipAndRoutesAllOrderOperationsTogether() {
        for(String path:java.util.List.of("/api/orders","/api/orders/42","/api/orders/42/cancel","/api/order-imports/preview"))
            assertThat(destination(path,"http://order:8082")).isEqualTo("http://order:8082");
        for(String path:java.util.List.of("/api/orders/42/inventory-allocations","/api/purchase-orders","/api/inventory/lots"))
            assertThat(destination(path,"http://order:8082")).isEqualTo("http://backend:8090");
        assertThat(destination("/api/auth/me","http://order:8082")).isEqualTo("http://identity:8081");
    }
    @Test void unsetOrderUrlKeepsLegacyRoutes() {
        assertThat(destination("/api/orders","")).isEqualTo("http://backend:8090");
    }
    @Test void invalidOrderUrlFailsBeforeServingTraffic() {
        assertThatThrownBy(()->destination("/api/orders","file:/tmp/order")).isInstanceOf(IllegalArgumentException.class);
    }
}
