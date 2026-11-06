package com.vigia;

import com.vigia.service.MonitoredService;
import com.vigia.service.ServiceStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MonitoredServiceTest {

    private final Instant now = Instant.now();

    private MonitoredService service(String url) {
        return new MonitoredService(null, "Teste", url, 60, true, true, 500);
    }

    @Test
    void statusFollowsResponseTimeThreshold() {
        MonitoredService s = service("https://example.com");

        s.registerCheck(true, 200L, now, null);
        assertThat(s.getLastStatus()).isEqualTo(ServiceStatus.UP);

        s.registerCheck(true, 900L, now, null);
        assertThat(s.getLastStatus()).isEqualTo(ServiceStatus.DEGRADED);

        s.registerCheck(false, 900L, now, null);
        assertThat(s.getLastStatus()).isEqualTo(ServiceStatus.DOWN);
    }

    @Test
    void keepsLastKnownCertificate_andForgetsItWhenUrlChanges() {
        MonitoredService s = service("https://example.com");
        Instant expiry = now.plus(30, ChronoUnit.DAYS);

        s.registerCheck(true, 100L, now, expiry);
        s.registerCheck(false, null, now, null); // falha de rede não apaga o certificado conhecido
        assertThat(s.getCertExpiresAt()).isEqualTo(expiry);

        s.update("Teste", "https://outro.example.com", 60, true, true, 500);
        assertThat(s.getCertExpiresAt()).isNull();
    }
}
