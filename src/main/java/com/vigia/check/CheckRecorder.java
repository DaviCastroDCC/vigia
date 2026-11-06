package com.vigia.check;

import com.vigia.incident.IncidentEvent;
import com.vigia.incident.IncidentTracker;
import com.vigia.service.MonitoredServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Numa única transação: salva o resultado, atualiza o último status do serviço e
 * abre ou encerra um incidente, se for o caso.
 */
@Component
public class CheckRecorder {

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;
    private final IncidentTracker incidents;

    public CheckRecorder(MonitoredServiceRepository services, HealthCheckRepository checks,
                         IncidentTracker incidents) {
        this.services = services;
        this.checks = checks;
        this.incidents = incidents;
    }

    /** O que foi salvo, e o incidente que começou ou terminou com esta verificação (se houver). */
    public record Recorded(HealthCheck check, Optional<IncidentEvent> incident) {
    }

    @Transactional
    public Optional<Recorded> record(Long serviceId, CheckResult result) {
        // O serviço pode ter sido apagado enquanto a requisição HTTP estava em andamento.
        return services.findById(serviceId).map(service -> {
            int previousFailures = service.getConsecutiveFailures();
            service.registerCheck(result.up(), result.responseTimeMs(), result.checkedAt(), result.certExpiresAt());
            HealthCheck saved = checks.save(new HealthCheck(service, result));
            return new Recorded(saved, incidents.onCheck(service, previousFailures, result));
        });
    }
}
