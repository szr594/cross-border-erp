package com.szr.crosserp.auth.service;

import com.szr.crosserp.auth.dto.AuthResponse;

public record AuthResult(AuthResponse response, String refreshToken) {
}

