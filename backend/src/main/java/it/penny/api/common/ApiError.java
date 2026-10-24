package it.penny.api.common;

import java.time.Instant;

/** Stable public error envelope. Do not expose stack traces or internal exception messages. */
public record ApiError(String code, String message, Instant timestamp) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, Instant.now());
    }
}
