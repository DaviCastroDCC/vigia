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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    void publicStatusShowsOnlyPublicServices_andDashboardShowsAll() throws Exception {
        String token = registerAndGetToken();
        String okUrl = "http://127.0.0.1:" + fakePort + "/ok";
        long publicId = createService(token, "Publico", okUrl, true);
        long privateId = createService(token, "Privado", okUrl, false);
        mvc.perform(post("/api/services/" + publicId + "/check").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/public/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + publicId + ")].uptime24h").value(100.0))
                .andExpect(jsonPath("$[?(@.id == " + publicId + ")].recentChecks.length()").value(1))
                .andExpect(jsonPath("$[?(@.id == " + privateId + ")]").isEmpty());

        mvc.perform(get("/api/services/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/")).andExpect(status().isOk());
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
        String body = mvc.perform(post("/api/services").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "name", name, "url", url, "intervalSeconds", 60, "publicPage", publicPage))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(body);
        return node.get("id").asLong();
    }
}
