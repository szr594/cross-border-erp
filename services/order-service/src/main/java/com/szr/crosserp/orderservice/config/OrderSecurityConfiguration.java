package com.szr.crosserp.orderservice.config;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szr.crosserp.orderservice.common.exception.ApiError;
import java.time.Instant;

@Configuration
public class OrderSecurityConfiguration {
    @Bean java.time.Clock clock() { return java.time.Clock.systemUTC(); }
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.auth.public-key}") Resource resource) throws Exception {
        try(var input=resource.getInputStream()) {
            var key=RsaKeyConverters.x509().convert(input);
            if(key==null || key.getModulus().bitLength()<2048) throw new IllegalStateException("Invalid RSA public key");
            var decoder=NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("cross-border-erp"));return decoder;
        }
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http,ObjectMapper mapper) throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().authenticated()).oauth2ResourceServer(r->r.jwt(j->{}).authenticationEntryPoint((req,res,e)->{
                res.setStatus(401);res.setContentType("application/json");res.setCharacterEncoding("UTF-8");
                mapper.writeValue(res.getWriter(),new ApiError("AUTH_UNAUTHORIZED","登录状态无效",Instant.now(),req.getRequestURI()));
            })).build();
    }
}
