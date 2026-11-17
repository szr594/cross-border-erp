package com.szr.crosserp.integration.orders;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
@Configuration
public class OrderCallbackSecurity {
    @Bean @Order(0)
    SecurityFilterChain orderCallbacks(HttpSecurity http,@Value("${ORDER_INTERNAL_SERVICE_TOKEN:}")String token) throws Exception {
        var filter=new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws java.io.IOException,ServletException {
                String supplied=request.getHeader("X-Service-Token");
                if(token.getBytes(StandardCharsets.UTF_8).length<32||supplied==null||!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))){response.setStatus(401);response.setContentType("application/json");response.getWriter().write("{\"code\":\"INTERNAL_UNAUTHORIZED\"}");return;}
                chain.doFilter(request,response);
            }
        };
        return http.securityMatcher("/internal/orders/**").csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->a.anyRequest().permitAll()).addFilterBefore(filter,org.springframework.security.web.access.intercept.AuthorizationFilter.class).build();
    }
}
