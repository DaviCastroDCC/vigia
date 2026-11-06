package com.vigia.check;

import com.vigia.incident.IncidentNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Verifica uma URL e salva o resultado. Usado pelo agendador e pelo endpoint "verificar agora". */
@Component
public class CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);

    private final HttpChecker httpChecker;
    private final CheckRecorder recorder;
    private final IncidentNotifier notifier;

    public CheckRunner(HttpChecker httpChecker, CheckRecorder recorder, IncidentNotifier notifier) {
        this.httpChecker = httpChecker;
        this.recorder = recorder;
        this.notifier = notifier;
    }

    public Optional<HealthCheck> run(Long serviceId, String url) {
        // A requisição HTTP fica fora da transação para não segurar conexão com o banco.
        CheckResult result = httpChecker.check(url);
        Optional<CheckRecorder.Recorded> recorded = recorder.record(serviceId, result);
        // O alerta sai só depois do commit: se a gravação falhar, ninguém é avisado de algo que não ficou salvo.
        recorded.flatMap(CheckRecorder.Recorded::incident).ifPresent(event -> {
            try {
                notifier.notify(event);
            } catch (RuntimeException e) {
                log.error("Falha ao enviar alerta do serviço {}", serviceId, e);
            }
        });
        return recorded.map(CheckRecorder.Recorded::check);
    }
}
