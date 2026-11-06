package com.vigia.security;

/** Usuário autenticado extraído do token JWT. Use com {@code @AuthenticationPrincipal AuthUser user}. */
public record AuthUser(Long id, String email) {
}
