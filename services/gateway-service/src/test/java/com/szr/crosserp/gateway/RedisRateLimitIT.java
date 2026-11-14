package com.szr.crosserp.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.gateway.web.AuthRateLimitFilter;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RedisRateLimitIT {
    @Test
    void unavailableRedisAllowsRequestWithinBoundedTime() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 1);
        factory.afterPropertiesSet();
        factory.start();
        try {
            AuthRateLimitFilter filter = new AuthRateLimitFilter(new ReactiveStringRedisTemplate(factory),
                new ObjectMapper().findAndRegisterModules());
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/refresh"));
            filter.filter(exchange, current -> {
                current.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                return current.getResponse().setComplete();
            }).block(Duration.ofSeconds(3));
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        } finally {
            factory.destroy();
        }
    }
    @Test
    void realRedisCountsConcurrentRequestsAtomicallyAndExpiresKeys() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        factory.start();
        ReactiveStringRedisTemplate template = new ReactiveStringRedisTemplate(factory);
        String address = "127.0.0." + (10 + Math.floorMod(UUID.randomUUID().hashCode(), 200));
        try {
            AuthRateLimitFilter filter = new AuthRateLimitFilter(template, new ObjectMapper().findAndRegisterModules());
            List<HttpStatus> statuses = Flux.range(0, 40).flatMap(i -> {
                MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/auth/login")
                    .remoteAddress(new InetSocketAddress(address, 12345)).build());
                return filter.filter(exchange, current -> {
                    current.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                    return current.getResponse().setComplete();
                }).then(Mono.fromSupplier(() -> (HttpStatus) exchange.getResponse().getStatusCode()));
            }, 8).collectList().block(Duration.ofSeconds(15));
            assertThat(statuses).filteredOn(status -> status == HttpStatus.NO_CONTENT).hasSize(10);
            assertThat(statuses).filteredOn(status -> status == HttpStatus.TOO_MANY_REQUESTS).hasSize(30);
            List<String> keys = template.keys("cross-erp:auth-rate:login:*").collectList().block();
            assertThat(keys).isNotEmpty();
            for (String key : keys) {
                assertThat(key).doesNotContain(address);
                assertThat(template.getExpire(key).block()).isPositive().isLessThanOrEqualTo(Duration.ofSeconds(60));
            }
        } finally {
            factory.destroy();
        }
    }
}
