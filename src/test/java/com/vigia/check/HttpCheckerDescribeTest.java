package com.vigia.check;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;

import static org.assertj.core.api.Assertions.assertThat;

class HttpCheckerDescribeTest {

    @Test
    void translatesCommonNetworkFailures() {
        ConnectException dns = new ConnectException();
        dns.initCause(new UnresolvedAddressException());
        assertThat(HttpChecker.describe(dns)).isEqualTo("Domínio não encontrado (DNS)");

        assertThat(HttpChecker.describe(new ConnectException("Connection refused"))).isEqualTo("Conexão recusada");
        assertThat(HttpChecker.describe(new ConnectException())).isEqualTo("Não foi possível conectar");
        assertThat(HttpChecker.describe(new IOException("fim inesperado"))).isEqualTo("fim inesperado");
    }
}
