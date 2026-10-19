package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"app.ai.enabled=false","app.ai.worker-enabled=false"}) @AutoConfigureMockMvc
class AiWorkbenchIntegrationTest {
    @Autowired AiWorkbenchService workbench; @Autowired AiConversationService sessions; @Autowired JdbcTemplate db;
    @Autowired UserAccountRepository users; @Autowired ObjectMapper mapper; @Autowired MockMvc mvc;
    String owner,other;
    @BeforeEach void setup(){owner=users.saveAndFlush(new UserAccount("workbench-"+UUID.randomUUID()+"@example.test","unused-hash","合成草稿用户")).getId();other=users.saveAndFlush(new UserAccount("workbench-"+UUID.randomUUID()+"@example.test","unused-hash","另一个合成用户")).getId();}
    String draft(AiDrafts.Mode mode) throws Exception {return mapper.writeValueAsString(new AiDrafts.Draft(1,mode,0,"参考规划",mode==AiDrafts.Mode.PLAN?LocalDate.of(2026,10,19):null,mode==AiDrafts.Mode.PLAN?LocalDate.of(2026,10,20):null,List.of(),List.of(),List.of(new AiDrafts.Item("first","练习","",mode==AiDrafts.Mode.ROUTE?null:LocalDate.of(2026,10,19),15,null,List.of()))));}
    String content(AiConversationService.Session session,String draft) throws Exception {
        var c=mapper.createObjectNode();c.put("schemaVersion",1);if(session.mode()==null)c.putNull("mode");else c.put("mode",session.mode());c.put("message","尚未发送的要求");c.put("importJson","");c.set("draft",draft==null?mapper.nullNode():mapper.readTree(draft));c.put("sourceSessionId",session.id());c.put("sourceVersion",session.version());return mapper.writeValueAsString(c);
    }
    AiWorkbenchService.Saved save(AiConversationService.Session session,String draft) throws Exception{return workbench.save(owner,UUID.randomUUID().toString(),"独立草稿",-1,content(session,draft));}
    @Test void persistsBeyondConversationExpiryAndResumesFreshWithoutAiOrBusinessWrites() throws Exception {
        var session=sessions.create(owner,AiDrafts.Mode.ROUTE,true);var saved=save(session,draft(AiDrafts.Mode.ROUTE));
        db.update("UPDATE ai_sessions SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),session.id());sessions.maintain();
        assertThrows(NotFoundException.class,()->sessions.read(owner,session.id()));assertEquals("参考规划",workbench.read(owner,saved.id()).content().path("draft").path("title").asText());
        var next=workbench.resume(owner,saved.id(),saved.version(),true);assertNotEquals(session.id(),next.id());assertTrue(next.turns().isEmpty());assertNotNull(next.draft());assertNull(next.applied());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM mainlines WHERE owner_id=?",Integer.class,owner));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_quota_requests WHERE owner_id=?",Integer.class,owner));
    }
    @Test void isolatesOwnersChecksVersionAndReturnsPrivateNoStore() throws Exception {
        var s=sessions.create(owner,AiDrafts.Mode.TODO,true);var saved=save(s,draft(AiDrafts.Mode.TODO));assertThrows(NotFoundException.class,()->workbench.read(other,saved.id()));
        workbench.save(owner,saved.id(),"新名称",0,content(s,draft(AiDrafts.Mode.TODO)));
        assertThrows(ConflictException.class,()->workbench.save(owner,saved.id(),"不能覆盖",0,content(s,null)));
        mvc.perform(get("/api/ai/saved-drafts/{id}",saved.id()).with(jwt().jwt(j->j.subject(owner)))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/ai/saved-drafts/{id}",saved.id()).with(jwt().jwt(j->j.subject(other)))).andExpect(status().isNotFound());
        mvc.perform(put("/api/ai/saved-drafts/{id}",saved.id()).with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content("{\"name\":\"x\",\"version\":1.5,\"contentJson\":\"{}\"}")).andExpect(status().isBadRequest());
    }
    @Test void clearingSkipsPendingAndStaleAndNeverDeletesGeneratedData() throws Exception {
        var s=sessions.create(owner,AiDrafts.Mode.TODO,true);var ready=sessions.saveDraft(owner,s.id(),0,draft(AiDrafts.Mode.TODO));sessions.apply(owner,s.id(),ready.version(),true);var saved=save(s,draft(AiDrafts.Mode.TODO));
        var pending=sessions.create(owner,null,true);var p=save(pending,null);
        // Synthetic queued row: no provider/Key/network is involved.
        String reservation=UUID.randomUUID().toString();db.update("INSERT INTO ai_quota_requests(id,owner_id,operation_key,fingerprint,quota_day,state,created_at,updated_at) VALUES (?,?,?,?,?,'RESERVED',?,?)",reservation,owner,"workbench-pending-key","0".repeat(64),LocalDate.now(),Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        db.update("INSERT INTO ai_jobs(id,session_id,owner_id,operation_key,fingerprint,reservation_id,state,user_message,created_at,updated_at) VALUES (?,?,?,?,?,?,'QUEUED','合成请求',?,?)",UUID.randomUUID().toString(),pending.id(),owner,"workbench-pending-key","0".repeat(64),reservation,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        var result=workbench.clear(owner,List.of(new AiWorkbenchService.Selection(saved.id(),saved.version()),new AiWorkbenchService.Selection(p.id(),p.version())));assertEquals(List.of(saved.id()),result.deleted());assertEquals(List.of(p.id()),result.skipped());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM todos WHERE owner_id=?",Integer.class,owner));assertTrue(sessions.read(owner,pending.id()).turns().size()>0);
        assertThrows(ConflictException.class,()->workbench.resume(owner,p.id(),p.version(),true));
        var again=workbench.clear(owner,List.of(new AiWorkbenchService.Selection(p.id(),99)));assertEquals(List.of(p.id()),again.skipped());
    }
    @Test void expiredAdjustmentMustMatchFreshProgressAndNeverAppliesStaleSuggestion() throws Exception {
        var s=sessions.create(owner,AiDrafts.Mode.ROUTE,true);var ready=sessions.saveDraft(owner,s.id(),0,draft(AiDrafts.Mode.ROUTE));String target=sessions.apply(owner,s.id(),ready.version(),true).path("path").asText().split("line=")[1];
        var adjustment=sessions.createAdjustment(owner,AiDrafts.Mode.ROUTE,target,true);var saved=save(adjustment,mapper.writeValueAsString(adjustment.draft()));
        db.update("UPDATE ai_sessions SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),adjustment.id());sessions.maintain();
        assertNotNull(workbench.resume(owner,saved.id(),0,true).adjustment());
        db.update("UPDATE mainline_items SET note='新的执行记录',version=version+1 WHERE mainline_id=?",target);
        int before=sessions.list(owner).size();assertThrows(ConflictException.class,()->workbench.resume(owner,saved.id(),0,true));assertEquals(before,sessions.list(owner).size());assertNotNull(workbench.read(owner,saved.id()).content());
    }
    @Test void boundsCapacityAndRejectsUnownedSourcesOrAccidentalExecutionFlags() throws Exception {
        var s=sessions.create(owner,null,true);for(int i=0;i<30;i++)save(s,null);
        assertThrows(ConflictException.class,()->save(s,null));assertEquals(30,workbench.list(owner).size());
        var content=mapper.readTree(content(s,null));((com.fasterxml.jackson.databind.node.ObjectNode)content).put("confirmed",true);
        assertThrows(IllegalArgumentException.class,()->workbench.save(owner,UUID.randomUUID().toString(),"x",-1,mapper.writeValueAsString(content)));
        assertThrows(IllegalArgumentException.class,()->workbench.save(owner,UUID.randomUUID().toString(),"x",-1,"x".repeat(245761)));
    }
    @Test void incompleteSavedEditsCannotGenerateAndFailedResumeRollsBackSession() throws Exception {
        var s=sessions.create(owner,AiDrafts.Mode.TODO,true);var invalid=mapper.readTree(draft(AiDrafts.Mode.TODO));((com.fasterxml.jackson.databind.node.ObjectNode)invalid.path("items").get(0)).put("title","");var saved=save(s,mapper.writeValueAsString(invalid));
        int before=sessions.list(owner).size();assertThrows(AiFailure.class,()->workbench.resume(owner,saved.id(),0,true));assertEquals(before,sessions.list(owner).size());assertThrows(IllegalArgumentException.class,()->workbench.resume(owner,saved.id(),0,false));
        assertEquals("",workbench.read(owner,saved.id()).content().path("draft").path("items").get(0).path("title").asText());
    }
}
