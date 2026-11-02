package com.darmoz.ribero.dto.response;

import java.time.Instant;

public record ErrorResponse(Instant timestamp, int status, String error, String message) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(Instant.now(), status, error, message);
    }
}
