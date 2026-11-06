package com.vigia.service;

import com.vigia.check.HealthCheck;
import com.vigia.incident.Incident;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ServiceDtos {

    public static final int DEFAULT_SLOW_THRESHOLD_MS = 1000;

    private ServiceDtos() {
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500)
            @Pattern(regexp = "^https?://\\S+$", message = "deve começar com http:// ou https://") String url,
            @NotNull @Min(30) @Max(86400) Integer intervalSeconds,
            Boolean active,
            Boolean publicPage,
            @Min(100) @Max(60000) Integer slowThresholdMs) {

        public boolean activeOrDefault() {
            return active == null || active;
        }

        public boolean publicPageOrDefault() {
            return publicPage != null && publicPage;
        }

        public int slowThresholdOrDefault() {
            return slowThresholdMs == null ? DEFAULT_SLOW_THRESHOLD_MS : slowThresholdMs;
        }
    }

    public record ServiceResponse(
            Long id,
            String name,
            String url,
            int intervalSeconds,
            boolean active,
            boolean publicPage,
            int slowThresholdMs,
            ServiceStatus lastStatus,
            Instant lastCheckedAt,
            Instant certExpiresAt,
            Instant createdAt) {

        public static ServiceResponse from(MonitoredService s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getUrl(), s.getIntervalSeconds(), s.isActive(),
                    s.isPublicPage(), s.getSlowThresholdMs(), s.getLastStatus(), s.getLastCheckedAt(),
                    s.getCertExpiresAt(), s.getCreatedAt());
        }
    }

    /** Ponto do gráfico: um por verificação. {@code slow} = respondeu, mas acima do limite. */
    public record CheckPoint(Instant checkedAt, boolean up, boolean slow, Long responseTimeMs) {

        public static CheckPoint from(HealthCheck c, MonitoredService s) {
            return new CheckPoint(c.getCheckedAt(), c.isUp(), s.isSlow(c.isUp(), c.getResponseTimeMs()),
                    c.getResponseTimeMs());
        }
    }

    /** Resumo de um dia (UTC) para a barra de 90 dias. Dias sem verificação não aparecem. */
    public record DailyStat(LocalDate day, long total, long up) {
    }

    /** Tudo que um card da página de status precisa, numa chamada só. */
    public record StatusCard(
            Long id,
            String name,
            String url,
            boolean active,
            boolean publicPage,
            int intervalSeconds,
            int slowThresholdMs,
            ServiceStatus lastStatus,
            Instant lastCheckedAt,
            Instant certExpiresAt,
            Double uptime24h,
            Double avgResponseTimeMs24h,
            Double uptime90d,
            List<DailyStat> daily,
            List<CheckPoint> recentChecks,
            List<IncidentView> incidents) {
    }

    /** Incidente para a página pública. {@code resolvedAt} nulo = em andamento. */
    public record IncidentView(Instant startedAt, Instant resolvedAt, String cause) {

        public static IncidentView from(Incident i) {
            return new IncidentView(i.getStartedAt(), i.getResolvedAt(), i.getCause());
        }
    }

    public record CheckResponse(
            Long id,
            Instant checkedAt,
            boolean up,
            Integer statusCode,
            Long responseTimeMs,
            String errorMessage) {

        public static CheckResponse from(HealthCheck c) {
            return new CheckResponse(c.getId(), c.getCheckedAt(), c.isUp(), c.getStatusCode(),
                    c.getResponseTimeMs(), c.getErrorMessage());
        }
    }

    public record UptimeResponse(
            Long serviceId,
            int periodHours,
            long totalChecks,
            long upChecks,
            Double uptimePercent,
            Double avgResponseTimeMs) {
    }
}
