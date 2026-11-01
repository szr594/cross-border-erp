package com.szr.crosserp.auth.dto;

public record AuthResponse(
    String accessToken,
    String tokenType,
    long expiresIn,
    CurrentUserResponse user
) {
}

