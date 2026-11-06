package com.vigia.check;

import com.vigia.service.MonitoredServiceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Salva o resultado e atualiza o último status do serviço numa única transação. */
@Component
public class CheckRecorder {

    private final MonitoredServiceRepository services;
    private final HealthCheckRepository checks;

    public CheckRecorder(MonitoredServiceRepository services, HealthCheckRepository checks) {
        this.services = services;
        this.checks = checks;
    }

    @Transactional
    public Optional<HealthCheck> record(Long serviceId, CheckResult result) {
        // O serviço pode ter sido apagado enquanto a requisição HTTP estava em andamento.
        return services.findById(serviceId).map(service -> {
            service.registerCheck(result.up(), result.responseTimeMs(), result.checkedAt(), result.certExpiresAt());
            return checks.save(new HealthCheck(service, result));
        });
    }
}
