package com.vigia;

import com.vigia.check.UrlGuard;
import com.vigia.config.MonitorProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UrlGuardTest {

    private final UrlGuard guard = new UrlGuard(new MonitorProperties(null,
            new MonitorProperties.Checker(10000, 10, true), null, null));

    @Test
    void blocksInternalAddresses() {
        assertThat(guard.validate("http://localhost:8080")).isPresent();
        assertThat(guard.validate("http://127.0.0.1")).isPresent();
        assertThat(guard.validate("http://10.0.0.5")).isPresent();
        assertThat(guard.validate("http://192.168.1.1")).isPresent();
        assertThat(guard.validate("http://169.254.169.254/latest/meta-data")).isPresent();
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThat(guard.validate("file:///etc/passwd")).contains("Apenas URLs http e https são aceitas");
    }

    @Test
    void acceptsPublicIp() {
        assertThat(guard.validate("https://8.8.8.8")).isEmpty();
    }
}
