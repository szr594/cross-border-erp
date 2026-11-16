package com.szr.crosserp.identity.auth.service;

import com.szr.crosserp.identity.auth.dto.AuthResponse;

public record AuthResult(AuthResponse response, String refreshToken) {
}
