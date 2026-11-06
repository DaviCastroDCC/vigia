package com.vigia.check;

import com.vigia.config.MonitorProperties;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Impede que a API seja usada para acessar a rede interna (SSRF): localhost, redes privadas
 * e o endereço de metadados da AWS (169.254.169.254). Desligue com CHECKER_BLOCK_PRIVATE=false
 * se quiser monitorar serviços da sua própria rede.
 */
@Component
public class UrlGuard {

    private final boolean blockPrivate;

    public UrlGuard(MonitorProperties properties) {
        this.blockPrivate = properties.checker().blockPrivateAddresses();
    }

    /** @return o motivo da recusa, ou vazio se a URL pode ser verificada. */
    public Optional<String> validate(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return Optional.of("URL inválida");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of("Apenas URLs http e https são aceitas");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return Optional.of("URL sem host");
        }
        if (!blockPrivate) {
            return Optional.empty();
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isInternal(address)) {
                    return Optional.of("Endereços internos ou privados não podem ser monitorados");
                }
            }
        } catch (UnknownHostException e) {
            // Host que não resolve agora: aceita no cadastro, a verificação vai marcar como fora do ar.
        }
        return Optional.empty();
    }

    static boolean isInternal(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        // IPv6 unique local (fc00::/7)
        return a instanceof Inet6Address && (a.getAddress()[0] & 0xFE) == 0xFC;
    }
}
