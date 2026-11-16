package com.szr.crosserp.identity.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class IdentitySecurityConfiguration {
    @Bean
    RSAKey signingKey(@Value("${app.auth.private-key}") Resource privateFile,
        @Value("${app.auth.public-key}") Resource publicFile,
        @Value("${app.auth.key-id}") String kid) throws Exception {
        try (var priv=privateFile.getInputStream(); var pub=publicFile.getInputStream()) {
            var privateKey=RsaKeyConverters.pkcs8().convert(priv);
            var publicKey=RsaKeyConverters.x509().convert(pub);
            if(privateKey==null || publicKey==null || publicKey.getModulus().bitLength()<2048
                || !privateKey.getModulus().equals(publicKey.getModulus()) || kid.isBlank())
                throw new IllegalStateException("Invalid RSA signing key configuration");
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(kid).build();
        }
    }
    @Bean
    JwtDecoder jwtDecoder(RSAKey key) throws Exception {
        var decoder=NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cross-border-erp"));
        return decoder;
    }
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8(); }
    @Bean SecurityFilterChain security(HttpSecurity http, com.fasterxml.jackson.databind.ObjectMapper mapper) throws Exception {
        return http.csrf(csrf->csrf.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.POST,
                "/api/auth/login","/api/auth/refresh","/api/auth/logout").permitAll().anyRequest().authenticated())
            .oauth2ResourceServer(r->r.jwt(j->{}).authenticationEntryPoint((req,res,error)->{
                res.setStatus(401);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
                mapper.writeValue(res.getWriter(),new com.szr.crosserp.identity.common.exception.ApiError(
                    "AUTH_UNAUTHORIZED","登录状态无效",java.time.Instant.now(),req.getRequestURI()));
            })).build();
    }
}
