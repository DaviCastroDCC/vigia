package com.vigia.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_link_code", length = 40, unique = true)
    private String telegramLinkCode;

    protected User() {
    }

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getTelegramChatId() {
        return telegramChatId;
    }

    /** Gera o código que vai no link do bot; vale até ser usado. */
    public void startTelegramLink(String code) {
        this.telegramLinkCode = code;
    }

    public void linkTelegram(long chatId) {
        this.telegramChatId = chatId;
        this.telegramLinkCode = null;
    }

    public void unlinkTelegram() {
        this.telegramChatId = null;
        this.telegramLinkCode = null;
    }
}
