package com.vigia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.vigia.incident.IncidentEvent;
import com.vigia.incident.IncidentNotifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorApiTest {

    private static HttpServer fakeSite;
    private static int fakePort;

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    /** O envio real (Telegram) é substituído: o teste só confere quais alertas seriam enviados. */
    @MockitoBean
    IncidentNotifier notifier;

    private static final AtomicInteger flakyStatus = new AtomicInteger(200);

    @BeforeAll
    static void startFakeSite() throws Exception {
        fakeSite = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeSite.createContext("/ok", ex -> {
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        fakeSite.createContext("/broken", ex -> {
            ex.sendResponseHeaders(503, -1);
            ex.close();
        });
        fakeSite.createContext("/slow", ex -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        fakeSite.createContext("/flaky", ex -> {
            ex.sendResponseHeaders(flakyStatus.get(), -1);
            ex.close();
        });
        fakeSite.start();
        fakePort = fakeSite.getAddress().getPort();
    }

    @AfterAll
    static void stopFakeSite() {
        fakeSite.stop(0);
    }

    @Test
    void rejectsRequestsWithoutToken() throws Exception {
        mvc.perform(get("/api/services")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidService() throws Exception {
        String token = registerAndGetToken();
        mvc.perform(post("/api/services").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"url\":\"ftp://x\",\"intervalSeconds\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.intervalSeconds").exists());
    }

    @Test
    void fullFlow_createCheckAndComputeUptime() throws Exception {
        String token = registerAndGetToken();

        long okId = createService(token, "Site OK", "http://127.0.0.1:" + fakePort + "/ok");
        long brokenId = createService(token, "Site quebrado", "http://127.0.0.1:" + fakePort + "/broken");

        mvc.perform(post("/api/services/" + okId + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.up").value(true))
                .andExpect(jsonPath("$.statusCode").value(200));
        mvc.perform(post("/api/services/" + brokenId + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.up").value(false))
                .andExpect(jsonPath("$.statusCode").value(503));

        mvc.perform(get("/api/services").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == " + okId + ")].lastStatus").value("UP"))
                .andExpect(jsonPath("$[?(@.id == " + brokenId + ")].lastStatus").value("DOWN"));

        mvc.perform(get("/api/services/" + okId + "/uptime").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChecks").value(1))
                .andExpect(jsonPath("$.uptimePercent").value(100.0));

        mvc.perform(get("/api/services/" + brokenId + "/checks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].errorMessage").value("HTTP 503"));

        mvc.perform(delete("/api/services/" + brokenId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void usersCannotSeeEachOthersServices() throws Exception {
        String alice = registerAndGetToken();
        String bob = registerAndGetToken();
        long id = createService(alice, "Privado", "https://example.com");

        mvc.perform(get("/api/services/" + id).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    @Test
    void showcaseIsVisibleToEveryone_butOtherUsersStayPrivate() throws Exception {
        String owner = showcaseToken();
        String okUrl = "http://127.0.0.1:" + fakePort + "/ok";
        long showcaseId = createService(owner, "Vitrine", okUrl, true);
        long ownerPrivateId = createService(owner, "Privado do dono", okUrl, false);
        mvc.perform(post("/api/services/" + showcaseId + "/check").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());

        // um usuário qualquer tenta publicar: o pedido é aceito, mas o serviço fica privado
        String stranger = registerAndGetToken();
        long strangerId = createService(stranger, "Spam", okUrl, true);
        mvc.perform(get("/api/services/" + strangerId).header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.publicPage").value(false));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + stranger))
                .andExpect(jsonPath("$.canPublish").value(false));
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.canPublish").value(true));

        // página pública: só a vitrine
        mvc.perform(get("/api/public/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + showcaseId + ")].uptime24h").value(100.0))
                .andExpect(jsonPath("$[?(@.id == " + showcaseId + ")].recentChecks.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == " + ownerPrivateId + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + strangerId + ")]").isEmpty());

        // painel do usuário: os dele (editáveis) + a vitrine (só leitura), nunca o privado do dono
        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + strangerId + ")].mine").value(hasItem(true)))
                .andExpect(jsonPath("$[?(@.id == " + showcaseId + ")].mine").value(hasItem(false)))
                .andExpect(jsonPath("$[?(@.id == " + ownerPrivateId + ")]").isEmpty());

        // a vitrine aparece no painel, mas continua sendo só leitura para os outros
        mvc.perform(post("/api/services/" + showcaseId + "/check").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/services/" + showcaseId).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // o dono não vê a própria vitrine duplicada
        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$[?(@.id == " + showcaseId + ")].mine").value(org.hamcrest.Matchers.contains(true)));

        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void slowResponseIsDegraded_butStillCountsAsUp() throws Exception {
        String token = registerAndGetToken();
        long id = createService(token, Map.of("name", "Lento", "url", "http://127.0.0.1:" + fakePort + "/slow",
                "intervalSeconds", 60, "publicPage", true, "slowThresholdMs", 100));

        mvc.perform(post("/api/services/" + id + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.up").value(true));

        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].lastStatus").value("DEGRADED"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].slowThresholdMs").value(100))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].uptime24h").value(100.0))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].recentChecks[0].slow").value(true))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].daily[0].total").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].daily[0].up").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].uptime90d").value(100.0));
    }

    @Test
    void rejectsInvalidSlowThreshold() throws Exception {
        String token = registerAndGetToken();
        mvc.perform(post("/api/services").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "X", "url", "https://example.com",
                                "intervalSeconds", 60, "slowThresholdMs", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.slowThresholdMs").exists());
    }

    @Test
    void badgeIsServedForPublicServices_only() throws Exception {
        String token = showcaseToken();
        String okUrl = "http://127.0.0.1:" + fakePort + "/ok";
        long publicId = createService(token, "Publico", okUrl, true);
        long privateId = createService(token, "Privado", okUrl, false);
        long strangerId = createService(registerAndGetToken(), "Outro", okUrl, true);
        mvc.perform(get("/api/public/services/" + strangerId + "/badge.svg"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/services/" + publicId + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String svg = mvc.perform(get("/api/public/services/" + publicId + "/badge.svg"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=300")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(svg).startsWith("<svg").contains("uptime 30d").contains("100%");

        mvc.perform(get("/api/public/services/" + privateId + "/badge.svg"))
                .andExpect(status().isNotFound());
    }

    @Test
    void incidentOpensAfterTwoFailures_andResolvesOnRecovery() throws Exception {
        String token = registerAndGetToken();
        long id = createService(token, "Instavel", "http://127.0.0.1:" + fakePort + "/flaky", true);
        String check = "/api/services/" + id + "/check";

        flakyStatus.set(503);
        mvc.perform(post(check).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.up").value(false));
        // uma falha isolada ainda não é incidente
        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents.length()").value(0));
        verify(notifier, never()).notify(argThat(e -> e.serviceId() == id));

        mvc.perform(post(check).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.up").value(false));
        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents[0].cause").value("HTTP 503"))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents[0].resolvedAt").value(hasItem(nullValue())));
        verify(notifier).notify(argThat(e -> e.serviceId() == id && e.type() == IncidentEvent.Type.OPENED));

        // a terceira falha não abre outro incidente nem manda outro alerta
        mvc.perform(post(check).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.up").value(false));
        verify(notifier, times(1)).notify(argThat(e -> e.serviceId() == id && e.type() == IncidentEvent.Type.OPENED));

        flakyStatus.set(200);
        mvc.perform(post(check).header("Authorization", "Bearer " + token)).andExpect(jsonPath("$.up").value(true));
        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == " + id + ")].incidents[0].resolvedAt").exists());
        verify(notifier).notify(argThat(e -> e.serviceId() == id && e.type() == IncidentEvent.Type.RESOLVED));
    }

    @Test
    void telegramLinkIsUnavailableWithoutBotToken() throws Exception {
        String token = registerAndGetToken();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telegramAvailable").value(false))
                .andExpect(jsonPath("$.telegramConnected").value(false));
        mvc.perform(post("/api/me/telegram").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void eachAccountHasAServiceLimit_exceptTheShowcaseOwner() throws Exception {
        String user = registerAndGetToken();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + user))
                .andExpect(jsonPath("$.serviceLimit").value(5));
        for (int i = 1; i <= 5; i++) {
            createService(user, "Site " + i, "https://example.com/" + i);
        }
        mvc.perform(post("/api/services").header("Authorization", "Bearer " + user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Sexto", "url", "https://example.com/6",
                                "intervalSeconds", 60))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Limite de 5")));

        // excluir um libera a vaga
        long first = json.readTree(mvc.perform(get("/api/services").header("Authorization", "Bearer " + user))
                .andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        mvc.perform(delete("/api/services/" + first).header("Authorization", "Bearer " + user))
                .andExpect(status().isNoContent());
        createService(user, "Sexto", "https://example.com/6");

        // o dono da vitrine não tem limite
        String owner = showcaseToken();
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + owner))
                .andExpect(jsonPath("$.serviceLimit").value(nullValue()));
        for (int i = 1; i <= 6; i++) {
            createService(owner, "Vitrine " + i, "https://example.com/v" + i);
        }
    }

    /** Token da conta dona da vitrine (monitor.showcase-owner-email no application-test.yml). */
    private String showcaseToken() throws Exception {
        String credentials = "{\"email\":\"vitrine@teste.com\",\"password\":\"senha-segura\"}";
        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andReturn().getResponse();
        String body = login.getStatus() == 200 ? login.getContentAsString()
                : mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vitrine\",\"email\":\"vitrine@teste.com\",\"password\":\"senha-segura\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private String registerAndGetToken() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@teste.com";
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Teste\",\"email\":\"" + email + "\",\"password\":\"senha-segura\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // login também precisa funcionar com as mesmas credenciais
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"senha-segura\"}"))
                .andExpect(status().isOk());

        return json.readTree(body).get("token").asText();
    }

    private long createService(String token, String name, String url) throws Exception {
        return createService(token, name, url, false);
    }

    private long createService(String token, String name, String url, boolean publicPage) throws Exception {
        return createService(token, Map.of("name", name, "url", url, "intervalSeconds", 60, "publicPage", publicPage));
    }

    private long createService(String token, Map<String, Object> request) throws Exception {
        String body = mvc.perform(post("/api/services").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("id").asLong();
    }
}
