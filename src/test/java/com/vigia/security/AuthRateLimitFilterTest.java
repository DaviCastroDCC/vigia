package com.vigia.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRateLimitFilterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };

    @Test
    void allowsUpToTheLimitPerIp_thenAsksToWait_andResetsAfterTheWindow() {
        AuthRateLimitFilter filter = new AuthRateLimitFilter(3, clock);

        assertThat(filter.tryAcquire("1.1.1.1")).isZero();
        assertThat(filter.tryAcquire("1.1.1.1")).isZero();
        assertThat(filter.tryAcquire("1.1.1.1")).isZero();
        assertThat(filter.tryAcquire("1.1.1.1")).isEqualTo(60);   // bloqueado: espere 60 s

        assertThat(filter.tryAcquire("2.2.2.2")).isZero();         // outro IP tem a própria cota

        now.addAndGet(45_000);
        assertThat(filter.tryAcquire("1.1.1.1")).isEqualTo(15);    // ainda faltam 15 s

        now.addAndGet(15_000);
        assertThat(filter.tryAcquire("1.1.1.1")).isZero();         // janela nova
    }
}
