package com.szr.crosserp.auth.controller;

import com.szr.crosserp.auth.config.AuthProperties;
import com.szr.crosserp.auth.dto.AuthResponse;
import com.szr.crosserp.auth.dto.CurrentUserResponse;
import com.szr.crosserp.auth.dto.LoginRequest;
import com.szr.crosserp.auth.service.AuthResult;
import com.szr.crosserp.auth.service.AuthService;
import com.szr.crosserp.common.exception.ApiException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthProperties properties;

    public AuthController(AuthService authService, AuthProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResult result = authService.login(
            request.loginName(), request.password(), servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")
        );
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = findCookie(request, properties.refreshCookieName());
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_MISSING", "登录状态已失效，请重新登录");
        }
        return withRefreshCookie(authService.refresh(
            refreshToken, request.getRemoteAddr(), request.getHeader("User-Agent")
        ));
    }

    @GetMapping("/me")
    CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        try {
            return authService.currentUser(Long.valueOf(jwt.getSubject()));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效");
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = findCookie(request, properties.refreshCookieName());
        authService.logout(refreshToken);
        ResponseCookie expired = ResponseCookie.from(properties.refreshCookieName(), "")
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expired.toString()).build();
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResult result) {
        ResponseCookie cookie = ResponseCookie.from(properties.refreshCookieName(), result.refreshToken())
            .httpOnly(true)
            .secure(properties.secureCookie())
            .sameSite("Strict")
            .path("/api/auth")
            .maxAge(Duration.ofDays(properties.refreshTokenDays()))
            .build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(result.response());
    }

    private String findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
