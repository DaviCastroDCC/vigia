package com.vigia.service;

public enum ServiceStatus {
    UP,
    /** Respondeu, mas acima do limite de tempo configurado no serviço. */
    DEGRADED,
    DOWN
}
