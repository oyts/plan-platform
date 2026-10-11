package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TodoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private String register() throws Exception {
        var body = mapper.writeValueAsString(Map.of("email", "todo-" + UUID.randomUUID() + "@example.test",
            "password", "TestPassword123!", "displayName", "待办验收"));
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(result).get("accessToken").asText();
    }
    private JsonNode create(String token) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/todos").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"临时事项\",\"dueDate\":\"2026-10-12\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("临时事项"))
            .andExpect(jsonPath("$.done").value(false)).andReturn().getResponse().getContentAsString());
    }
    private String update(JsonNode item, boolean done, boolean deleted) throws Exception {
        return mapper.writeValueAsString(Map.of("title", "更新事项", "note", "保留的备注", "dueDate", "2026-10-12",
            "done", done, "deleted", deleted, "version", item.get("version").asLong()));
    }
    @Test void isolatesUsersAndDoesNotAffectPlansOrRoutes() throws Exception {
        String owner = register(), other = register(); var item = create(owner);
        mvc.perform(get("/api/todos").param("from", "2026-10-12").param("to", "2026-10-12")
            .header("Authorization", "Bearer " + other)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(put("/api/todos/{id}", item.get("id").asText()).header("Authorization", "Bearer " + other)
            .contentType(MediaType.APPLICATION_JSON).content(update(item, true, false))).andExpect(status().isNotFound());
        for (String path : new String[]{"/api/plans", "/api/mainlines"}) mvc.perform(get(path)
            .header("Authorization", "Bearer " + owner)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/todos").param("from", "2026-10-12").param("to", "2026-10-12"))
            .andExpect(status().isUnauthorized());
    }
    @Test void requiresVersionAndRejectsStaleChangesIncludingDeletion() throws Exception {
        String owner = register(); var item = create(owner); String id = item.get("id").asText();
        mvc.perform(put("/api/todos/{id}", id).header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\":\"无版本\",\"dueDate\":\"2026-10-12\",\"done\":false,\"deleted\":false}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/todos/{id}", id).header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
            .content(update(item, true, false))).andExpect(status().isOk()).andExpect(jsonPath("$.done").value(true));
        mvc.perform(put("/api/todos/{id}", id).header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
            .content(update(item, false, true))).andExpect(status().isConflict());
    }
    @Test void movesToTrashAndRestoresWithoutLosingContent() throws Exception {
        String owner = register(); var item = create(owner); String id = item.get("id").asText();
        var deleted = mapper.readTree(mvc.perform(put("/api/todos/{id}", id).header("Authorization", "Bearer " + owner)
            .contentType(MediaType.APPLICATION_JSON).content(update(item, true, true))).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/todos").param("from", "2026-10-12").param("to", "2026-10-12")
            .header("Authorization", "Bearer " + owner)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/todos").param("from", "2026-10-12").param("to", "2026-10-12").param("deleted", "true")
            .header("Authorization", "Bearer " + owner)).andExpect(jsonPath("$[0].note").value("保留的备注"));
        mvc.perform(put("/api/todos/{id}", id).header("Authorization", "Bearer " + owner)
            .contentType(MediaType.APPLICATION_JSON).content(update(deleted, true, false))).andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(false)).andExpect(jsonPath("$.done").value(true));
    }
    @Test void repeatsCreateSafelyWithSameIdempotencyKey() throws Exception {
        String owner = register(), key = UUID.randomUUID().toString();
        String body = "{\"title\":\"幂等临时事项\",\"dueDate\":\"2026-10-12\"}";
        var first = mvc.perform(post("/api/todos").header("Authorization", "Bearer " + owner).header("X-Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/todos").header("Authorization", "Bearer " + owner).header("X-Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
            .andExpect(header().string("X-Idempotent-Replay", "true")).andExpect(content().json(first));
        mvc.perform(get("/api/todos").param("from", "2026-10-12").param("to", "2026-10-12")
            .header("Authorization", "Bearer " + owner)).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void validatesTitlesDatesAndPublishesCapability() throws Exception {
        String owner = register();
        for (String body : new String[]{"{\"title\":\" \",\"dueDate\":\"2026-10-12\"}", "{\"title\":\"无日期\"}"})
            mvc.perform(post("/api/todos").header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/todos").param("from", "2026-10-01").param("to", "2026-11-01")
            .header("Authorization", "Bearer " + owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/config")).andExpect(jsonPath("$.todoEnabled").value(true));
    }
}
