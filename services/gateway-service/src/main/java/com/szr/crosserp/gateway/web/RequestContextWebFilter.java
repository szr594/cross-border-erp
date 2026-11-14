package com.szr.crosserp.gateway.web;

import java.util.List;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestContextWebFilter implements WebFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private static final List<String> UNTRUSTED_INTERNAL_HEADERS = List.of(
        "X-User-Id",
        "X-User-Roles",
        "X-Authenticated-User",
        "X-Internal-Service",
        "X-Service-Token",
        "Forwarded",
        "X-Forwarded-For",
        "X-Forwarded-Host",
        "X-Forwarded-Port",
        "X-Forwarded-Proto",
        "X-Forwarded-Prefix"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = validRequestId(exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER));
        String peer = exchange.getRequest().getRemoteAddress() == null ? "unknown"
            : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        String trusted = System.getenv("TRUSTED_PROXY_IP");
        String client = peer;
        if (peer.equals(trusted) && forwarded != null
            && (forwarded.matches("[0-9.]+") || forwarded.matches("[0-9a-fA-F:]+"))) client = forwarded;
        exchange.getAttributes().put("clientIp", client);
        ServerHttpRequest request = exchange.getRequest().mutate()
            .headers(headers -> {
                headers.set(REQUEST_ID_HEADER, requestId);
                UNTRUSTED_INTERNAL_HEADERS.forEach(headers::remove);
                headers.remove("X-Real-IP");
            })
            .build();
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String validRequestId(String candidate) {
        if (candidate != null) {
            try {
                return UUID.fromString(candidate).toString();
            } catch (IllegalArgumentException ignored) {
                // Replace malformed external values instead of forwarding them into logs and downstream services.
            }
        }
        return UUID.randomUUID().toString();
    }
}
