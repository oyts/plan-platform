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
class LegacyBackfillPreviewIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    private String token() throws Exception {
        String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("email","backfill-"+UUID.randomUUID()+"@example.test",
                "password","SyntheticOnly123","displayName","合成补迁预检"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("accessToken").asText();
    }
    private JsonNode imported(String token,JsonNode source) throws Exception {
        return json.readTree(mvc.perform(post("/api/migrations/legacy/import").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(source.toString())).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString());
    }
    // Construct a synthetic pre-conversion batch: the old writer imported only active parents.
    // This is not an old-image/database compatibility acceptance claim.
    private JsonNode oldBatch(String token) throws Exception {
        return oldBatch(token,LegacyRecyclingTest.fixture());
    }
    private JsonNode oldBatch(String token,ObjectNode full) throws Exception {
        ObjectNode active=full.deepCopy();
        ((ObjectNode)active.path("workspace")).putArray("trash");
        ((ObjectNode)active.path("workspace")).putArray("deletionMarkers");
        ObjectNode report=(ObjectNode)imported(token,active);
        String checksum=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(full.toString().getBytes(StandardCharsets.UTF_8)));
        report.put("checksum",checksum);report.remove("recyclingVersion");
        db.update("update legacy_imports set payload=?,checksum=?,report_json=? where id=?",
            full.toString(),checksum,report.toString(),report.path("importId").asText());
        return report;
    }
    private JsonNode preview(String token,String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id)
            .header("Authorization","Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void oldBatchPreviewIsReadOnlyStableAndIncludesCurrentParentVersions() throws Exception {
        String token=token();JsonNode report=oldBatch(token);String id=report.path("importId").asText();
        var before=db.queryForMap("select payload,report_json,status from legacy_imports where id=?",id);
        int plans=db.queryForObject("select count(*) from plans",Integer.class);
        JsonNode first=preview(token,id);
        assertFalse(first.path("alreadyIncluded").asBoolean());assertTrue(first.path("writeSupported").asBoolean());
        assertEquals(4,first.path("entries").size());assertEquals(first,preview(token,id));
        assertEquals(2,java.util.stream.StreamSupport.stream(first.path("entries").spliterator(),false)
            .filter(n->n.path("action").asText().equals("ATTACH_DELETED_CHILD")).count());
        assertEquals(before,db.queryForMap("select payload,report_json,status from legacy_imports where id=?",id));
        assertEquals(plans,db.queryForObject("select count(*) from plans",Integer.class));
        String planId=report.path("createdPlanIds").get(0).asText();
        db.update("update plans set version=version+1,name=? where id=?","迁移后本人已编辑",planId);
        JsonNode after=preview(token,id);
        assertNotEquals(first.path("fingerprint"),after.path("fingerprint"));
        assertFalse(after.toString().contains("迁移后本人已编辑"));
        assertEquals("迁移后本人已编辑",db.queryForObject("select name from plans where id=?",String.class,planId));
    }
    @Test void currentImportsAreMarkedIncludedAndRollbackRetainsAlgorithmVersion() throws Exception {
        String token=token();JsonNode report=imported(token,LegacyRecyclingTest.fixture());String id=report.path("importId").asText();
        assertEquals(1,report.path("recyclingVersion").asInt());JsonNode result=preview(token,id);
        assertTrue(result.path("alreadyIncluded").asBoolean());assertTrue(result.path("entries").isEmpty());
        mvc.perform(post("/api/migrations/legacy/{id}/rollback",id).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recyclingVersion").value(1));
        mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
    }
    @Test void otherOwnersAndMissingParentsAreRejectedWithoutWrites() throws Exception {
        String token=token(),other=token();JsonNode report=oldBatch(token);String id=report.path("importId").asText();
        mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+other)).andExpect(status().isNotFound());
        String plan=report.path("createdPlanIds").get(0).asText();
        db.update("update plans set deleted_at=CURRENT_TIMESTAMP where id=?",plan);
        mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
        assertEquals("COMPLETED",db.queryForObject("select status from legacy_imports where id=?",String.class,id));
    }
    @Test void corruptMappingAndSnapshotCannotBeGuessed() throws Exception {
        String token=token();ObjectNode report=(ObjectNode)oldBatch(token);String id=report.path("importId").asText();
        report.putArray("createdPlanIds");
        db.update("update legacy_imports set report_json=? where id=?",report.toString(),id);
        mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
        db.update("update legacy_imports set payload=? where id=?","{}",id);
        mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
    }
    @Test void nestedSnapshotAndPermanentMarkersDoNotMutateCurrentChildren() throws Exception {
        String token=token();ObjectNode full=LegacyRecyclingTest.fixture();
        ((ObjectNode)full.at("/workspace/trash/2/payload")).put("mainlineId","removed-route");
        ((ObjectNode)full.path("workspace")).putArray("deletionMarkers").addObject()
            .put("id","trash:mainline-item:live-step").put("type","mainline-item").put("entityId","live-step")
            .put("parentId","live-route").put("deletedAt","2026-10-20T05:00:00Z");
        JsonNode report=oldBatch(token,full),result=preview(token,report.path("importId").asText());
        assertEquals(1,result.path("retainedPermanentMarkers").asInt());
        JsonNode nested=java.util.stream.StreamSupport.stream(result.path("entries").spliterator(),false)
            .filter(n->n.path("legacyId").asText().equals("removed-step")).findFirst().orElseThrow();
        assertEquals("IN_PARENT_SNAPSHOT",nested.path("action").asText());assertTrue(nested.path("targetParentId").isNull());
        assertEquals(1,db.queryForObject("select count(*) from mainline_items where mainline_id=? and deleted_at is null",Integer.class,
            report.path("createdMainlineIds").get(0).asText()));
    }
    @Test void unknownAlgorithmVersionCannotSilentlyBecomeOldFormat() throws Exception {
        String token=token();ObjectNode report=(ObjectNode)oldBatch(token);String id=report.path("importId").asText();
        for(JsonNode version:List.of(json.getNodeFactory().textNode("bad"),json.getNodeFactory().numberNode(2))){
            report.set("recyclingVersion",version);db.update("update legacy_imports set report_json=? where id=?",report.toString(),id);
            mvc.perform(get("/api/migrations/legacy/{id}/recycling-preview",id).header("Authorization","Bearer "+token)).andExpect(status().isConflict());
        }
    }
}
