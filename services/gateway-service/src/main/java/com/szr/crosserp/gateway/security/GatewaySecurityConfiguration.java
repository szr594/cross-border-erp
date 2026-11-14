package com.szr.crosserp.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.gateway.config.GatewayAuthProperties;
import com.szr.crosserp.gateway.web.RequestContextWebFilter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(GatewayAuthProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ObjectMapper objectMapper) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .authorizeExchange(authorize -> authorize
                .pathMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                .permitAll()
                .pathMatchers("/actuator/health", "/actuator/health/**")
                .permitAll()
                .anyExchange()
                .authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((exchange, exception) -> writeError(
                    exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "登录状态无效"
                ))
                .accessDeniedHandler((exchange, exception) -> writeError(
                    exchange, objectMapper, HttpStatus.FORBIDDEN,
                    "AUTH_FORBIDDEN", "没有权限执行该操作"
                ))
            )
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(Customizer.withDefaults())
                .authenticationEntryPoint((exchange, exception) -> writeError(
                    exchange, objectMapper, HttpStatus.UNAUTHORIZED,
                    "AUTH_UNAUTHORIZED", "登录状态无效"
                ))
                .accessDeniedHandler((exchange, exception) -> writeError(
                    exchange, objectMapper, HttpStatus.FORBIDDEN,
                    "AUTH_FORBIDDEN", "没有权限执行该操作"
                ))
            )
            .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(GatewayAuthProperties properties) {
        byte[] secret = properties.jwtSecret() == null
            ? new byte[0]
            : properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder
            .withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cross-border-erp"));
        return decoder;
    }

    private Mono<Void> writeError(
        ServerWebExchange exchange,
        ObjectMapper objectMapper,
        HttpStatus status,
        String code,
        String message
    ) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.empty();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        GatewayErrorResponse error = new GatewayErrorResponse(
            code,
            message,
            Instant.now(),
            exchange.getRequest().getPath().value(),
            exchange.getRequest().getHeaders().getFirst(RequestContextWebFilter.REQUEST_ID_HEADER)
        );
        try {
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(objectMapper.writeValueAsBytes(error));
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return exchange.getResponse().setComplete();
        }
    }
}
