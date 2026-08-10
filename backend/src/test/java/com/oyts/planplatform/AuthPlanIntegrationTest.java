package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthPlanIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registrationPlanCreationAndUserIsolationWorkTogether() throws Exception {
        String ownerToken = register("owner@example.com", "计划负责人");
        mvc.perform(post("/api/plans")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Java 求职计划","description":"算法、八股与项目实践","startDate":"2026-08-10","endDate":"2026-09-30","overallTagName":"求职总投入","overallTagColor":"#002FA7"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Java 求职计划"))
            .andExpect(jsonPath("$.taskCount").value(0));

        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Java 求职计划"));

        String friendToken = register("friend@example.com", "朋友账号");
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + friendToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private String register(String email, String displayName) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(email, "password123", displayName));
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private record RegisterBody(String email, String password, String displayName) {}
}
