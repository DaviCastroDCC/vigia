package com.vigia.incident;

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

import java.time.Duration;
import java.time.Instant;

/** Período em que um serviço ficou fora do ar. {@code resolvedAt} nulo = ainda em andamento. */
@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private MonitoredService service;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 500)
    private String cause;

    protected Incident() {
    }

    public Incident(MonitoredService service, Instant startedAt, String cause) {
        this.service = service;
        this.startedAt = startedAt;
        this.cause = cause == null ? null : cause.substring(0, Math.min(500, cause.length()));
    }

    public void resolve(Instant at) {
        this.resolvedAt = at;
    }

    public Duration duration(Instant now) {
        return Duration.between(startedAt, resolvedAt != null ? resolvedAt : now);
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getCause() {
        return cause;
    }
}
