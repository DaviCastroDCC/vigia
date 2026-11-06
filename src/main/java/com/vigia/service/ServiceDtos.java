package com.vigia.service;

import com.vigia.check.HealthCheck;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ServiceDtos {

    private ServiceDtos() {
    }

    public record ServiceRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 500)
            @Pattern(regexp = "^https?://\\S+$", message = "deve começar com http:// ou https://") String url,
            @NotNull @Min(30) @Max(86400) Integer intervalSeconds,
            Boolean active,
            Boolean publicPage) {

        public boolean activeOrDefault() {
            return active == null || active;
        }

        public boolean publicPageOrDefault() {
            return publicPage != null && publicPage;
        }
    }

    public record ServiceResponse(
            Long id,
            String name,
            String url,
            int intervalSeconds,
            boolean active,
            boolean publicPage,
            ServiceStatus lastStatus,
            Instant lastCheckedAt,
            Instant createdAt) {

        public static ServiceResponse from(MonitoredService s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getUrl(), s.getIntervalSeconds(), s.isActive(),
                    s.isPublicPage(), s.getLastStatus(), s.getLastCheckedAt(), s.getCreatedAt());
        }
    }

    /** Ponto do gráfico: um por verificação. */
    public record CheckPoint(Instant checkedAt, boolean up, Long responseTimeMs) {

        public static CheckPoint from(HealthCheck c) {
            return new CheckPoint(c.getCheckedAt(), c.isUp(), c.getResponseTimeMs());
        }
    }

    /** Tudo que um card da página de status precisa, numa chamada só. */
    public record StatusCard(
            Long id,
            String name,
            String url,
            boolean active,
            boolean publicPage,
            int intervalSeconds,
            ServiceStatus lastStatus,
            Instant lastCheckedAt,
            Double uptime24h,
            Double avgResponseTimeMs24h,
            List<CheckPoint> recentChecks) {
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
