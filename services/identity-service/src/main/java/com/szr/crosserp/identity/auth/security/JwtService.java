package com.szr.crosserp.identity.auth.security;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.szr.crosserp.identity.auth.config.AuthProperties;
import com.szr.crosserp.identity.user.model.User;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final AuthProperties properties;
    private JwtEncoder encoder;

    private final RSAKey key;
    public JwtService(AuthProperties properties, RSAKey key) {
        this.properties = properties;
        this.key = key;
    }

    @PostConstruct
    void initialize() {
        encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    public String issue(User user, UUID sessionId, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("cross-border-erp")
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(user.getId().toString())
            .claim("sid", sessionId.toString())
            .claim("roles", user.getRoles().stream().map(role -> role.getCode()).sorted().toList())
            .build();
        JwsHeader header = JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).keyId(key.getKeyID()).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expiresInSeconds() {
        return properties.accessTokenMinutes() * 60;
    }
}
