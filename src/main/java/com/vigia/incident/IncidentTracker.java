package com.vigia.incident;

import com.vigia.check.CheckResult;
import com.vigia.config.MonitorProperties;
import com.vigia.service.MonitoredService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Decide, a cada verificação, se um incidente começa ou termina. Uma falha isolada não abre
 * incidente (evita alarme falso por oscilação de rede): são precisas N falhas seguidas.
 * Deve ser chamado dentro da transação que salvou a verificação.
 */
@Component
public class IncidentTracker {

    private final IncidentRepository incidents;
    private final int failuresBeforeIncident;

    public IncidentTracker(IncidentRepository incidents, MonitorProperties properties) {
        this.incidents = incidents;
        this.failuresBeforeIncident = Math.max(1, properties.alerts().failuresBeforeIncident());
    }

    /** @param previousFailures falhas seguidas antes desta verificação */
    public Optional<IncidentEvent> onCheck(MonitoredService service, int previousFailures, CheckResult result) {
        if (!result.up()) {
            if (service.getConsecutiveFailures() != failuresBeforeIncident) {
                return Optional.empty(); // ainda não confirmou, ou o incidente já está aberto
            }
            if (incidents.findFirstByServiceIdAndResolvedAtIsNull(service.getId()).isPresent()) {
                return Optional.empty();
            }
            Incident incident = incidents.save(new Incident(service, service.getFailingSince(), result.errorMessage()));
            return Optional.of(event(IncidentEvent.Type.OPENED, service, incident));
        }
        if (previousFailures < failuresBeforeIncident) {
            return Optional.empty(); // não havia incidente aberto: evita a consulta no caso comum
        }
        return incidents.findFirstByServiceIdAndResolvedAtIsNull(service.getId()).map(incident -> {
            incident.resolve(result.checkedAt());
            return event(IncidentEvent.Type.RESOLVED, service, incident);
        });
    }

    private static IncidentEvent event(IncidentEvent.Type type, MonitoredService s, Incident i) {
        return new IncidentEvent(type, s.getId(), s.getName(), s.getUrl(), s.getOwner().getTelegramChatId(),
                i.getStartedAt(), i.getResolvedAt(), i.getCause());
    }
}
