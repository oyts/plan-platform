package com.oyts.planplatform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:bootstrap_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "app.security.registration-enabled=false", "app.security.bootstrap-admin-email=bootstrap@example.test",
    "app.bootstrap.token-hash=2a74a98f980787226888656e6560191fbd87be44701d91f6c24c9d6c6d5dd3f7",
    "app.bootstrap.expires-at=2099-01-01T00:00:00Z"
})
@AutoConfigureMockMvc
class BootstrapIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private String request(String email, String token) throws Exception {
        return mapper.writeValueAsString(Map.of("email", email, "password", "BootstrapTest123!", "displayName", "初始管理员", "invitationToken", token));
    }
    @Test void bootstrapIsEmailBoundSingleUseAndDoesNotOpenRegistration() throws Exception {
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.registrationEnabled").value(false))
            .andExpect(jsonPath("$.apiVersion").value(1)).andExpect(jsonPath("$.serverVersion").value("0.1.7"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("other@example.test", "bootstrap-test-token")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("bootstrap@example.test", "wrong-token")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("bootstrap@example.test", "bootstrap-test-token")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("ADMIN"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("bootstrap@example.test", "bootstrap-test-token")))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request("other2@example.test", "bootstrap-test-token")))
            .andExpect(status().isForbidden());
    }
}
