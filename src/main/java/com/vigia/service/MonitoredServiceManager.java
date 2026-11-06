package com.vigia.service;

import com.vigia.auth.UserRepository;
import com.vigia.check.CheckRunner;
import com.vigia.check.HealthCheckRepository;
import com.vigia.check.UptimeStats;
import com.vigia.check.UrlGuard;
import com.vigia.common.ApiException;
import com.vigia.service.ServiceDtos.CheckPoint;
import com.vigia.service.ServiceDtos.CheckResponse;
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

    public MonitoredServiceManager(MonitoredServiceRepository services, HealthCheckRepository checks,
                                   UserRepository users, UrlGuard urlGuard, CheckRunner checkRunner) {
        this.services = services;
        this.checks = checks;
        this.users = users;
        this.urlGuard = urlGuard;
        this.checkRunner = checkRunner;
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
                req.intervalSeconds(), req.activeOrDefault(), req.publicPageOrDefault());
        return ServiceResponse.from(services.save(service));
    }

    @Transactional
    public ServiceResponse update(Long ownerId, Long id, ServiceRequest req) {
        MonitoredService service = find(ownerId, id);
        service.update(req.name().trim(), validatedUrl(req.url()), req.intervalSeconds(), req.activeOrDefault(),
                req.publicPageOrDefault());
        return ServiceResponse.from(service);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        services.delete(find(ownerId, id));
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

    /** Cards do painel do usuário logado. */
    @Transactional(readOnly = true)
    public List<StatusCard> dashboard(Long ownerId) {
        return services.findByOwnerIdOrderByNameAsc(ownerId).stream().map(this::toCard).toList();
    }

    /** Cards da página pública: só serviços marcados como públicos. */
    @Transactional(readOnly = true)
    public List<StatusCard> publicStatus() {
        return services.findByPublicPageTrueOrderByNameAsc().stream().map(this::toCard).toList();
    }

    private StatusCard toCard(MonitoredService s) {
        UptimeResponse uptime = uptimeOf(s.getId(), 24);
        List<CheckPoint> recent = new ArrayList<>(
                checks.findTop60ByServiceIdOrderByCheckedAtDesc(s.getId()).stream().map(CheckPoint::from).toList());
        Collections.reverse(recent); // do mais antigo ao mais recente, para o gráfico
        return new StatusCard(s.getId(), s.getName(), s.getUrl(), s.isActive(), s.isPublicPage(),
                s.getIntervalSeconds(), s.getLastStatus(), s.getLastCheckedAt(),
                uptime.uptimePercent(), uptime.avgResponseTimeMs(), recent);
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
