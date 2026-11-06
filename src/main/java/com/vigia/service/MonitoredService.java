package com.vigia.service;

import com.vigia.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "monitored_services")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "interval_seconds", nullable = false)
    private int intervalSeconds;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "public_page", nullable = false)
    private boolean publicPage;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", length = 10)
    private ServiceStatus lastStatus;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "slow_threshold_ms", nullable = false)
    private int slowThresholdMs;

    @Column(name = "cert_expires_at")
    private Instant certExpiresAt;

    protected MonitoredService() {
    }

    public MonitoredService(User owner, String name, String url, int intervalSeconds, boolean active,
                            boolean publicPage, int slowThresholdMs) {
        this.owner = owner;
        this.createdAt = Instant.now();
        update(name, url, intervalSeconds, active, publicPage, slowThresholdMs);
    }

    public void update(String name, String url, int intervalSeconds, boolean active, boolean publicPage,
                       int slowThresholdMs) {
        if (!url.equals(this.url)) {
            this.certExpiresAt = null; // o certificado salvo era de outra URL
        }
        this.name = name;
        this.url = url;
        this.intervalSeconds = intervalSeconds;
        this.active = active;
        this.publicPage = publicPage;
        this.slowThresholdMs = slowThresholdMs;
    }

    /** Uma verificação que respondeu, mas acima do limite configurado. */
    public boolean isSlow(boolean up, Long responseTimeMs) {
        return up && responseTimeMs != null && responseTimeMs > slowThresholdMs;
    }

    public boolean isPublicPage() {
        return publicPage;
    }

    /** Diz se já passou o intervalo desde a última verificação. */
    public boolean isDue(Instant now) {
        return active && (lastCheckedAt == null || !lastCheckedAt.plusSeconds(intervalSeconds).isAfter(now));
    }

    public void registerCheck(boolean up, Long responseTimeMs, Instant checkedAt, Instant certExpiresAt) {
        if (!up) {
            this.lastStatus = ServiceStatus.DOWN;
        } else {
            this.lastStatus = isSlow(true, responseTimeMs) ? ServiceStatus.DEGRADED : ServiceStatus.UP;
        }
        this.lastCheckedAt = checkedAt;
        if (certExpiresAt != null) {
            this.certExpiresAt = certExpiresAt;
        }
    }

    public int getSlowThresholdMs() {
        return slowThresholdMs;
    }

    public Instant getCertExpiresAt() {
        return certExpiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public boolean isActive() {
        return active;
    }

    public ServiceStatus getLastStatus() {
        return lastStatus;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
