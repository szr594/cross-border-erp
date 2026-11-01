package com.szr.crosserp.auth.service;

import com.szr.crosserp.auth.config.AuthProperties;
import com.szr.crosserp.auth.dto.AuthResponse;
import com.szr.crosserp.auth.dto.CurrentUserResponse;
import com.szr.crosserp.auth.model.RefreshSession;
import com.szr.crosserp.auth.repository.RefreshSessionRepository;
import com.szr.crosserp.auth.security.JwtService;
import com.szr.crosserp.common.exception.ApiException;
import com.szr.crosserp.user.model.User;
import com.szr.crosserp.user.model.UserStatus;
import com.szr.crosserp.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
        UserRepository userRepository,
        RefreshSessionRepository sessionRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        AuthProperties properties,
        Clock clock
    ) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String loginName, String password, String ipAddress, String userAgent) {
        Instant now = clock.instant();
        User user = userRepository.findByLoginNameIgnoreCase(loginName.trim()).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            throw invalidCredentials();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw invalidCredentials();
        }
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.LOCKED, "AUTH_ACCOUNT_LOCKED", "账号已临时锁定，请稍后重试");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailedLogin(
                properties.maxLoginFailures(),
                now.plus(properties.lockMinutes(), ChronoUnit.MINUTES)
            );
            userRepository.save(user);
            throw invalidCredentials();
        }
        user.recordSuccessfulLogin(now);
        userRepository.save(user);
        return createSession(user, now, ipAddress, userAgent);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawToken, String ipAddress, String userAgent) {
        Instant now = clock.instant();
        RefreshSession current = sessionRepository.findByTokenHash(hash(rawToken))
            .orElseThrow(this::invalidRefreshToken);
        if (current.isRevoked()) {
            sessionRepository.revokeAllForUser(current.getUser().getId(), now);
            throw invalidRefreshToken();
        }
        if (!current.getExpiresAt().isAfter(now) || current.getUser().getStatus() != UserStatus.ACTIVE) {
            current.revoke(now);
            throw invalidRefreshToken();
        }

        String replacementToken = randomToken();
        UUID replacementId = UUID.randomUUID();
        RefreshSession replacement = new RefreshSession(
            replacementId,
            current.getUser(),
            hash(replacementToken),
            now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS),
            now,
            ipAddress,
            limit(userAgent, 512)
        );
        current.rotateTo(replacementId, now);
        sessionRepository.save(current);
        sessionRepository.save(replacement);
        return responseFor(current.getUser(), replacementId, replacementToken, now);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        sessionRepository.findByTokenHash(hash(rawToken)).ifPresent(session -> session.revoke(now));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse currentUser(Long userId) {
        User user = userRepository.findById(userId)
            .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "登录状态无效"));
        return CurrentUserResponse.from(user);
    }

    private AuthResult createSession(User user, Instant now, String ipAddress, String userAgent) {
        String refreshToken = randomToken();
        UUID sessionId = UUID.randomUUID();
        sessionRepository.save(new RefreshSession(
            sessionId,
            user,
            hash(refreshToken),
            now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS),
            now,
            ipAddress,
            limit(userAgent, 512)
        ));
        return responseFor(user, sessionId, refreshToken, now);
    }

    private AuthResult responseFor(User user, UUID sessionId, String refreshToken, Instant now) {
        AuthResponse response = new AuthResponse(
            jwtService.issue(user, sessionId, now),
            "Bearer",
            jwtService.expiresInSeconds(),
            CurrentUserResponse.from(user)
        );
        return new AuthResult(response, refreshToken);
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "登录名或密码错误");
    }

    private ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_INVALID", "登录状态已失效，请重新登录");
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
