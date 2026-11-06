package com.vigia.check;

import java.time.Instant;

/**
 * Resultado bruto de uma requisição HTTP a um serviço, antes de ser salvo.
 * {@code certExpiresAt} só vem preenchido em URLs HTTPS.
 */
public record CheckResult(Instant checkedAt, boolean up, Integer statusCode, Long responseTimeMs, String errorMessage,
                          Instant certExpiresAt) {

    public static CheckResult success(Instant at, int statusCode, long responseTimeMs, Instant certExpiresAt) {
        return new CheckResult(at, statusCode < 400, statusCode, responseTimeMs,
                statusCode < 400 ? null : "HTTP " + statusCode, certExpiresAt);
    }

    public static CheckResult failure(Instant at, Long responseTimeMs, String error) {
        return new CheckResult(at, false, null, responseTimeMs, error, null);
    }
}
