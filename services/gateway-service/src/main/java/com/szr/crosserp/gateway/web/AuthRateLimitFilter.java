package com.szr.crosserp.gateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.gateway.security.GatewayErrorResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class AuthRateLimitFilter implements WebFilter, Ordered {
    private static final RedisScript<Long> COUNTER = RedisScript.of(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public AuthRateLimitFilter(ReactiveStringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        int limit = "/api/auth/login".equals(path) ? 10 : "/api/auth/refresh".equals(path) ? 30 : 0;
        if (limit == 0 || exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }
        String address = exchange.getRequest().getRemoteAddress() == null ? "unknown"
            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        address = exchange.getAttributeOrDefault("clientIp", address);
        String key = "cross-erp:auth-rate:" + path.substring(path.lastIndexOf('/') + 1) + ":" + digest(address);
        return redis.execute(COUNTER, List.of(key)).single()
            .timeout(Duration.ofMillis(750))
            .onErrorReturn(0L)
            .flatMap(count -> count > limit ? reject(exchange) : chain.filter(exchange));
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        return Mono.defer(() -> {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().set("Retry-After", "60");
            GatewayErrorResponse error = new GatewayErrorResponse("AUTH_RATE_LIMITED", "请求过于频繁，请稍后重试",
                Instant.now(), exchange.getRequest().getPath().value(),
                exchange.getRequest().getHeaders().getFirst(RequestContextWebFilter.REQUEST_ID_HEADER));
            try {
                return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory()
                    .wrap(mapper.writeValueAsBytes(error))));
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                return Mono.error(exception);
            }
        });
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }
}
