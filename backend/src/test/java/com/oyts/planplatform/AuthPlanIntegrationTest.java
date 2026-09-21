package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
class AuthPlanIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository users;

    @Test
    void registrationPlanCreationAndUserIsolationWorkTogether() throws Exception {
        String ownerToken = register("owner@example.com", "计划负责人");
        String planBody = mvc.perform(post("/api/plans")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Java 求职计划","description":"算法、八股与项目实践","startDate":"2026-08-10","endDate":"2026-09-30","overallTagName":"求职总投入","overallTagColor":"#002FA7"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Java 求职计划"))
            .andExpect(jsonPath("$.taskCount").value(0)).andReturn().getResponse().getContentAsString();
        String planId = objectMapper.readTree(planBody).get("id").asText();

        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Java 求职计划"));

        String friendToken = register("friend@example.com", "朋友账号");
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + friendToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/plans/{planId}/tasks", planId).header("Authorization", "Bearer " + friendToken))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/plans/{planId}/tags", planId).header("Authorization", "Bearer " + friendToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"越权标签\",\"color\":\"#000000\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(put("/api/plans/{planId}", planId).header("Authorization", "Bearer " + friendToken)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"越权修改","description":"","startDate":"2026-08-10","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7","version":0}
                    """))
            .andExpect(status().isNotFound());

        String lineBody = mvc.perform(post("/api/mainlines").header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"私有主线\",\"description\":\"\",\"color\":\"#002FA7\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String lineId = objectMapper.readTree(lineBody).get("id").asText();
        mvc.perform(post("/api/mainlines/{lineId}/items", lineId).header("Authorization", "Bearer " + friendToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"越权步骤\",\"description\":\"\",\"acceptance\":[],\"plannedMinutes\":10,\"sortOrder\":0}"))
            .andExpect(status().isNotFound());
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
    void idempotencyKeysReplayCompletedWritesAndRejectDifferentPayloads() throws Exception {
        String token = register("idempotency@example.com", "幂等测试账号");
        String key = "plan-create-idempotency-001";
        String body = """
            {"name":"幂等计划","description":"只创建一次","startDate":"2026-09-22","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7"}
            """;
        MvcResult created = mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        String planId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(header().string("X-Idempotent-Replay", "true"))
            .andExpect(jsonPath("$.id").value(planId));
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .header("X-Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body.replace("幂等计划", "不同计划")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("同一操作键不能用于不同请求内容。"));
    }

    @Test
    void profileAndPasswordChangesProtectTheAccountLifecycle() throws Exception {
        String token = register("account@example.com", "账户测试账号");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/auth/me").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"更新后的名称\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("更新后的名称"));
        mvc.perform(post("/api/auth/password").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"password123\",\"newPassword\":\"newpassword456\"}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"account@example.com\",\"password\":\"password123\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"account@example.com\",\"password\":\"newpassword456\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.displayName").value("更新后的名称"));
        mvc.perform(post("/api/auth/password-reset/request").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"not-an-account@example.com\"}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void onlyAdministratorsCanCreateOrReadInvitations() throws Exception {
        String initialToken = register("admin@example.com", "管理员账号");
        var administrator = users.findByEmailIgnoreCase("admin@example.com").orElseThrow(); administrator.makeAdmin(); users.save(administrator);
        String adminToken = login("admin@example.com", "password123");
        mvc.perform(post("/api/admin/invitations").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invited@example.com\",\"expiresInHours\":24}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("invited@example.com"));
        mvc.perform(get("/api/admin/invitations").header("Authorization", "Bearer " + initialToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void longPlanTasksCanBePagedAndDeferredIntoTheCatchUpRail() throws Exception {
        String token = register("schedule@example.com", "日程测试账号");
        JsonNode plan = objectMapper.readTree(mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"长计划","description":"","startDate":"2026-09-01","endDate":"2026-10-31","overallTagName":"总计","overallTagColor":"#002FA7"}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String planId = plan.get("id").asText();
        JsonNode task = objectMapper.readTree(mvc.perform(post("/api/plans/{planId}/tasks", planId)
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("""
                    {"date":"2026-09-18","plannedMinutes":45,"category":"JAVA","title":"可补做任务","description":"","acceptance":[],"resourceUrl":"","required":true,"sortOrder":0}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String taskId = task.get("id").asText();

        mvc.perform(get("/api/plans/{planId}/tasks/page", planId).header("Authorization", "Bearer " + token)
                .param("from", "2026-09-15").param("to", "2026-09-21").param("page", "0").param("size", "5"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("可补做任务"));
        mvc.perform(post("/api/tasks/{taskId}/defer", taskId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{" + "\"deferTo\":\"2026-09-20\",\"note\":\"周末补做\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DEFERRED"))
            .andExpect(jsonPath("$.deferTo").value("2026-09-20"));
        mvc.perform(get("/api/tasks/carryover").header("Authorization", "Bearer " + token)
                .param("from", "2026-09-20").param("to", "2026-09-20"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(taskId));
        mvc.perform(get("/api/plans/{planId}/stats/daily", planId).header("Authorization", "Bearer " + token)
                .param("from", "2026-09-18").param("to", "2026-09-20").param("page", "0").param("size", "2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.content[0].date").value("2026-09-18"));
    }

    @Test
    void milestonesTemplatesAndRecurringTasksArePrivateAndUsable() throws Exception {
        String token = register("studio@example.com", "工作台测试账号");
        JsonNode plan = objectMapper.readTree(mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"工作台计划","description":"","startDate":"2026-09-01","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7"}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String planId = plan.get("id").asText();
        JsonNode template = objectMapper.readTree(mvc.perform(post("/api/templates").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"算法日练","category":"ALGORITHM","title":"一道算法题","description":"记录思路","plannedMinutes":30,"acceptance":["提交笔记"],"resourceUrl":"","required":true}
                    """)).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("算法日练"))
            .andReturn().getResponse().getContentAsString());
        String templateId = template.get("id").asText();
        mvc.perform(post("/api/templates/{templateId}/instantiate", templateId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{" + "\"planId\":\"" + planId + "\",\"date\":\"2026-09-01\",\"sortOrder\":0}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.templateId").value(templateId));
        JsonNode recurring = objectMapper.readTree(mvc.perform(post("/api/plans/{planId}/tasks/recurring", planId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"task":{"date":"2026-09-02","plannedMinutes":30,"category":"ALGORITHM","title":"重复练习","description":"","acceptance":[],"resourceUrl":"","required":true,"sortOrder":1},"mode":"WEEKDAYS","until":"2026-09-04"}
                    """))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].recurrenceId").isNotEmpty())
            .andReturn().getResponse().getContentAsString());
        String recurrenceId = recurring.get(0).get("recurrenceId").asText();
        mvc.perform(get("/api/plans/{planId}/recurrences", planId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].recurrenceId").value(recurrenceId)).andExpect(jsonPath("$[0].taskCount").value(3));
        mvc.perform(post("/api/plans/{planId}/recurrences/{recurrenceId}/stop", planId, recurrenceId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"from\":\"2026-09-03\",\"reason\":\"本轮结束\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stoppedCount").value(2));
        mvc.perform(get("/api/plans/{planId}/tasks/export", planId).header("Authorization", "Bearer " + token).param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("重复练习")));
        JsonNode milestone = objectMapper.readTree(mvc.perform(post("/api/plans/{planId}/milestones", planId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"第一阶段验收","date":"2026-09-05","description":"检查基础完成度","acceptance":["任务完成","复盘已写"]}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String milestoneId = milestone.get("id").asText();
        mvc.perform(put("/api/milestones/{milestoneId}/progress", milestoneId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checks\":[true,true],\"note\":\"已验收\",\"version\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.checks[1]").value(true)).andExpect(jsonPath("$.note").value("已验收"));
        mvc.perform(get("/api/plans/{planId}/milestones", planId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("第一阶段验收"));
    }

    @Test
    void reflectionLedgerAndRecycleBinKeepPersonalRecordsRecoverable() throws Exception {
        String token = register("evidence@example.com", "证据测试账号");
        JsonNode plan = objectMapper.readTree(mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"name":"证据计划","description":"","startDate":"2026-09-01","endDate":"2026-09-30","overallTagName":"总计","overallTagColor":"#002FA7"}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String planId = plan.get("id").asText();
        JsonNode task = objectMapper.readTree(mvc.perform(post("/api/plans/{planId}/tasks", planId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"date":"2026-09-03","plannedMinutes":20,"category":"JAVA","title":"可恢复任务","description":"","acceptance":[],"resourceUrl":"","required":true,"sortOrder":0}
                    """)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String taskId = task.get("id").asText();
        mvc.perform(post("/api/tasks/{taskId}/manual-time", taskId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"seconds\":600,\"version\":0}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/time-entries").header("Authorization", "Bearer " + token).param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].taskTitle").value("可恢复任务"));
        mvc.perform(get("/api/time-entries").header("Authorization", "Bearer " + token).param("from", "2026-09-01").param("to", "2026-09-30").param("planId", planId).param("source", "MANUAL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].source").value("MANUAL"));
        mvc.perform(put("/api/plans/{planId}/reflections/{date}", planId, "2026-09-03").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"completedSummary\":\"完成任务\",\"unfinishedSummary\":\"\",\"blockers\":\"\",\"firstTaskTomorrow\":\"复习\",\"energy\":4,\"note\":\"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.energy").value(4));
        mvc.perform(get("/api/plans/{planId}/reflections/page", planId).header("Authorization", "Bearer " + token).param("from", "2026-09-01").param("to", "2026-09-30"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].completedSummary").value("完成任务"));
        mvc.perform(get("/api/plans/{planId}/reflections/export", planId).header("Authorization", "Bearer " + token).param("from", "2026-09-01").param("to", "2026-09-30").param("format", "MARKDOWN"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("# 证据计划：复盘记录")));
        mvc.perform(delete("/api/tasks/{taskId}", taskId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trash").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("TASK")).andExpect(jsonPath("$[0].id").value(taskId));
        mvc.perform(post("/api/trash/TASK/{taskId}/restore", taskId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/plans/{planId}/tasks", planId).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(taskId));
        mvc.perform(delete("/api/plans/{planId}", planId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trash").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].type").value("PLAN"));
        mvc.perform(post("/api/trash/PLAN/{planId}/restore", planId).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(planId));
    }

    @Test
    void revokedRefreshTokenReturnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody("logout@example.com", "password123", "退出测试账号"));
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("HttpOnly"), org.hamcrest.Matchers.containsString("SameSite=Strict"))))
            .andReturn();
        Cookie refreshCookie = registration.getResponse().getCookie("plan_refresh");

        mvc.perform(post("/api/auth/logout").cookie(refreshCookie))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshCookieRotatesAndOldCookieCannotBeReused() throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody("rotate@example.com", "password123", "轮换测试账号"));
        MvcResult registration = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn();
        Cookie firstCookie = registration.getResponse().getCookie("plan_refresh");
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(firstCookie))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        Cookie secondCookie = refreshed.getResponse().getCookie("plan_refresh");
        org.junit.jupiter.api.Assertions.assertNotEquals(firstCookie.getValue(), secondCookie.getValue());
        mvc.perform(post("/api/auth/refresh").cookie(firstCookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(secondCookie)).andExpect(status().isOk());
    }

    @Test
    void loginRateLimitAndProtectedSessionEndpointsWork() throws Exception {
        String token = register("limited@example.com", "限流测试账号");
        String invalid = "{\"email\":\"limited@example.com\",\"password\":\"wrong-pass-1\"}";
        for (int attempt = 0; attempt < 3; attempt++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(invalid))
            .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/auth/sessions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
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
              "gates":{"G1":{"gateId":"G1","checks":{"完成迁移":true},"note":"旧闸门备注","reviewedAt":"2026-09-17T12:00:00Z"}},
              "workspace":{"activePlanId":"summer-plan","defaultPlanTags":[{"id":"all","name":"总计","color":"#002FA7","isOverall":true}],"customPlans":[],"customMainlines":[],"taskTemplates":[{"id":"template-a","name":"每日复习","title":"复习知识点","description":"","startTime":"09:00","endTime":"09:30","plannedMinutes":30,"acceptance":["完成记录"],"resourceUrl":"","createdAt":"2026-09-17T12:00:00Z","updatedAt":"2026-09-17T12:00:00Z"}],"trash":[],"deletionMarkers":[],"updatedAt":"2026-09-17T12:00:00Z"}
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
        JsonNode importResult = objectMapper.readTree(importedBody);
        String importId = importResult.get("importId").asText();
        String importedPlanId = importResult.get("createdPlanIds").get(0).asText();
        mvc.perform(get("/api/templates").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("每日复习"));
        mvc.perform(get("/api/plans/{planId}/milestones", importedPlanId).header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("G1")).andExpect(jsonPath("$[0].checks[0]").value(true));

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

    private String login(String email, String password) throws Exception {
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private record RegisterBody(String email, String password, String displayName) {}
}

@SpringBootTest(properties = "app.security.registration-enabled=false")
@AutoConfigureMockMvc
class RegistrationPolicyIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void closedRegistrationIsPublishedAndEnforcedByBackend() throws Exception {
        mvc.perform(get("/api/auth/config"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.registrationEnabled").value(false));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"closed@example.com","password":"password123","displayName":"关闭注册测试"}
                """))
            .andExpect(status().isForbidden());
    }
}
