package com.vigia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(Jwt jwt, Checker checker, Alerts alerts) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    public record Checker(long tickMs, int timeoutSeconds, boolean blockPrivateAddresses) {
    }

    /**
     * @param failuresBeforeIncident falhas seguidas para abrir um incidente
     * @param zone                   fuso dos horários nas mensagens de alerta
     * @param telegramBotToken       token do bot (vazio = Telegram desligado)
     */
    public record Alerts(int failuresBeforeIncident, String zone, String telegramBotToken) {

        public boolean telegramEnabled() {
            return telegramBotToken != null && !telegramBotToken.isBlank();
        }
    }
}
