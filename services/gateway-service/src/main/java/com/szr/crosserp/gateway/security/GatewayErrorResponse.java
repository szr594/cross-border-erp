package com.szr.crosserp.gateway.security;

import java.time.Instant;

public record GatewayErrorResponse(
    String code,
    String message,
    Instant timestamp,
    String path,
    String requestId
) {
}
