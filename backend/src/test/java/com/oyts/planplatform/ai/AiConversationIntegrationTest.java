package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/fake-test-key","app.ai.worker-enabled=false"}) @AutoConfigureMockMvc
class AiConversationIntegrationTest {
    @Autowired AiConversationService sessions; @Autowired AiQuotaService quotas; @Autowired UserAccountRepository users;
    @Autowired ObjectMapper mapper; @Autowired JdbcTemplate db; @Autowired MockMvc mvc; @Autowired AiProperties config;
    @MockBean AiProvider provider;
    @Autowired AiAttempts attempts;
    String owner,other,admin;
    String user(boolean isAdmin) {var user=new UserAccount("dialogue-"+UUID.randomUUID()+"@example.test","unused-test-hash","对话验收");if(isAdmin)user.makeAdmin();return users.saveAndFlush(user).getId();}
    RequestPostProcessor as(String id){return jwt().jwt(j->j.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_USER"));}
    @BeforeEach void setup(){db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");owner=user(false);other=user(false);admin=user(true);quotas.changePolicy(admin,10,0);reset(provider);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    String draft(AiDrafts.Mode mode) throws Exception {
        var first=new AiDrafts.Item("one","第一步","练习说明",mode==AiDrafts.Mode.ROUTE?null:LocalDate.of(2026,10,15),30,mode==AiDrafts.Mode.ROUTE?"base":null,List.of());
        var second=new AiDrafts.Item("two","第二步","",mode==AiDrafts.Mode.ROUTE?null:LocalDate.of(2026,10,16),20,null,mode==AiDrafts.Mode.ROUTE?List.of("one"):List.of());
        return mapper.writeValueAsString(new AiDrafts.Draft(1,mode,0,"验收规划",mode==AiDrafts.Mode.PLAN?LocalDate.of(2026,10,15):null,mode==AiDrafts.Mode.PLAN?LocalDate.of(2026,10,31):null,mode==AiDrafts.Mode.PLAN?List.of(7):List.of(),mode==AiDrafts.Mode.ROUTE?List.of(new AiDrafts.Stage("base","基础")):List.of(),List.of(first,second)));
    }
    void reply(String mode,String draft) throws Exception {when(provider.generate(any())).thenReturn(new AiProvider.Result("{\"message\":\"请核对这份草稿。\",\"recommendedMode\":\""+mode+"\",\"draft\":"+draft+"}",100,200));}
    void runNext() {var work=sessions.claim();assertNotNull(work);try(var guard=new AiExecutionGuard(2,0)){var runner=new AiJobRunner(sessions,provider,guard,config,attempts,false,false);try{runner.run(work);}finally{runner.close();}}}
    @Test void discussesModeFirstAndDeduplicatesPaidAdmission() throws Exception {
        var session=sessions.create(owner,null,true);reply("ROUTE","null");
        var job=sessions.enqueue(owner,session.id(),session.version(),"conversation-first","想学吉他，时间不固定");
        assertEquals(job.id(),sessions.enqueue(owner,session.id(),session.version(),"conversation-first","想学吉他，时间不固定").id());
        assertThrows(ConflictException.class,()->sessions.enqueue(owner,session.id(),session.version(),"conversation-first","不同内容"));
        runNext();var result=sessions.read(owner,session.id());assertNull(result.mode());assertNull(result.draft());assertEquals("ROUTE",result.recommendedMode());assertEquals(1,quotas.usage(owner).used());verify(provider,times(1)).generate(any());
        assertEquals("ROUTE",sessions.choose(owner,session.id(),result.version(),AiDrafts.Mode.ROUTE).mode());
    }
    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void createsAllThreeModesOnlyAfterExplicitConfirmationAndOnlyOnce(AiDrafts.Mode mode) throws Exception {
        var initial=sessions.create(owner,mode,true);reply(mode.name(),draft(mode));
        sessions.enqueue(owner,initial.id(),initial.version(),"generate-"+mode,"请生成具体步骤");runNext();
        var ready=sessions.read(owner,initial.id());assertNotNull(ready.draft());
        String table=mode==AiDrafts.Mode.PLAN?"plans":mode==AiDrafts.Mode.ROUTE?"mainlines":"todos";
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE owner_id=?",Integer.class,owner));
        assertThrows(IllegalArgumentException.class,()->sessions.apply(owner,ready.id(),ready.version(),false));
        assertThrows(NotFoundException.class,()->sessions.apply(other,ready.id(),ready.version(),true));
        var applied=sessions.apply(owner,ready.id(),ready.version(),true);assertEquals(2,applied.path("count").asInt());
        assertEquals(applied,sessions.apply(owner,ready.id(),ready.version(),true));
        assertEquals(mode==AiDrafts.Mode.TODO?2:1,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE owner_id=?",Integer.class,owner));
        if(mode==AiDrafts.Mode.PLAN) assertEquals(64,db.queryForObject("SELECT rest_days_mask FROM plans WHERE owner_id=?",Integer.class,owner));
        if(mode==AiDrafts.Mode.ROUTE) mvc.perform(get("/api/mainlines").with(as(owner))).andExpect(status().isOk()).andExpect(jsonPath("$[0].items[0].unlocked").value(true)).andExpect(jsonPath("$[0].items[1].unlocked").value(false)).andExpect(jsonPath("$[0].items[1].dependencyMode").value("EXPLICIT"));
    }
    @Test void manualDraftEditsUseVersionsAndNeverTouchExistingBusiness() throws Exception {
        var initial=sessions.create(owner,AiDrafts.Mode.TODO,true);
        var saved=sessions.saveDraft(owner,initial.id(),initial.version(),draft(AiDrafts.Mode.TODO));
        assertThrows(ConflictException.class,()->sessions.saveDraft(owner,saved.id(),initial.version(),draftUnchecked(AiDrafts.Mode.TODO)));
        assertThrows(NotFoundException.class,()->sessions.read(other,saved.id()));
        assertEquals(0,quotas.usage(owner).used());
        mvc.perform(get("/api/ai/sessions/{id}",saved.id()).with(as(other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/sessions/{id}",saved.id()).with(as(owner))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(post("/api/ai/sessions/{id}/apply",saved.id()).with(as(owner)).contentType("application/json").content("{\"version\":"+saved.version()+",\"confirmed\":false}")).andExpect(status().isBadRequest());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM todos WHERE owner_id=?",Integer.class,owner));
        sessions.delete(owner,saved.id(),saved.version());assertTrue(sessions.list(owner).isEmpty());
    }
    String draftUnchecked(AiDrafts.Mode mode){try{return draft(mode);}catch(Exception impossible){throw new RuntimeException(impossible);}}
    @Test void uncertainFailurePreservesDraftAndQuotaAndDoesNotAutomaticallyRetry() throws Exception {
        var initial=sessions.create(owner,AiDrafts.Mode.PLAN,true);var saved=sessions.saveDraft(owner,initial.id(),initial.version(),draft(AiDrafts.Mode.PLAN));
        when(provider.generate(any())).thenThrow(new AiFailure(AiFailure.Code.TIMEOUT,true));
        sessions.enqueue(owner,saved.id(),saved.version(),"uncertain-update","把节奏放慢");
        assertThrows(ConflictException.class,()->sessions.saveDraft(owner,saved.id(),saved.version()+1,draftUnchecked(AiDrafts.Mode.PLAN)));
        runNext();var result=sessions.read(owner,saved.id());assertEquals("验收规划",result.draft().title());assertEquals("UNKNOWN",result.turns().getFirst().state());assertEquals(1,quotas.usage(owner).pending());assertNull(sessions.claim());verify(provider,times(1)).generate(any());
    }
    @Test void invalidModelModeCannotWriteOrReplaceExistingDraftAndKnownFailuresRefund() throws Exception {
        var initial=sessions.create(owner,null,true);reply("TODO",draft(AiDrafts.Mode.TODO));sessions.enqueue(owner,initial.id(),0,"unconfirmed-mode","未选择模式");runNext();
        assertNull(sessions.read(owner,initial.id()).draft());assertEquals("FAILED",sessions.read(owner,initial.id()).turns().getFirst().state());assertEquals(0,quotas.usage(owner).pending());assertEquals(0,quotas.usage(owner).used());
    }
    @Test void recoversStaleRunningJobsAndDeletesExpiredConversationNotBusiness() throws Exception {
        var session=sessions.create(owner,AiDrafts.Mode.TODO,true);sessions.enqueue(owner,session.id(),0,"interrupted-job","任务");assertNotNull(sessions.claim());
        db.update("UPDATE ai_jobs SET updated_at=? WHERE session_id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(300)),session.id());sessions.maintain();assertEquals("UNKNOWN",sessions.read(owner,session.id()).turns().getFirst().state());
        db.update("UPDATE ai_sessions SET expires_at=? WHERE id=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),session.id());sessions.maintain();assertTrue(sessions.list(owner).isEmpty());assertEquals(1,quotas.usage(owner).pending());
        db.update("UPDATE ai_quota_requests SET quota_day=? WHERE owner_id=?",LocalDate.now(AiQuotaService.ZONE).minusDays(100),owner);
        sessions.maintain();assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_quota_requests WHERE owner_id=?",Integer.class,owner));
    }
    @Test void enforcesConsentLimitsAndSkipsGenericIdempotencyCache() throws Exception {
        assertThrows(IllegalArgumentException.class,()->sessions.create(owner,AiDrafts.Mode.TODO,false));
        var initial=sessions.create(owner,AiDrafts.Mode.TODO,true);
        mvc.perform(post("/api/ai/sessions/{id}/jobs",initial.id()).with(as(owner)).header("X-Idempotency-Key","own-ai-ledger-key").contentType("application/json").content("{\"message\":\"练习\",\"operationKey\":\"http-ai-ledger\",\"version\":0}")).andExpect(status().isAccepted());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM idempotency_records WHERE owner_id=?",Integer.class,owner));
        mvc.perform(post("/api/ai/sessions").with(as(owner)).contentType("application/json").content(" ".repeat(256001))).andExpect(status().isPayloadTooLarge());
        assertThrows(IllegalArgumentException.class,()->sessions.enqueue(owner,initial.id(),1,"huge-message","x".repeat(8001)));
    }
    @Test void applyRollsBackWholeBatchIfALaterBusinessInsertFails() throws Exception {
        var initial=sessions.create(owner,AiDrafts.Mode.TODO,true);var saved=sessions.saveDraft(owner,initial.id(),0,draft(AiDrafts.Mode.TODO));
        // An isolated in-memory test database constraint forces failure on the second item.
        db.execute("ALTER TABLE todos ADD CONSTRAINT ai_test_batch_failure CHECK (owner_id <> '"+owner+"' OR title <> '第二步')");
        try {
            assertThrows(org.hibernate.exception.ConstraintViolationException.class,()->sessions.apply(owner,saved.id(),saved.version(),true));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM todos WHERE owner_id=?",Integer.class,owner));
            assertNull(sessions.read(owner,saved.id()).applied());assertNotNull(sessions.read(owner,saved.id()).draft());
        } finally {db.execute("ALTER TABLE todos DROP CONSTRAINT ai_test_batch_failure");}
    }
    @Test void boundsDurableQueueAndGlobalRunningClaims() {
        for(int i=0;i<6;i++){String member=user(false);var session=sessions.create(member,AiDrafts.Mode.TODO,true);sessions.enqueue(member,session.id(),0,"capacity-"+i,"任务");}
        var overflow=sessions.create(owner,AiDrafts.Mode.TODO,true);
        var failure=assertThrows(AiFailure.class,()->sessions.enqueue(owner,overflow.id(),0,"capacity-overflow","任务"));assertEquals(AiFailure.Code.BUSY,failure.code());
        assertEquals(6,db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='QUEUED'",Integer.class));assertNotNull(sessions.claim());assertNotNull(sessions.claim());assertNull(sessions.claim());
    }
}
