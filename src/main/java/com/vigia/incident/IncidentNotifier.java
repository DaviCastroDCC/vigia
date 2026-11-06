package com.vigia.incident;

import com.vigia.config.MonitorProperties;
import com.vigia.telegram.TelegramClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Transforma a abertura/encerramento de um incidente em mensagem no Telegram do dono do serviço. */
@Component
public class IncidentNotifier {

    private static final Logger log = LoggerFactory.getLogger(IncidentNotifier.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final TelegramClient telegram;
    private final ZoneId zone;

    public IncidentNotifier(TelegramClient telegram, MonitorProperties properties) {
        this.telegram = telegram;
        this.zone = ZoneId.of(properties.alerts().zone());
    }

    public void notify(IncidentEvent event) {
        log.info("Incidente {} em {} ({})", event.type(), event.serviceName(), event.url());
        if (event.ownerChatId() != null && telegram.enabled()) {
            telegram.sendMessage(event.ownerChatId(), message(event));
        }
    }

    String message(IncidentEvent e) {
        String name = "<b>" + escape(e.serviceName()) + "</b>";
        if (e.type() == IncidentEvent.Type.OPENED) {
            return "🔴 " + name + " está fora do ar\n"
                    + escape(e.url()) + "\n"
                    + "Causa: " + escape(e.cause() == null ? "desconhecida" : e.cause()) + "\n"
                    + "Desde " + TIME.format(e.startedAt().atZone(zone));
        }
        return "🟢 " + name + " voltou ao ar\n"
                + "Ficou fora por " + humanize(Duration.between(e.startedAt(), e.resolvedAt()));
    }

    public static String humanize(Duration d) {
        long minutes = Math.max(1, Math.round(d.toSeconds() / 60.0));
        if (minutes < 60) {
            return minutes + " min";
        }
        long hours = minutes / 60;
        return minutes % 60 == 0 ? hours + " h" : hours + " h " + (minutes % 60) + " min";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
