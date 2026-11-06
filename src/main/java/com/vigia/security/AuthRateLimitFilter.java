package com.vigia.security;

import com.vigia.config.MonitorProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita login e cadastro por IP (janela fixa de 1 minuto), contra força bruta de senha e criação de
 * contas em massa. Acima do limite responde 429 com Retry-After.
 *
 * O IP vem de {@code request.getRemoteAddr()}: em produção o Caddy fica na frente e envia o IP real em
 * X-Forwarded-For (sobrescrevendo o que o cliente mandar), e o Spring o aplica antes deste filtro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000;
    private static final int MAX_TRACKED_IPS = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public AuthRateLimitFilter(MonitorProperties properties) {
        this(properties.limits() == null ? 0 : properties.limits().authRequestsPerMinute(), Clock.systemUTC());
    }

    AuthRateLimitFilter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return limit <= 0 || !"POST".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfterSeconds = tryAcquire(request.getRemoteAddr());
        if (retryAfterSeconds > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("""
                    {"title":"Too Many Requests","status":429,"detail":"Muitas tentativas. Tente de novo em %d s."}"""
                    .formatted(retryAfterSeconds));
            return;
        }
        chain.doFilter(request, response);
    }

    /** @return 0 se a requisição pode seguir; senão, quantos segundos esperar. */
    long tryAcquire(String ip) {
        long now = clock.millis();
        if (windows.size() > MAX_TRACKED_IPS) {
            windows.values().removeIf(w -> now - w.start >= WINDOW_MS);
        }
        Window window = windows.compute(ip, (k, w) -> w == null || now - w.start >= WINDOW_MS ? new Window(now) : w);
        synchronized (window) {
            if (window.count < limit) {
                window.count++;
                return 0;
            }
        }
        return Math.max(1, (window.start + WINDOW_MS - now + 999) / 1000);
    }

    private static final class Window {
        final long start;
        int count;

        Window(long start) {
            this.start = start;
        }
    }
}
