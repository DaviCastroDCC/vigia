package com.vigia;

import com.vigia.incident.IncidentNotifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentNotifierTest {

    @Test
    void humanizesDurations() {
        assertThat(IncidentNotifier.humanize(Duration.ofSeconds(20))).isEqualTo("1 min");
        assertThat(IncidentNotifier.humanize(Duration.ofMinutes(7))).isEqualTo("7 min");
        assertThat(IncidentNotifier.humanize(Duration.ofMinutes(60))).isEqualTo("1 h");
        assertThat(IncidentNotifier.humanize(Duration.ofMinutes(135))).isEqualTo("2 h 15 min");
    }
}
