package com.vigia.incident;

import java.time.Instant;

/**
 * Abertura ou encerramento de um incidente, com tudo que o alerta precisa já carregado
 * (o alerta é enviado depois da transação, quando as entidades não podem mais ser lidas).
 */
public record IncidentEvent(Type type, Long serviceId, String serviceName, String url, Long ownerChatId,
                            Instant startedAt, Instant resolvedAt, String cause) {

    public enum Type { OPENED, RESOLVED }
}
