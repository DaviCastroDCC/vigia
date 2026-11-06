package com.vigia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "monitor.limits.auth-requests-per-minute=3")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRateLimitApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void bruteForceOnLoginIsCutAfterTheLimit() throws Exception {
        String wrong = "{\"email\":\"alvo@teste.com\",\"password\":\"chute-errado\"}";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));

        // o resto da API não é afetado
        mvc.perform(get("/api/public/status")).andExpect(status().isOk());
    }
}
