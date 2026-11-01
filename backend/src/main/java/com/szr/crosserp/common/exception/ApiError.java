package com.szr.crosserp.common.exception;

import java.time.Instant;

public record ApiError(String code, String message, Instant timestamp, String path) {
}

