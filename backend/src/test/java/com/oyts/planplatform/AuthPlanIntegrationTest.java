package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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

    @Test
    void returnedVersionsCanBeUsedForSequentialWritesAndRejectStaleWrites() throws Exception {
        String token = register("versions@example.com", "版本测试账号");
        String planJson = """
            {"name":"版本计划","description":"验证乐观锁","startDate":"2026-09-17","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7"}
            """;
        JsonNode plan = objectMapper.readTree(mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(planJson)).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
        String planId = plan.get("id").asText();

        JsonNode task = objectMapper.readTree(mvc.perform(post("/api/plans/{planId}/tasks", planId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
                    {"date":"2026-09-17","plannedMinutes":30,"category":"学习","title":"版本任务","description":"","acceptance":["完成"],"resourceUrl":"","required":true,"sortOrder":1}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String taskId = task.get("id").asText();

        mvc.perform(post("/api/tasks/{taskId}/timer/start", taskId).param("version", "0")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post("/api/tasks/{taskId}/timer/pause", taskId).param("version", "1")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));

        String stalePlanUpdate = """
            {"name":"版本计划（已更新）","description":"验证乐观锁","startDate":"2026-09-17","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7","version":0}
            """;
        mvc.perform(put("/api/plans/{planId}", planId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(stalePlanUpdate))
            .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(put("/api/plans/{planId}", planId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(stalePlanUpdate))
            .andExpect(status().isConflict());
    }

    @Test
    void revokedRefreshTokenReturnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody("logout@example.com", "password123", "退出测试账号"));
        JsonNode tokens = objectMapper.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String refreshToken = tokens.get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized());
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
