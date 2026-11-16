package com.szr.crosserp.identity.auth.security;

import com.nimbusds.jose.jwk.RSAKey;
import com.szr.crosserp.identity.auth.config.AuthProperties;
import com.szr.crosserp.identity.user.model.User;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtServiceTest {
    @Test
    void signsRs256WithKidAndRejectsWrongKeyAndExpiredToken() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair=generator.generateKeyPair();
        var key=new RSAKey.Builder((RSAPublicKey)pair.getPublic())
            .privateKey((RSAPrivateKey)pair.getPrivate()).keyID("identity-test").build();
        var properties=new AuthProperties(null,15,7,5,15,"cross_erp_refresh",true);
        var service=new JwtService(properties,key); service.initialize();
        User user=mock(User.class);
        when(user.getId()).thenReturn(42L); when(user.getRoles()).thenReturn(Set.of());
        var decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)pair.getPublic()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cross-border-erp"));
        var sid=UUID.randomUUID(); var now=Instant.now();
        var token=service.issue(user,sid,now); var jwt=decoder.decode(token);
        assertThat(jwt.getHeaders()).containsEntry("alg","RS256").containsEntry("kid","identity-test");
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sid.toString());
        assertThat(jwt.getExpiresAt().getEpochSecond()-jwt.getIssuedAt().getEpochSecond()).isEqualTo(900);
        var wrong=NimbusJwtDecoder.withPublicKey((RSAPublicKey)generator.generateKeyPair().getPublic()).build();
        assertThatThrownBy(()->wrong.decode(token)).isInstanceOf(JwtException.class);
        var expired=service.issue(user,sid,now.minusSeconds(1800));
        assertThatThrownBy(()->decoder.decode(expired)).isInstanceOf(JwtException.class);
    }
}
