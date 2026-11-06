package com.vigia.service;

import com.vigia.auth.UserRepository;
import com.vigia.check.CheckRunner;
import com.vigia.check.HealthCheckRepository;
import com.vigia.check.UptimeStats;
import com.vigia.check.UrlGuard;
import com.vigia.common.ApiException;
import com.vigia.incident.IncidentRepository;
import com.vigia.service.ServiceDtos.IncidentView;
import com.vigia.service.ServiceDtos.CheckPoint;
import com.vigia.service.ServiceDtos.CheckResponse;
import com.vigia.service.ServiceDtos.DailyStat;
import com.vigia.service.ServiceDtos.StatusCard;
import com.vigia.service.ServiceDtos.ServiceRequest;
import com.vigia.service.ServiceDtos.ServiceResponse;
import com.vigia.service.ServiceDtos.UptimeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MonitoredServiceManager {

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final UserRepository users;
    private final UrlGuard urlGuard;
    private final CheckRunner checkRunner;
    private final DailyStatsCache dailyStats;
    private final IncidentRepository incidents;
    private final Showcase showcase;

    public MonitoredServiceManager(MonitoredServiceRepository services, HealthCheckRepository checks,
                                   UserRepository users, UrlGuard urlGuard, CheckRunner checkRunner,
                                   DailyStatsCache dailyStats, IncidentRepository incidents, Showcase showcase) {
        this.showcase = showcase;
        this.services = services;
        this.checks = checks;
        this.users = users;
        this.urlGuard = urlGuard;
        this.checkRunner = checkRunner;
        this.dailyStats = dailyStats;
        this.incidents = incidents;
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> list(Long ownerId) {
        return services.findByOwnerIdOrderByNameAsc(ownerId).stream().map(ServiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long ownerId, Long id) {
        return ServiceResponse.from(find(ownerId, id));
    }

    @Transactional
    public ServiceResponse create(Long ownerId, ServiceRequest req) {
        String url = validatedUrl(req.url());
        var service = new MonitoredService(users.getReferenceById(ownerId), req.name().trim(), url,
                req.intervalSeconds(), req.activeOrDefault(), publicPage(ownerId, req), req.slowThresholdOrDefault());
        return ServiceResponse.from(services.save(service));
    }

    @Transactional
    public ServiceResponse update(Long ownerId, Long id, ServiceRequest req) {
        MonitoredService service = find(ownerId, id);
        service.update(req.name().trim(), validatedUrl(req.url()), req.intervalSeconds(), req.activeOrDefault(),
                publicPage(ownerId, req), req.slowThresholdOrDefault());
        return ServiceResponse.from(service);
    }

    /** Só o dono da vitrine publica; para os demais, "público" é sempre falso. */
    private boolean publicPage(Long ownerId, ServiceRequest req) {
        return req.publicPageOrDefault() && showcase.canPublish(ownerId);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        services.delete(find(ownerId, id));
        dailyStats.evict(id);
    }

    @Transactional(readOnly = true)
    public Page<CheckResponse> history(Long ownerId, Long id, int page, int size) {
        find(ownerId, id);
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200));
        return checks.findByServiceIdOrderByCheckedAtDesc(id, pageable).map(CheckResponse::from);
    }

    @Transactional(readOnly = true)
    public UptimeResponse uptime(Long ownerId, Long id, int hours) {
        find(ownerId, id);
        return uptimeOf(id, hours);
    }

    private UptimeResponse uptimeOf(Long id, int hours) {
        int period = Math.clamp(hours, 1, 24 * 90);
        UptimeStats stats = checks.uptimeStats(id, Instant.now().minus(Duration.ofHours(period)));
        long total = stats.total() == null ? 0 : stats.total();
        long up = stats.upCount() == null ? 0 : stats.upCount();
        Double percent = total == 0 ? null : Math.round(up * 10000.0 / total) / 100.0;
        Double avg = stats.avgResponseMs() == null ? null : Math.round(stats.avgResponseMs() * 10) / 10.0;
        return new UptimeResponse(id, period, total, up, percent, avg);
    }

    /** Painel do usuário logado: os próprios serviços (editáveis) e, depois, a vitrine (só leitura). */
    @Transactional(readOnly = true)
    public List<StatusCard> dashboard(Long ownerId) {
        List<StatusCard> cards = new ArrayList<>(
                services.findByOwnerIdOrderByNameAsc(ownerId).stream().map(s -> toCard(s, true)).toList());
        showcase.ownerId().filter(id -> !id.equals(ownerId)).ifPresent(id ->
                services.findByOwnerIdAndPublicPageTrueOrderByNameAsc(id).forEach(s -> cards.add(toCard(s, false))));
        return cards;
    }

    /** Página pública: a vitrine (ou, sem vitrine configurada, todo serviço marcado como público). */
    @Transactional(readOnly = true)
    public List<StatusCard> publicStatus() {
        return publicServices().stream().map(s -> toCard(s, false)).toList();
    }

    private List<MonitoredService> publicServices() {
        if (!showcase.configured()) {
            return services.findByPublicPageTrueOrderByNameAsc();
        }
        return showcase.ownerId().map(services::findByOwnerIdAndPublicPageTrueOrderByNameAsc).orElse(List.of());
    }

    private StatusCard toCard(MonitoredService s, boolean mine) {
        UptimeResponse uptime = uptimeOf(s.getId(), 24);
        List<CheckPoint> recent = new ArrayList<>(checks.findTop60ByServiceIdOrderByCheckedAtDesc(s.getId()).stream()
                .map(c -> CheckPoint.from(c, s)).toList());
        Collections.reverse(recent); // do mais antigo ao mais recente, para o gráfico
        List<DailyStat> daily = dailyStats.get(s.getId());
        return new StatusCard(s.getId(), s.getName(), s.getUrl(), s.isActive(), s.isPublicPage(),
                s.getIntervalSeconds(), s.getSlowThresholdMs(), s.getLastStatus(), s.getLastCheckedAt(),
                s.getCertExpiresAt(), uptime.uptimePercent(), uptime.avgResponseTimeMs(), percentOf(daily), daily,
                recent, recentIncidents(s.getId()), mine);
    }

    private List<IncidentView> recentIncidents(Long serviceId) {
        Instant since = Instant.now().minus(Duration.ofDays(90));
        return incidents.findTop5ByServiceIdAndStartedAtAfterOrderByStartedAtDesc(serviceId, since).stream()
                .map(IncidentView::from).toList();
    }

    private static Double percentOf(List<DailyStat> days) {
        long total = days.stream().mapToLong(DailyStat::total).sum();
        long up = days.stream().mapToLong(DailyStat::up).sum();
        return total == 0 ? null : Math.round(up * 10000.0 / total) / 100.0;
    }

    /** Uptime de um serviço público, para o badge. Serviços privados respondem 404. */
    @Transactional(readOnly = true)
    public UptimeResponse publicUptime(Long id, int days) {
        services.findById(id).filter(this::isPubliclyVisible)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        return uptimeOf(id, Math.clamp(days, 1, 90) * 24);
    }

    private boolean isPubliclyVisible(MonitoredService s) {
        if (!s.isPublicPage()) {
            return false;
        }
        return !showcase.configured() || showcase.ownerId().filter(s.getOwner().getId()::equals).isPresent();
    }

    /** Executa uma verificação imediata, sem esperar o agendador. */
    public CheckResponse checkNow(Long ownerId, Long id) {
        MonitoredService service = services.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        return checkRunner.run(service.getId(), service.getUrl())
                .map(CheckResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
    }

    private MonitoredService find(Long ownerId, Long id) {
        return services.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
    }

    private String validatedUrl(String raw) {
        String url = raw.trim();
        urlGuard.validate(url).ifPresent(reason -> {
            throw new ApiException(HttpStatus.BAD_REQUEST, reason);
        });
        return url;
    }
}
