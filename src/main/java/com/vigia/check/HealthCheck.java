package com.vigia.check;

import com.vigia.service.MonitoredService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "health_checks")
public class HealthCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean up;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected HealthCheck() {
    }

    public HealthCheck(MonitoredService service, CheckResult result) {
        this.service = service;
        this.checkedAt = result.checkedAt();
        this.up = result.up();
        this.statusCode = result.statusCode();
        this.responseTimeMs = result.responseTimeMs();
        this.errorMessage = result.errorMessage() == null ? null
                : result.errorMessage().substring(0, Math.min(500, result.errorMessage().length()));
    }

    public Long getId() {
        return id;
    }

    public MonitoredService getService() {
        return service;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    public boolean isUp() {
        return up;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
