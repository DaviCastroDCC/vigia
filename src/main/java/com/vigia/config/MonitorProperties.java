package com.vigia.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param showcaseOwnerEmail conta cujos serviços públicos formam a vitrine (vazio = sem vitrine)
 */
@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(Jwt jwt, Checker checker, Alerts alerts, String showcaseOwnerEmail, Limits limits) {

    /** @param maxServicesPerUser serviços por conta (0 = sem limite). O dono da vitrine não tem limite. */
    public record Limits(int maxServicesPerUser) {
    }

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
