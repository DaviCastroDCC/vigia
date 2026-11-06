package com.vigia.service;

import com.vigia.check.HealthCheckRepository;
import com.vigia.service.ServiceDtos.DailyStat;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resumo diário dos últimos 90 dias, guardado em memória por alguns minutos.
 * Agregar 90 dias de verificações a cada atualização da página (a cada 15 s, por visitante)
 * seria desperdício: o dia de hoje pode ficar alguns minutos atrasado sem prejuízo.
 */
@Component
public class DailyStatsCache {

    static final int DAYS = 90;
    private static final Duration TTL = Duration.ofMinutes(3);

    private final HealthCheckRepository checks;
    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    public DailyStatsCache(HealthCheckRepository checks) {
        this.checks = checks;
    }

    public List<DailyStat> get(Long serviceId) {
        Instant now = Instant.now();
        Entry entry = cache.get(serviceId);
        if (entry == null || entry.loadedAt().plus(TTL).isBefore(now)) {
            entry = new Entry(now, load(serviceId, now));
            cache.put(serviceId, entry);
        }
        return entry.days();
    }

    public void evict(Long serviceId) {
        cache.remove(serviceId);
    }

    private List<DailyStat> load(Long serviceId, Instant now) {
        Instant since = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(DAYS - 1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
        return checks.dailyStats(serviceId, since).stream()
                .map(row -> new DailyStat(toDate(row[0]), ((Number) row[1]).longValue(), ((Number) row[2]).longValue()))
                .toList();
    }

    private static LocalDate toDate(Object value) {
        return value instanceof Date d ? d.toLocalDate() : (LocalDate) value;
    }

    private record Entry(Instant loadedAt, List<DailyStat> days) {
    }
}
