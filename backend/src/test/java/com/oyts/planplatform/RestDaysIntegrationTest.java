package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RestDaysIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void createsAndEditsRestDaysWithVersionAndOwnerProtection() throws Exception {
        String token = register("rest-owner@example.test");
        String other = register("rest-other@example.test");
        JsonNode plan = create(token, ",\"restDays\":[7,1]");
        String id = plan.get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals("[1,7]", plan.get("restDays").toString());
        String body = updateBody(plan, List.of(3));
        mvc.perform(put("/api/plans/{id}", id).header("Authorization", "Bearer " + other)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(put("/api/plans/{id}", id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.restDays[0]").value(3));
        mvc.perform(put("/api/plans/{id}", id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].restDays[0]").value(3));
    }

    @Test
    void omissionPreservesRestDaysButEmptyListClearsThemAndTasksRemain() throws Exception {
        String token = register("rest-compat@example.test");
        JsonNode plan = create(token, ",\"restDays\":[6,7]");
        String id = plan.get("id").asText();
        mvc.perform(post("/api/plans/{id}/tasks", id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"date":"2026-10-03","title":"周六任务仍存在","category":"JAVA","plannedMinutes":20,"acceptance":[],"required":true,"sortOrder":0}
                """)).andExpect(status().isCreated());
        String preserved = mvc.perform(put("/api/plans/{id}", id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"旧客户端编辑","startDate":"2026-10-01","endDate":"2026-10-31"}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.restDays.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(put("/api/plans/{id}", id).header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(updateBody(mapper.readTree(preserved), List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.restDays").isEmpty()).andExpect(jsonPath("$.taskCount").value(1));
        mvc.perform(get("/api/plans/{id}/tasks", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("周六任务仍存在"));
        org.junit.jupiter.api.Assertions.assertTrue(create(token, "").get("restDays").isEmpty());
    }

    @Test
    void rejectsInvalidOrDuplicateWeekdaysAndPublishesCapability() throws Exception {
        String token = register("rest-validation@example.test");
        for (String days : List.of("[0]", "[8]", "[null]", "[1,1]", "[1,2,3,4,5,6,7,7]")) {
            mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(createBody(",\"restDays\":" + days)))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/plans").header("Authorization", "Bearer " + token)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/auth/config")).andExpect(status().isOk()).andExpect(jsonPath("$.planRestDaysEnabled").value(true));
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", "TestPassword123!", "displayName", "休息日测试"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accessToken").asText();
    }
    private String createBody(String extra) {
        return "{\"name\":\"测试计划\",\"startDate\":\"2026-10-01\",\"endDate\":\"2026-10-31\"" + extra + "}";
    }
    private JsonNode create(String token, String extra) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/plans").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(createBody(extra))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }
    private String updateBody(JsonNode plan, List<Integer> days) throws Exception {
        return mapper.writeValueAsString(Map.of("name", plan.get("name").asText(), "startDate", plan.get("startDate").asText(),
            "endDate", plan.get("endDate").asText(), "version", plan.get("version").asLong(), "restDays", days));
    }
}
