package com.vigia.check;

import com.vigia.service.MonitoredService;
import com.vigia.service.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A cada "tick" procura os serviços cujo intervalo já venceu e verifica todos em paralelo,
 * uma virtual thread por serviço. Assim um site lento não atrasa os outros.
 */
@Component
@ConditionalOnProperty(prefix = "monitor.checker", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final MonitoredServiceRepository services;
    private final CheckRunner runner;

    public CheckScheduler(MonitoredServiceRepository services, CheckRunner runner) {
        this.services = services;
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${monitor.checker.tick-ms}", initialDelay = 5000)
    public void runDueChecks() {
        Instant now = Instant.now();
        List<MonitoredService> due = services.findByActiveTrue().stream().filter(s -> s.isDue(now)).toList();
        if (due.isEmpty()) {
            return;
        }
        log.debug("Verificando {} serviço(s)", due.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (MonitoredService service : due) {
                Long id = service.getId();
                String url = service.getUrl();
                executor.submit(() -> {
                    try {
                        runner.run(id, url).ifPresent(check -> {
                            if (!check.isUp()) {
                                log.warn("Serviço {} ({}) fora do ar: {}", id, url, check.getErrorMessage());
                            }
                        });
                    } catch (RuntimeException e) {
                        log.error("Falha ao verificar o serviço {}", id, e);
                    }
                });
            }
        } // o close() espera todas as verificações terminarem antes do próximo tick
    }
}
