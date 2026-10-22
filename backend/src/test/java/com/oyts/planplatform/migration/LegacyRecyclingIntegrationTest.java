package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class LegacyRecyclingIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
    private String token() throws Exception {
        String email="recycle-"+UUID.randomUUID()+"@example.test";
        String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(java.util.Map.of("email",email,"password","SyntheticOnly123","displayName","合成回收站验收"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }
    private JsonNode imported(String token,JsonNode payload) throws Exception {
        return json.readTree(mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(payload.toString())).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }
    @Test void recyclesSnapshotsPreservesOriginalTimestampAndDoesNotResumeOldTimer() throws Exception {
        String token=token();ObjectNode payload=LegacyRecyclingTest.fixture();
        ((ObjectNode)payload.at("/workspace/trash/0/payload/progress/removed-task")).putArray("timeEntries").addObject()
            .put("startedAt","2026-10-02T08:58:00Z").put("endedAt","2026-10-02T09:00:00Z").put("seconds",120).put("source","timer");
        String original=payload.toString();
        mvc.perform(post("/api/migrations/legacy/preview").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(original))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(true)).andExpect(jsonPath("$.plans").value(2)).andExpect(jsonPath("$.mainlines").value(2))
            .andExpect(jsonPath("$.timeEntries").value(1));
        JsonNode report=imported(token,payload);String batch=report.path("importId").asText();
        assertEquals(json.readTree(original),json.readTree(db.queryForObject("select payload from legacy_imports where id=?",String.class,batch)));
        String removedPlan=report.path("createdPlanIds").get(1).asText();
        assertEquals(java.time.Instant.parse("2026-10-20T01:00:00Z"),db.queryForObject("select deleted_at from plans where id=?",java.sql.Timestamp.class,removedPlan).toInstant());
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        String liveLine=report.path("createdMainlineIds").get(0).asText();
        var row=db.queryForMap("select status,started_at,deleted_at,accumulated_seconds from mainline_items where mainline_id=? and title=?",liveLine,"删除步骤");
        assertEquals("PAUSED",row.get("status"));assertNull(row.get("started_at"));assertNotNull(row.get("deleted_at"));assertEquals(60L,((Number)row.get("accumulated_seconds")).longValue());
        mvc.perform(post("/api/trash/PLAN/{id}/restore",removedPlan).header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(2));
    }
    @Test void repeatImportAndRollbackRemainIdempotentAndOwnerIsolated() throws Exception {
        String token=token(),other=token();ObjectNode payload=LegacyRecyclingTest.fixture();JsonNode first=imported(token,payload);
        assertEquals(first.path("importId"),imported(token,payload).path("importId"));String batch=first.path("importId").asText();
        mvc.perform(post("/api/migrations/legacy/{id}/rollback",batch).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        for(int i=0;i<2;i++)mvc.perform(post("/api/migrations/legacy/{id}/rollback",batch).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ROLLED_BACK"));
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(0));
        assertEquals(batch,imported(token,payload).path("importId").asText());
    }
    @Test void malformedSnapshotFailsPreviewAndLateWriteFailureRollsBackWholeBatch() throws Exception {
        String token=token();ObjectNode malformed=LegacyRecyclingTest.fixture();((ObjectNode)malformed.at("/workspace/trash/2/payload")).put("mainlineId","missing");
        mvc.perform(post("/api/migrations/legacy/preview").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(malformed.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ready").value(false));
        mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(malformed.toString())).andExpect(status().isBadRequest());
        ObjectNode late=LegacyRecyclingTest.fixture();((ObjectNode)late.at("/workspace/trash/0/payload/plan")).put("endDate","2026-09-01");
        mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(late.toString())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/migrations/legacy").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void concurrentSameBackupCreatesOnlyOneBatchAndOneSetOfRecords() throws Exception {
        String token=token();ObjectNode payload=LegacyRecyclingTest.fixture();
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var threads=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Callable<String> action=()->{gate.await();return imported(token,payload).path("importId").asText();};
            var first=threads.submit(action);var second=threads.submit(action);gate.countDown();
            assertEquals(first.get(30,java.util.concurrent.TimeUnit.SECONDS),second.get(30,java.util.concurrent.TimeUnit.SECONDS));
        }
        mvc.perform(get("/api/migrations/legacy").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void savedSnapshotCannotBypassSingleCompletedBatchGuard() throws Exception {
        String token=token();ObjectNode saved=LegacyRecyclingTest.fixture(),other=saved.deepCopy();other.put("planVersion","other-synthetic");
        mvc.perform(post("/api/migrations/legacy-snapshot").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(saved.toString())).andExpect(status().isCreated());
        imported(token,other);
        mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(saved.toString())).andExpect(status().isConflict());
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(1));
    }
}
