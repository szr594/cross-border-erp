package com.szr.crosserp.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "app.auth.jwt-secret=gateway-test-secret-at-least-32-bytes-long"
    }
)
class GatewaySecurityIntegrationTest {

    private static final String TEST_SECRET = "gateway-test-secret-at-least-32-bytes-long";
    private static DisposableServer legacyBackend;

    @DynamicPropertySource
    static void gatewayProperties(DynamicPropertyRegistry registry) {
        legacyBackend = HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle((request, response) -> {
                String requestId = request.requestHeaders().get("X-Request-Id");
                String userId = request.requestHeaders().get("X-User-Id");
                String roles = request.requestHeaders().get("X-User-Roles");
                String internalService = request.requestHeaders().get("X-Internal-Service");
                String forwardedFor = request.requestHeaders().get("X-Forwarded-For");
                String body = responseBody(requestId, userId, roles, internalService, forwardedFor);
                return response
                    .status(200)
                    .header(HttpHeaderNames.CONTENT_TYPE, "application/json")
                    .sendString(Mono.just(body));
            })
            .bindNow();
        registry.add(
            "app.gateway.legacy-backend-uri",
            () -> "http://127.0.0.1:" + legacyBackend.port()
        );
    }

    @AfterAll
    static void stopLegacyBackend() {
        if (legacyBackend != null) {
            legacyBackend.disposeNow();
        }
    }

    @Autowired
    private WebTestClient client;

    @Test
    void healthEndpointIsPublicAndIncludesRequestId() {
        client.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().value("X-Request-Id", value -> assertThat(value).isNotBlank())
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void protectedApiRequiresJwt() {
        client.get()
            .uri("/api/orders")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType("application/json")
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_UNAUTHORIZED")
            .jsonPath("$.path").isEqualTo("/api/orders")
            .jsonPath("$.requestId").isNotEmpty();
    }

    @Test
    void malformedJwtIsRejected() {
        client.get()
            .uri("/api/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    void expiredJwtIsRejected() {
        Instant now = Instant.now();
        String token = accessToken(TEST_SECRET, now.minus(20, ChronoUnit.MINUTES), now.minus(5, ChronoUnit.MINUTES));
        client.get()
            .uri("/api/orders")
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    void jwtSignedWithAnotherSecretIsRejected() {
        Instant now = Instant.now();
        String token = accessToken(
            "another-gateway-test-secret-at-least-32-bytes",
            now,
            now.plus(15, ChronoUnit.MINUTES)
        );
        client.get()
            .uri("/api/orders")
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("AUTH_UNAUTHORIZED");
    }

    @Test
    void publicAuthenticationPathIsForwardedAndUntrustedHeadersAreRemoved() {
        client.post()
            .uri("/api/auth/login")
            .header("X-Request-Id", "invalid-request-id")
            .header("X-User-Id", "forged-user")
            .header("X-User-Roles", "OWNER")
            .header("X-Internal-Service", "forged-service")
            .header("X-Forwarded-For", "203.0.113.9")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.requestId").isNotEmpty()
            .jsonPath("$.userId").doesNotExist()
            .jsonPath("$.roles").doesNotExist()
            .jsonPath("$.internalService").doesNotExist()
            .jsonPath("$.forwardedFor").value(value ->
                assertThat(String.valueOf(value)).doesNotContain("203.0.113.9")
            );
    }

    @Test
    void validJwtAndRequestIdAreForwarded() {
        String requestId = UUID.randomUUID().toString();
        client.get()
            .uri("/api/orders")
            .headers(headers -> {
                headers.setBearerAuth(accessToken());
                headers.set("X-Request-Id", requestId);
            })
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals("X-Request-Id", requestId)
            .expectBody()
            .jsonPath("$.requestId").isEqualTo(requestId);
    }

    private String accessToken() {
        Instant now = Instant.now();
        return accessToken(TEST_SECRET, now, now.plus(15, ChronoUnit.MINUTES));
    }

    private String accessToken(String signingSecret, Instant issuedAt, Instant expiresAt) {
        byte[] secret = signingSecret.getBytes(StandardCharsets.UTF_8);
        SecretKey key = new SecretKeySpec(secret, "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("cross-border-erp")
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject("1")
            .claim("roles", java.util.List.of("OWNER"))
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String responseBody(
        String requestId,
        String userId,
        String roles,
        String internalService,
        String forwardedFor
    ) {
        StringBuilder body = new StringBuilder("{");
        appendField(body, "requestId", requestId);
        if (userId != null) {
            appendField(body, "userId", userId);
        }
        if (roles != null) {
            appendField(body, "roles", roles);
        }
        if (internalService != null) {
            appendField(body, "internalService", internalService);
        }
        appendField(body, "forwardedFor", forwardedFor == null ? "" : forwardedFor);
        return body.append('}').toString();
    }

    private static void appendField(StringBuilder body, String name, String value) {
        if (body.length() > 1) {
            body.append(',');
        }
        body.append('"').append(name).append("\":\"").append(value).append('"');
    }
}
