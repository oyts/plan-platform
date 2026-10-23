package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicitly exercise intake tolerance; no new strict validator is installed in production. */
@SpringBootTest @AutoConfigureMockMvc
class LegacyWireContractIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
    private String token()throws Exception{
        return json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email","legacy-wire-"+UUID.randomUUID()+"@example.test","password","SyntheticOnly123","displayName","旧格式合成契约")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray()).path("accessToken").asText();
    }
    private JsonNode send(String token,String path,JsonNode body,int status)throws Exception{
        return json.readTree(mvc.perform(post("/api/migrations/"+path).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().is(status)).andReturn().getResponse().getContentAsByteArray());
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6})
    void sixLegacyVersionsImportMissingHistoricalFieldsAndRetainUnknownSnapshotFields(int version)throws Exception{
        String token=token();ObjectNode payload=(ObjectNode)json.readTree("""
            {"schemaVersion":6,"planVersion":"synthetic-legacy","progress":{"task-a":{"status":"pending"}},"reflections":{},"gates":{},
             "migrationCatalog":{"defaultPlan":{"id":"plan-a","name":"合成旧计划","startDate":"2026-10-01","endDate":"2026-10-02","tags":[],"tasks":[{"id":"task-a","date":"2026-10-01","title":"合成旧任务","plannedMinutes":20}]}},
             "workspace":{},"unknownHistoricalMetadata":{"preserved":"合成未知字段"}}
            """);
        payload.put("schemaVersion",version);if(version==1)payload.remove("workspace");
        // Versions2/3/4 deliberately lack customMainlines/defaultPlanTags/trash/markers.
        assertThat(send(token,"legacy/preview",payload,200).path("ready").asBoolean()).isTrue();
        JsonNode result=send(token,"legacy/import",payload,201);String batch=result.path("importId").asText();
        assertThat(result.path("createdPlanIds").size()).isEqualTo(1);
        assertThat(send(token,"legacy/import",payload,201).path("importId").asText()).isEqualTo(batch);
        JsonNode retained=json.readTree(db.queryForObject("select payload from legacy_imports where id=?",String.class,batch));
        assertThat(retained).isEqualTo(payload);
        assertThat(retained.at("/progress/task-a").has("timeEntries")).isFalse();
        assertThat(retained.at("/progress/task-a").has("acceptanceChecks")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"[]","17","\"synthetic unsupported text\""})
    void previewFailureAndRawSnapshotSuccessAreNotConfusedWithImport(String raw)throws Exception{
        String token=token();JsonNode payload=json.readTree(raw);
        assertThat(send(token,"legacy/preview",payload,200).path("ready").asBoolean()).isFalse();
        JsonNode saved=send(token,"legacy-snapshot",payload,201);
        assertThat(json.readTree(db.queryForObject("select payload from legacy_imports where id=?",String.class,saved.path("snapshotId").asText()))).isEqualTo(payload);
        send(token,"legacy/import",payload,400);
        mvc.perform(get("/api/plans").header("Authorization","Bearer "+token)).andExpect(jsonPath("$.length()").value(0));
    }
}
