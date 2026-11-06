package com.vigia.check;

import com.vigia.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Faz a requisição HTTP e mede o tempo de resposta. Status menor que 400 conta como "no ar". */
@Component
public class HttpChecker {

    private final HttpClient client;
    private final Duration timeout;
    private final UrlGuard urlGuard;

    public HttpChecker(MonitorProperties properties, UrlGuard urlGuard) {
        this.timeout = Duration.ofSeconds(properties.checker().timeoutSeconds());
        this.urlGuard = urlGuard;
        // Redirecionamentos não são seguidos: um 3xx já indica que o serviço respondeu,
        // e seguir redirects poderia levar a um endereço interno.
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public CheckResult check(String url) {
        Instant startedAt = Instant.now();
        Optional<String> blocked = urlGuard.validate(url);
        if (blocked.isPresent()) {
            return CheckResult.failure(startedAt, null, blocked.get());
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("User-Agent", "Vigia/2.0")
                .GET()
                .build();
        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            return CheckResult.success(startedAt, response.statusCode(), elapsedMs(start), certExpiry(response));
        } catch (HttpTimeoutException e) {
            return CheckResult.failure(startedAt, elapsedMs(start), "Tempo esgotado após " + timeout.toSeconds() + "s");
        } catch (IOException e) {
            return CheckResult.failure(startedAt, elapsedMs(start), describe(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.failure(startedAt, elapsedMs(start), "Verificação interrompida");
        }
    }

    /**
     * Traduz as falhas de rede mais comuns para algo legível na página pública e no alerta.
     * O HttpClient costuma embrulhar a causa real (ex.: DNS) dentro de um ConnectException sem mensagem.
     */
    static String describe(IOException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof UnresolvedAddressException || t instanceof UnknownHostException) {
                return "Domínio não encontrado (DNS)";
            }
            if (t instanceof SSLHandshakeException) {
                return "Falha no certificado/TLS" + (t.getMessage() != null ? ": " + t.getMessage() : "");
            }
            if (t instanceof ConnectException && t.getMessage() != null && t.getMessage().contains("refused")) {
                return "Conexão recusada";
            }
        }
        if (e instanceof ConnectException) {
            return "Não foi possível conectar";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Validade do certificado do servidor, lida da própria conexão TLS (sem requisição extra). */
    static Instant certExpiry(HttpResponse<?> response) {
        return response.sslSession().map(session -> {
            try {
                Certificate[] chain = session.getPeerCertificates();
                return chain.length > 0 && chain[0] instanceof X509Certificate x509
                        ? x509.getNotAfter().toInstant() : null;
            } catch (SSLPeerUnverifiedException e) {
                return null;
            }
        }).orElse(null);
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
