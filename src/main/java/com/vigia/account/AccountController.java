package com.vigia.account;

import com.vigia.auth.User;
import com.vigia.auth.UserRepository;
import com.vigia.common.ApiException;
import com.vigia.security.AuthUser;
import com.vigia.telegram.TelegramClient;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;

/** Dados da conta logada e conexão com o Telegram para receber alertas. */
@RestController
@RequestMapping("/api/me")
public class AccountController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final TelegramClient telegram;

    public AccountController(UserRepository users, TelegramClient telegram) {
        this.users = users;
        this.telegram = telegram;
    }

    public record Me(String name, String email, boolean telegramAvailable, boolean telegramConnected) {
    }

    public record TelegramLink(String url) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Me me(@AuthenticationPrincipal AuthUser auth) {
        User user = find(auth);
        return new Me(user.getName(), user.getEmail(), telegram.enabled(), user.getTelegramChatId() != null);
    }

    /** Gera um link de uso único: ao abri-lo e tocar em "Iniciar", o bot associa o chat a esta conta. */
    @PostMapping("/telegram")
    @Transactional
    public TelegramLink linkTelegram(@AuthenticationPrincipal AuthUser auth) {
        String bot = telegram.botUsername()
                .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Alertas no Telegram não estão configurados"));
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        find(auth).startTelegramLink(code);
        return new TelegramLink("https://t.me/" + bot + "?start=" + code);
    }

    @DeleteMapping("/telegram")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unlinkTelegram(@AuthenticationPrincipal AuthUser auth) {
        find(auth).unlinkTelegram();
    }

    private User find(AuthUser auth) {
        return users.findById(auth.id())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));
    }
}
