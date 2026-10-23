package com.oyts.planplatform.ai;

import com.oyts.planplatform.auth.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/synthetic","app.ai.worker-enabled=false"})
class AiRepairIntegrationTest {
    @Autowired AiConversationService sessions;@Autowired AiQuotaService quotas;@Autowired AiAttempts attempts;
    @Autowired AiProperties config;@Autowired UserAccountRepository users;@Autowired JdbcTemplate db;
    @Autowired AiUsageService usage;
    @MockBean AiProvider provider;String owner;
    private static final String VALID="{\"message\":\"先讨论具体日期，再预览草稿。\",\"recommendedMode\":\"TODO\",\"draft\":null}";
    @BeforeEach void setup(){
        db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");
        db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");
        owner=users.saveAndFlush(new UserAccount("repair-"+UUID.randomUUID()+"@example.test","unused-synthetic-hash","合成修复")).getId();
        quotas.changePolicy(owner,20,0);reset(provider);
    }
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    AiConversationService.Work queued(){var s=sessions.create(owner,AiDrafts.Mode.TODO,true);sessions.enqueue(owner,s.id(),s.version(),UUID.randomUUID().toString(),"今天想做一些事情，先交流");return sessions.claim();}
    void run(AiConversationService.Work w){try(var guard=new AiExecutionGuard(1,0)){var runner=new AiJobRunner(sessions,provider,guard,config,attempts,false,true);try{runner.run(w);}finally{runner.close();}}}
    String state(AiConversationService.Work w){return db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,w.id());}
    long count(){return db.queryForObject("SELECT COUNT(*) FROM ai_job_attempts",Long.class);}
    @Test void repairsOnceWithoutRefundingReservationAndSumsBothKnownUsages(){
        when(provider.generate(any())).thenReturn(new AiProvider.Result("{}",100,200)).thenAnswer(call->{
            assertEquals(1,quotas.usage(owner).pending());assertEquals(0,quotas.usage(owner).used());
            assertTrue(((AiProvider.Request)call.getArgument(0)).message().contains("OUTPUT_REPAIR"));
            return new AiProvider.Result(VALID,300,400);
        });
        var w=queued();run(w);assertEquals("SUCCEEDED",state(w));assertEquals(2,count());assertEquals(1,quotas.usage(owner).used());
        assertEquals(400,db.queryForObject("SELECT input_tokens FROM ai_quota_requests WHERE id=?",Long.class,w.reservation()));
        assertEquals(600,db.queryForObject("SELECT output_tokens FROM ai_quota_requests WHERE id=?",Long.class,w.reservation()));
        assertEquals(true,db.queryForObject("SELECT token_usage_complete FROM ai_quota_requests WHERE id=?",Boolean.class,w.reservation()));
        run(w);verify(provider,times(2)).generate(any());
    }
    @Test void secondInvalidOutputStopsAndReleasesOnlyOnce(){
        when(provider.generate(any())).thenReturn(new AiProvider.Result("{}",10,20));var w=queued();run(w);
        assertEquals("FAILED",state(w));assertEquals(2,count());assertEquals(0,quotas.usage(owner).pending());assertEquals(0,quotas.usage(owner).used());verify(provider,times(2)).generate(any());
    }
    @Test void timeoutNeverAutomaticallyRetries(){
        when(provider.generate(any())).thenThrow(new AiFailure(AiFailure.Code.TIMEOUT,true));var w=queued();run(w);
        assertEquals("UNKNOWN",state(w));assertEquals(1,count());assertEquals(1,quotas.usage(owner).pending());verify(provider,times(1)).generate(any());
    }
    @Test void repairTimeoutKeepsFirstUsageButDoesNotCallItComplete(){
        when(provider.generate(any())).thenReturn(new AiProvider.Result("{}",10,20)).thenThrow(new AiFailure(AiFailure.Code.TIMEOUT,true));
        var w=queued();run(w);assertEquals("UNKNOWN",state(w));assertEquals(1,quotas.usage(owner).pending());
        assertEquals(10,db.queryForObject("SELECT input_tokens FROM ai_quota_requests WHERE id=?",Long.class,w.reservation()));
        assertEquals(false,db.queryForObject("SELECT token_usage_complete FROM ai_quota_requests WHERE id=?",Boolean.class,w.reservation()));
        var totals=usage.report(null,null,0,20).totals();assertEquals(10,totals.inputTokens());assertEquals(0,totals.tokenKnownRequests());
        run(w);verify(provider,times(2)).generate(any());
    }
    @Test void missingUsageOrInsufficientBudgetDoesNotRepair(){
        when(provider.generate(any())).thenThrow(new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.USAGE_MISSING,null));var first=queued();run(first);verify(provider,times(1)).generate(any());
        reset(provider);when(provider.generate(any())).thenReturn(new AiProvider.Result("{}",64001,8192));var second=queued();run(second);verify(provider,times(1)).generate(any());assertEquals("FAILED",state(second));
    }
    @Test void singleFenceIsLocallyRemovedWithoutPaidRepair(){
        when(provider.generate(any())).thenReturn(new AiProvider.Result("```json\n"+VALID+"\n```",10,20));var w=queued();run(w);assertEquals("SUCCEEDED",state(w));assertEquals(1,count());verify(provider,times(1)).generate(any());
        assertEquals("before\n```json\n{}\n```",AiOutputText.unwrap("before\n```json\n{}\n```"));
    }
    @Test void durableFirstAttemptClaimCannotBeReusedByAnotherRunner(){
        var w=queued();assertTrue(attempts.begin(w.id(),0));run(w);verifyNoInteractions(provider);assertEquals(1,count());
        assertThrows(IllegalArgumentException.class,()->attempts.begin(w.id(),2));
    }
    @Test void lateKnownUsageDoesNotReviveUnknownOutcome(){
        var w=queued();assertTrue(attempts.begin(w.id(),0));sessions.fail(w,new AiFailure(AiFailure.Code.TIMEOUT,true));
        attempts.finish(w.id(),0,"REJECTED",new AiFailure(AiFailure.Code.INVALID_OUTPUT),new AiProvider.Result("{}",100,200));
        assertEquals("UNKNOWN",state(w));assertEquals(1,quotas.usage(owner).pending());assertFalse(attempts.begin(w.id(),1));
        assertEquals(100,db.queryForObject("SELECT input_tokens FROM ai_quota_requests WHERE id=?",Long.class,w.reservation()));
    }
}
