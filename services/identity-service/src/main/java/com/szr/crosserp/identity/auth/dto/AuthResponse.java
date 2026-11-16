package com.szr.crosserp.identity.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    CurrentUserResponse user
) {
}
