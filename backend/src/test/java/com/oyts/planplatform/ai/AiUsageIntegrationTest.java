package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/synthetic-only-not-read","app.ai.worker-enabled=false"})
@AutoConfigureMockMvc
class AiUsageIntegrationTest {
    @Autowired MockMvc mvc;@Autowired AiQuotaService quotas;@Autowired AiConversationService conversations;@Autowired UserAccountRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper mapper;
    String admin,owner;
    @BeforeEach void setup(){db.update("DELETE FROM ai_jobs");db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("UPDATE ai_quota_policy SET daily_limit=20,version=0 WHERE id=1");admin=user(true);owner=user(false);}
    String user(boolean admin){var user=new UserAccount(UUID.randomUUID()+"@example.test","unused-test-hash","用量合成账号");if(admin)user.makeAdmin();return users.saveAndFlush(user).getId();}
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String id,String role){return jwt().jwt(j->j.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
    String session(){return conversations.create(owner,null,true).id();}
    String succeeded(){String session=session();var job=conversations.enqueue(owner,session,0,UUID.randomUUID().toString(),"synthetic private prompt MUST_NOT_LEAK");var work=conversations.claim();assertEquals(job.id(),work.id());conversations.succeed(work,new AiProvider.Result("{\"message\":\"private reply MUST_NOT_LEAK\",\"recommendedMode\":\"ROUTE\",\"draft\":null}",120,45));return session;}
    @Test void summaryIsAdminOnlyPrivateAndContainsNoConversationContent()throws Exception{
        succeeded();mvc.perform(get("/api/admin/ai/usage")).andExpect(status().isUnauthorized());mvc.perform(get("/api/admin/ai/usage").with(as(owner,"USER"))).andExpect(status().isForbidden());
        var result=mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.totals.requests").value(1)).andExpect(jsonPath("$.totals.succeeded").value(1)).andExpect(jsonPath("$.totals.inputTokens").value(120)).andExpect(jsonPath("$.totals.outputTokens").value(45)).andReturn().getResponse().getContentAsString();
        assertFalse(result.contains("MUST_NOT_LEAK"));assertFalse(result.contains("api-key-file"));assertFalse(result.contains("sessionId"));
    }
    @Test void usageSurvivesDeletingConversationAndRepeatedCompletionIsNotCountedTwice()throws Exception{
        String id=succeeded();conversations.delete(owner,id,conversations.read(owner,id).version());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_jobs",Integer.class));
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.succeeded").value(1)).andExpect(jsonPath("$.totals.tokenKnownRequests").value(1)).andExpect(jsonPath("$.recent.items[0].inputTokens").value(120));
        assertEquals(1,quotas.usage(owner).used());
    }
    @Test void failureUnknownAndQueuedAreDistinctWithoutRefundOrPaidRetry()throws Exception{
        String first=session();conversations.enqueue(owner,first,0,UUID.randomUUID().toString(),"failure");var failed=conversations.claim();conversations.fail(failed,new AiFailure(AiFailure.Code.BUSY,false));conversations.fail(failed,new AiFailure(AiFailure.Code.BUSY,false));
        String second=session();conversations.enqueue(owner,second,0,UUID.randomUUID().toString(),"unknown");conversations.fail(conversations.claim(),new AiFailure(AiFailure.Code.TIMEOUT,true));
        conversations.enqueue(owner,session(),0,UUID.randomUUID().toString(),"queued");
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.requests").value(3)).andExpect(jsonPath("$.totals.failed").value(1)).andExpect(jsonPath("$.totals.unknown").value(1)).andExpect(jsonPath("$.totals.queued").value(1)).andExpect(jsonPath("$.runtime.queued").value(1)).andExpect(jsonPath("$.totals.tokenKnownRequests").value(0));
        assertEquals(2,quotas.usage(owner).pending());assertEquals(0,quotas.usage(owner).used());
    }
    @Test void datesPaginationAndLegacyUnknownTokensAreValidated()throws Exception{
        var day=LocalDate.now(AiQuotaService.ZONE);var old=quotas.reserve(owner,"legacy-usage", "a".repeat(64));quotas.settle(owner,old.id(),"SUCCEEDED");
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.totals.succeeded").value(1)).andExpect(jsonPath("$.totals.tokenKnownRequests").value(0)).andExpect(jsonPath("$.recent.items[0].inputTokens").isEmpty());
        for(String[] dates:List.of(new String[]{day.minusDays(31).toString(),day.toString()},new String[]{day.toString(),day.plusDays(1).toString()},new String[]{day.minusDays(90).toString(),day.minusDays(89).toString()}))mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN")).param("from",dates[0]).param("to",dates[1])).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN")).param("size","51")).andExpect(status().isBadRequest());
    }
    @Test void attemptReasonsAreSanitizedMetadataAndCorrelateWithOwnerNumber()throws Exception{
        var session=conversations.create(owner,null,true);conversations.enqueue(owner,session.id(),0,UUID.randomUUID().toString(),"MUST_NOT_LEAK");var work=conversations.claim();
        db.update("INSERT INTO ai_job_attempts(job_id,attempt_no,state,reason,input_chars,output_chars,input_tokens,output_tokens,started_at,completed_at) VALUES (?,0,'REJECTED','DRAFT_INVALID',100,200,50,60,?,?)",work.id(),java.sql.Timestamp.from(Instant.now().minusSeconds(1)),java.sql.Timestamp.from(Instant.now()));
        var diagnostic=conversations.read(owner,session.id()).turns().getFirst().diagnostic();assertEquals(work.reservation(),diagnostic.requestId());assertEquals(100,diagnostic.details().getFirst().inputChars());assertTrue(diagnostic.details().getFirst().durationMs()>=0);
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.reasons[0].reason").value("DRAFT_INVALID")).andExpect(jsonPath("$.reasons[0].attempts").value(1)).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("MUST_NOT_LEAK"))));
    }
    @Test void repairAttemptsKnownTokensAndWaitAreSeparateFromParentTotals()throws Exception{
        conversations.enqueue(owner,session(),0,UUID.randomUUID().toString(),"synthetic");var work=conversations.claim();var start=Instant.now();db.update("update ai_jobs set created_at=? where id=?",java.sql.Timestamp.from(start.minusSeconds(10)),work.id());
        db.update("insert into ai_job_attempts(job_id,attempt_no,state,input_tokens,output_tokens,started_at) values (?,0,'REJECTED',50,60,?)",work.id(),java.sql.Timestamp.from(start));
        db.update("insert into ai_job_attempts(job_id,attempt_no,state,started_at) values (?,1,'UNKNOWN',?)",work.id(),java.sql.Timestamp.from(start.plusSeconds(1)));
        mvc.perform(get("/api/admin/ai/usage").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.attempts.attempts").value(2)).andExpect(jsonPath("$.attempts.repairs").value(1)).andExpect(jsonPath("$.attempts.tokenKnownAttempts").value(1)).andExpect(jsonPath("$.attempts.inputTokens").value(50)).andExpect(jsonPath("$.attempts.outputTokens").value(60)).andExpect(jsonPath("$.attempts.averageQueueWaitMs").value(10000)).andExpect(jsonPath("$.attempts.waitKnownJobs").value(1)).andExpect(jsonPath("$.totals.inputTokens").value(0));
    }
}
