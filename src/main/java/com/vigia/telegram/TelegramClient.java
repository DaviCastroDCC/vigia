package com.vigia.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vigia.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Chamadas mínimas à Bot API do Telegram, sem biblioteca: getMe, getUpdates e sendMessage. */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final String token;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private volatile String botUsername;

    public TelegramClient(MonitorProperties properties, ObjectMapper json) {
        this.token = properties.alerts().telegramBotToken();
        this.json = json;
    }

    public boolean enabled() {
        return token != null && !token.isBlank();
    }

    /** Nome do bot (para montar o link t.me/...), lido uma vez da API. */
    public Optional<String> botUsername() {
        if (!enabled()) {
            return Optional.empty();
        }
        if (botUsername == null) {
            call("getMe", Map.of()).map(r -> r.path("username").asText(null)).ifPresent(u -> botUsername = u);
        }
        return Optional.ofNullable(botUsername);
    }

    public List<JsonNode> getUpdates(long offset) {
        return call("getUpdates", Map.of("offset", offset, "timeout", 0, "allowed_updates", List.of("message")))
                .map(result -> {
                    List<JsonNode> updates = new ArrayList<>();
                    result.forEach(updates::add);
                    return updates;
                })
                .orElse(List.of());
    }

    public boolean sendMessage(long chatId, String html) {
        return call("sendMessage", Map.of("chat_id", chatId, "text", html, "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true))).isPresent();
    }

    private Optional<JsonNode> call(String method, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/" + method))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body());
            if (!node.path("ok").asBoolean()) {
                // Nunca loga a URL: ela contém o token do bot.
                log.warn("Telegram {} falhou: {}", method, node.path("description").asText());
                return Optional.empty();
            }
            return Optional.of(node.path("result"));
        } catch (IOException e) {
            log.warn("Telegram {} indisponível: {}", method, e.getClass().getSimpleName());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
