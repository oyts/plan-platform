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

    @Test
    void portableLegacyBackupCanBePreviewedImportedIdempotentlyAndRolledBack() throws Exception {
        String token = register("migration@example.com", "迁移测试账号");
        String backup = """
            {
              "schemaVersion":6,"planVersion":"1.10.1","exportedAt":"2026-09-17T08:00:00Z",
              "migrationCatalog":{
                "defaultPlan":{"id":"summer-plan","name":"暑假计划","startDate":"2026-09-17","endDate":"2026-09-30","overallTagId":"all","tags":[{"id":"all","name":"总计","color":"#002FA7","isOverall":true}],"tasks":[{"id":"task-a","date":"2026-09-17","startTime":"09:00","endTime":"10:00","plannedMinutes":60,"category":"java","title":"Java 学习","description":"完成章节","acceptance":["提交笔记"],"required":true,"sourceRef":"https://example.com"}]},
                "builtInMainlines":[{"id":"guitar","name":"吉他主线","description":"顺序学习","color":"#16A34A","items":[{"id":"step-a","date":"2026-09-17","startTime":"10:00","endTime":"10:30","plannedMinutes":30,"category":"guitar","title":"第一关","description":"基础练习","acceptance":["完成录音"],"required":false,"sourceRef":""}]}]
              },
              "progress":{
                "task-a":{"taskId":"task-a","status":"completed","accumulatedSeconds":600,"startedAt":null,"completedAt":"2026-09-17T08:10:00Z","timeEntries":[{"id":"entry-a","taskId":"task-a","startedAt":"2026-09-17T08:00:00Z","endedAt":"2026-09-17T08:10:00Z","seconds":600,"source":"timer"}],"acceptanceChecks":[true],"note":"完成","deferTo":null,"skipReason":null,"updatedAt":"2026-09-17T08:10:00Z"},
                "step-a":{"taskId":"step-a","status":"completed","accumulatedSeconds":300,"startedAt":null,"completedAt":"2026-09-17T08:20:00Z","timeEntries":[],"acceptanceChecks":[true],"note":"通过","deferTo":null,"skipReason":null,"updatedAt":"2026-09-17T08:20:00Z"}
              },
              "reflections":{"2026-09-17":{"date":"2026-09-17","completedSummary":"完成学习","unfinishedSummary":"","blockers":"","firstTaskTomorrow":"继续","energy":4,"note":"状态良好","updatedAt":"2026-09-17T12:00:00Z"}},
              "gates":{"G1":{"gateId":"G1","checks":{},"note":"","reviewedAt":null}},
              "workspace":{"activePlanId":"summer-plan","defaultPlanTags":[{"id":"all","name":"总计","color":"#002FA7","isOverall":true}],"customPlans":[],"customMainlines":[],"taskTemplates":[],"trash":[],"deletionMarkers":[],"updatedAt":"2026-09-17T12:00:00Z"}
            }
            """;

        mvc.perform(post("/api/migrations/legacy/preview").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(backup))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true))
            .andExpect(jsonPath("$.plans").value(1)).andExpect(jsonPath("$.mainlines").value(1));
        String importedBody = mvc.perform(post("/api/migrations/legacy/import").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(backup))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.tasks").value(1))
            .andExpect(jsonPath("$.timeEntries").value(1)).andExpect(jsonPath("$.mainlineItems").value(1))
            .andReturn().getResponse().getContentAsString();
        String importId = objectMapper.readTree(importedBody).get("importId").asText();

        mvc.perform(post("/api/migrations/legacy/import").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(backup))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.importId").value(importId));
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/mainlines").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/migrations/legacy/{importId}/rollback", importId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/mainlines").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/migrations/legacy/import").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(backup))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COMPLETED"));
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
