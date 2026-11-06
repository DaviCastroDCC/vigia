package com.vigia.check;

import org.springframework.stereotype.Component;

import java.util.Optional;

/** Verifica uma URL e salva o resultado. Usado pelo agendador e pelo endpoint "verificar agora". */
@Component
public class CheckRunner {

    private final HttpChecker httpChecker;
    private final CheckRecorder recorder;

    public CheckRunner(HttpChecker httpChecker, CheckRecorder recorder) {
        this.httpChecker = httpChecker;
        this.recorder = recorder;
    }

    public Optional<HealthCheck> run(Long serviceId, String url) {
        // A requisição HTTP fica fora da transação para não segurar conexão com o banco.
        CheckResult result = httpChecker.check(url);
        return recorder.record(serviceId, result);
    }
}
