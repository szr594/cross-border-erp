package com.szr.crosserp.orderservice.config;

import com.szr.crosserp.orderservice.user.UserDirectorySynchronization;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UserDirectoryWebConfiguration implements WebMvcConfigurer {
    private final UserDirectorySynchronization directory;
    public UserDirectoryWebConfiguration(UserDirectorySynchronization directory) { this.directory=directory; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                directory.refresh();
                return true;
            }
        }).addPathPatterns("/api/orders", "/api/orders/**", "/api/order-imports/**");
    }
}
