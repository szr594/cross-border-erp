package com.szr.crosserp.orderservice.common.exception;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp, String path) {
}
