package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
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
class LegacySupplementIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
    private String token() throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email","supplement-"+UUID.randomUUID()+"@example.test","password","SyntheticOnly123","displayName","合成补迁"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    private ObjectNode oldBatch(String token,ObjectNode full) throws Exception {
        ObjectNode active=full.deepCopy();((ObjectNode)active.path("workspace")).putArray("trash");((ObjectNode)active.path("workspace")).putArray("deletionMarkers");
        ObjectNode report=(ObjectNode)json.readTree(mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(active.toString())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        report.put("checksum",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(full.toString().getBytes(StandardCharsets.UTF_8))));report.remove("recyclingVersion");
        db.update("update legacy_imports set payload=?,checksum=?,report_json=? where id=?",full.toString(),report.path("checksum").asText(),report.toString(),report.path("importId").asText());return report;
    }
    private ObjectNode request(String token,String batch) throws Exception {
        JsonNode preview=json.readTree(mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",batch).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return json.createObjectNode().put("operationKey",UUID.randomUUID().toString()).put("fingerprint",preview.path("fingerprint").asText()).put("confirmed",true);
    }
    private JsonNode apply(String token,String batch,JsonNode request) throws Exception {
        return json.readTree(mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void addsOnlyMissingTrashPreservesEditsAndWholeBatchRollbackIncludesSupplement() throws Exception {
        String token=token();ObjectNode report=oldBatch(token,LegacyRecyclingTest.fixture());String batch=report.path("importId").asText(),plan=report.path("createdPlanIds").get(0).asText();
        db.update("update plans set name=?,version=version+1 where id=?","迁移后新名称",plan);
        var live=db.queryForMap("select t.id,t.title,p.status,p.accumulated_seconds from plan_tasks t join task_progress p on p.task_id=t.id where t.plan_id=?",plan);
        String snapshot=db.queryForObject("select payload from legacy_imports where id=?",String.class,batch);
        JsonNode result=apply(token,batch,request(token,batch));assertEquals(1,result.path("plans").asInt());assertEquals(1,result.path("mainlines").asInt());assertEquals(1,result.path("tags").asInt());
        assertEquals("迁移后新名称",db.queryForObject("select name from plans where id=?",String.class,plan));
        assertEquals(live,db.queryForMap("select t.id,t.title,p.status,p.accumulated_seconds from plan_tasks t join task_progress p on p.task_id=t.id where t.plan_id=?",plan));
        assertEquals(snapshot,db.queryForObject("select payload from legacy_imports where id=?",String.class,batch));
        assertEquals(1,db.queryForObject("select count(*) from legacy_recycling_supplements where import_id=?",Integer.class,batch));
        JsonNode updated=json.readTree(db.queryForObject("select report_json from legacy_imports where id=?",String.class,batch));assertEquals(1,updated.path("recyclingVersion").asInt());assertEquals(2,updated.path("createdPlanIds").size());
        assertEquals("PAUSED",db.queryForObject("select status from mainline_items where title=? and mainline_id=?",String.class,"删除步骤",report.path("createdMainlineIds").get(0).asText()));
        mvc.perform(post("/api/migrations/legacy/{id}/rollback",batch).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        for(JsonNode id:updated.path("createdPlanIds"))assertEquals(0,db.queryForObject("select count(*) from plans where id=?",Integer.class,id.asText()));
        for(JsonNode id:updated.path("createdMainlineIds"))assertEquals(0,db.queryForObject("select count(*) from mainlines where id=?",Integer.class,id.asText()));
    }
    @Test void repeatedAndConcurrentConfirmationReturnsOneDurableReceipt() throws Exception {
        String token=token(),batch=oldBatch(token,LegacyRecyclingTest.fixture()).path("importId").asText();ObjectNode request=request(token,batch);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var threads=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<JsonNode> action=()->{gate.await();return apply(token,batch,request);};
            var first=threads.submit(action);var second=threads.submit(action);gate.countDown();assertEquals(first.get(),second.get());
        }
        assertEquals(1,db.queryForObject("select count(*) from legacy_recycling_supplements where import_id=?",Integer.class,batch));
        JsonNode first=apply(token,batch,request);request.put("operationKey",UUID.randomUUID().toString());assertEquals(first,apply(token,batch,request));
        request.put("fingerprint","0".repeat(64));
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
    }
    @Test void staleVersionAndMissingConfirmationOrWrongOwnerCannotWrite() throws Exception {
        String token=token(),other=token();JsonNode report=oldBatch(token,LegacyRecyclingTest.fixture());String batch=report.path("importId").asText();ObjectNode request=request(token,batch);
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+other).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isNotFound());
        request.put("confirmed",false);mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isBadRequest());request.put("confirmed",true);
        db.update("update plans set version=version+1 where id=?",report.path("createdPlanIds").get(0).asText());
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
        assertEquals(0,db.queryForObject("select count(*) from legacy_recycling_supplements where import_id=?",Integer.class,batch));
    }
    @Test void lateInvalidSnapshotRollsBackAllObjectsAndReceipt() throws Exception {
        String token=token();ObjectNode source=LegacyRecyclingTest.fixture();((ObjectNode)source.at("/workspace/trash/0/payload/plan")).put("endDate","2026-09-01");
        JsonNode report=oldBatch(token,source);String batch=report.path("importId").asText();ObjectNode request=request(token,batch);
        String originalReport=db.queryForObject("select report_json from legacy_imports where id=?",String.class,batch);
        String owner=db.queryForObject("select owner_id from legacy_imports where id=?",String.class,batch);
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isBadRequest());
        assertEquals(1,db.queryForObject("select count(*) from mainlines where owner_id=?",Integer.class,owner));assertEquals(1,db.queryForObject("select count(*) from plans where owner_id=?",Integer.class,owner));
        assertEquals(0,db.queryForObject("select count(*) from legacy_recycling_supplements where import_id=?",Integer.class,batch));assertEquals(originalReport,db.queryForObject("select report_json from legacy_imports where id=?",String.class,batch));
    }
    @Test void nestedRecycleAndPermanentMarkersNeverRemoveCurrentSteps() throws Exception {
        String token=token();ObjectNode source=LegacyRecyclingTest.fixture();((ObjectNode)source.at("/workspace/trash/2/payload")).put("mainlineId","removed-route");
        ((ObjectNode)source.path("workspace")).putArray("deletionMarkers").addObject().put("id","trash:mainline-item:live-step").put("type","mainline-item").put("entityId","live-step").put("parentId","live-route").put("deletedAt","2026-10-20T05:00:00Z");
        JsonNode report=oldBatch(token,source);String batch=report.path("importId").asText();apply(token,batch,request(token,batch));
        assertEquals(1,db.queryForObject("select count(*) from mainline_items where mainline_id=? and deleted_at is null",Integer.class,report.path("createdMainlineIds").get(0).asText()));
        JsonNode updated=json.readTree(db.queryForObject("select report_json from legacy_imports where id=?",String.class,batch));
        assertEquals(2,db.queryForObject("select count(*) from mainline_items where mainline_id=?",Integer.class,updated.path("createdMainlineIds").get(1).asText()));
    }
    @Test void oldReceiptCannotBeReplayedAfterRollbackOrNewImport() throws Exception {
        String token=token();ObjectNode source=LegacyRecyclingTest.fixture();String batch=oldBatch(token,source).path("importId").asText();ObjectNode request=request(token,batch);apply(token,batch,request);
        mvc.perform(post("/api/migrations/legacy/{id}/rollback",batch).header("Authorization","Bearer "+token)).andExpect(status().isOk());
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
        mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(source.toString())).andExpect(status().isCreated());
        mvc.perform(post("/api/migrations/legacy/{id}/recycling-supplement",batch).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(request.toString())).andExpect(status().isConflict());
    }
}
