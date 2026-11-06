package com.vigia.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.vigia.auth.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lê as mensagens enviadas ao bot. Quando alguém abre o link t.me/bot?start=CÓDIGO, o Telegram manda
 * "/start CÓDIGO": o código identifica o usuário do Vigia, e o chat dele passa a receber os alertas.
 * Usa polling (getUpdates) em vez de webhook, então não precisa de nenhuma rota pública extra.
 */
@Component
@ConditionalOnExpression("'${monitor.alerts.telegram-bot-token:}' != ''")
public class TelegramLinkPoller {

    private final TelegramClient telegram;
    private final UserRepository users;
    private final TransactionTemplate tx;
    private long offset;

    public TelegramLinkPoller(TelegramClient telegram, UserRepository users, TransactionTemplate tx) {
        this.telegram = telegram;
        this.users = users;
        this.tx = tx;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 5000)
    public void poll() {
        for (JsonNode update : telegram.getUpdates(offset)) {
            offset = update.path("update_id").asLong() + 1;
            JsonNode message = update.path("message");
            long chatId = message.path("chat").path("id").asLong();
            if (chatId != 0) {
                telegram.sendMessage(chatId, reply(chatId, message.path("text").asText("")));
            }
        }
    }

    private String reply(long chatId, String text) {
        if (!text.startsWith("/start ")) {
            return "Olá! Para receber alertas, entre no painel do Vigia e clique em <b>Conectar Telegram</b>.";
        }
        String code = text.substring("/start ".length()).trim();
        Boolean linked = tx.execute(status -> users.findByTelegramLinkCode(code).map(user -> {
            user.linkTelegram(chatId);
            return true;
        }).orElse(false));
        return Boolean.TRUE.equals(linked)
                ? "✅ <b>Vigia conectado.</b>\nVocê vai receber um aviso aqui quando um dos seus serviços cair e quando voltar."
                : "Esse link expirou ou já foi usado. Gere um novo no painel do Vigia.";
    }
}
