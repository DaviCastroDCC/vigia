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
    private final HttpChecker httpChecker;
    private boolean warmedUp;

    public CheckScheduler(MonitoredServiceRepository services, CheckRunner runner, HttpChecker httpChecker) {
        this.services = services;
        this.runner = runner;
        this.httpChecker = httpChecker;
    }

    @Scheduled(fixedDelayString = "${monitor.checker.tick-ms}", initialDelay = 5000)
    public void runDueChecks() {
        List<MonitoredService> active = services.findByActiveTrue();
        if (!warmedUp) {
            warmUp(active);
            warmedUp = true;
        }
        Instant now = Instant.now();
        List<MonitoredService> due = active.stream().filter(s -> s.isDue(now)).toList();
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

    /**
     * Logo depois que a aplicação sobe, a primeira requisição a cada site é bem mais lenta que as seguintes:
     * a JVM ainda não otimizou o código de TLS e nenhuma conexão está aberta. Medido em produção, eram
     * picos de ~1 s em todos os serviços ao mesmo tempo, a cada deploy. Esta rodada descartada
     * "aquece" a JVM e o pool de conexões, para que o gráfico mostre a lentidão dos sites, e não a nossa.
     */
    private void warmUp(List<MonitoredService> active) {
        long start = System.nanoTime();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            active.forEach(service -> executor.submit(() -> httpChecker.check(service.getUrl())));
        }
        log.info("Aquecimento: {} serviço(s) em {} ms", active.size(), (System.nanoTime() - start) / 1_000_000);
    }
}
