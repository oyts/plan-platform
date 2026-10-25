package com.oyts.planplatform.ai;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.ConflictException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/synthetic","app.ai.worker-enabled=false"}) @AutoConfigureMockMvc
class AiRuntimeIntegrationTest {
    @Autowired AiRuntimePolicy policy;@Autowired AiConversationService ai;@Autowired AiQuotaService quota;@Autowired AiProperties config;@Autowired AiAttempts attempts;
    @Autowired JdbcTemplate db;@Autowired UserAccountRepository users;@Autowired ObjectMapper json;@Autowired MockMvc mvc;
    @MockBean AiProvider provider;String admin,other,owner;
    String user(boolean isAdmin){var u=new UserAccount("runtime-"+UUID.randomUUID()+"@example.test","unused-test-hash","运行配置合成账号");if(isAdmin)u.makeAdmin();return users.saveAndFlush(u).getId();}
    @BeforeEach void setup(){clean();admin=user(true);other=user(true);owner=user(false);db.update("UPDATE ai_quota_policy SET daily_limit=100 WHERE id=1");reset(provider);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("DELETE FROM ai_runtime_audit");db.update("UPDATE ai_runtime_policy SET version=0,settings_json=NULL,updated_at=NULL WHERE id=1");}
    AiRuntimePolicy.Limits limits(int concurrency,int queue){return new AiRuntimePolicy.Limits(concurrency,queue,16000,2048,4096,60);}
    org.springframework.test.web.servlet.request.RequestPostProcessor as(String id,String role){return jwt().jwt(j->j.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_"+role));}
    AiConversationService.Job enqueue(String who){var s=ai.create(who,AiDrafts.Mode.TODO,true);return ai.enqueue(who,s.id(),s.version(),UUID.randomUUID().toString(),"仅合成讨论",AiConversationService.Intent.DIALOGUE);}
    static AiProvider.Result reply(){return new AiProvider.Result("{\"message\":\"合成讨论，无真实业务\",\"recommendedMode\":null,\"draft\":null}",100,128);}
    @Test void privateRoleGatesAndOverviewNeverExposeBusinessOrKey()throws Exception{
        mvc.perform(get("/api/admin/ai/runtime")).andExpect(status().isUnauthorized());
        for(String path:List.of("/api/admin/access","/api/admin/overview","/api/admin/ai/runtime","/api/admin/ai/runtime/audit")){
            mvc.perform(get(path).with(as(owner,"USER"))).andExpect(status().isForbidden());
            var body=mvc.perform(get(path).with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("apiKey"));assertFalse(body.contains("user_message"));assertFalse(body.contains("context_json"));
        }
    }
    @Test void downgradedRoleCannotUsePreviouslyIssuedAdminAuthority()throws Exception{
        db.update("UPDATE users SET role='USER' WHERE id=?",admin);
        mvc.perform(get("/api/admin/access").with(as(admin,"ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/ai/runtime").with(as(admin,"ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"limits",limits(1,0))))).andExpect(status().isForbidden());
        assertEquals(0,policy.current().version());
    }
    @Test void policyPersistsAuditRestoresAsNewVersionAndDoesNotResetDailyUsage(){
        var job=enqueue(owner);ai.fail(ai.claim(),new AiFailure(AiFailure.Code.TIMEOUT,true));int pending=quota.usage(owner).pending();assertEquals(1,pending);
        var before=policy.current();var after=policy.change(admin,limits(1,1),before.version());
        assertEquals(after,new AiRuntimePolicy(db,json,config).current());assertEquals(pending,quota.usage(owner).pending());
        var audit=policy.history(0,10);assertEquals(1,audit.total());assertEquals(before,audit.items().getFirst().before());assertEquals(after,audit.items().getFirst().after());
        assertThrows(ConflictException.class,()->policy.change(other,limits(2,0),0));assertEquals(1,policy.history(0,10).total());
        assertThrows(IllegalArgumentException.class,()->policy.restore(admin,audit.items().getFirst().id(),after.version(),false));
        var restored=policy.restore(admin,audit.items().getFirst().id(),after.version(),true);assertEquals(before.limits(),restored.limits());assertEquals(2,restored.version());assertEquals(2,policy.history(0,10).total());assertEquals(pending,quota.usage(owner).pending());assertEquals("UNKNOWN",db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,job.id()));
    }
    @Test void twoAdministratorsRacingCreateOneVersionAndOneAudit()throws Exception{
        var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){
            var jobs=new ArrayList<Future<Boolean>>();for(String actor:List.of(admin,other))jobs.add(pool.submit(()->{start.await();try{policy.change(actor,limits(1,0),0);return true;}catch(ConflictException conflict){return false;}}));start.countDown();int saved=0;for(var f:jobs)if(f.get(10,TimeUnit.SECONDS))saved++;assertEquals(1,saved);
        }assertEquals(1,policy.current().version());assertEquals(1,policy.history(0,10).total());
    }
    @Test void rejectsNullDecimalStringOutOfRangeAndIncompatibleBudgets()throws Exception{
        for(String changed:List.of("\"maxConcurrent\":1.5","\"maxConcurrent\":\"1\"","\"maxConcurrent\":3","\"queueCapacity\":null","\"maxInputChars\":32001","\"totalOutputTokens\":1024","\"timeoutSeconds\":121")){
            var node=json.valueToTree(limits(1,1));String key=changed.substring(1,changed.indexOf(':')-1);((com.fasterxml.jackson.databind.node.ObjectNode)node).set(key,json.readTree('{'+changed+'}').get(key));
            mvc.perform(put("/api/admin/ai/runtime").with(as(admin,"ADMIN")).contentType("application/json").content(json.writeValueAsString(Map.of("version",0,"limits",node)))).andExpect(status().isBadRequest());
        }assertEquals(0,policy.current().version());assertEquals(0,policy.history(0,10).total());
    }
    @Test void lowerQueueDrainsExistingAndDoesNotLoseOrRepriceAcceptedJobs(){
        policy.change(admin,limits(1,1),0);var first=enqueue(owner);var running=ai.claim();var second=enqueue(user(false));
        var low=new AiRuntimePolicy.Limits(1,0,1000,128,256,1);policy.change(other,low,1);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ai_jobs",Integer.class));assertEquals(2048,running.limits().maxOutputTokens());
        assertThrows(AiFailure.class,()->enqueue(user(false)));assertNull(ai.claim());
        db.update("UPDATE ai_jobs SET updated_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(5)),first.id());ai.maintain();assertEquals("RUNNING",db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,first.id()));
        ai.succeed(running,reply());var next=ai.claim();assertEquals(second.id(),next.id());assertEquals(2048,next.limits().maxOutputTokens());ai.succeed(next,reply());
        var fresh=enqueue(user(false));var claimed=ai.claim();assertEquals(fresh.id(),claimed.id());assertEquals(low,claimed.limits());
    }
    @Test void firstOverrideFreezesLegacyAcceptedJobsUsingPreviousDeploymentPolicy(){
        var first=enqueue(owner);db.update("UPDATE ai_jobs SET runtime_input_chars=NULL,runtime_output_tokens=NULL,runtime_total_output_tokens=NULL,runtime_timeout_seconds=NULL,runtime_version=NULL WHERE id=?",first.id());
        var old=policy.current().limits();policy.change(admin,new AiRuntimePolicy.Limits(1,0,1000,128,256,1),0);
        var accepted=ai.claim();assertEquals(first.id(),accepted.id());assertEquals(old.maxInputChars(),accepted.limits().maxInputChars());assertEquals(old.timeoutSeconds(),accepted.limits().timeoutSeconds());assertEquals(old.maxOutputTokens(),accepted.limits().maxOutputTokens());
        assertEquals(0L,db.queryForObject("SELECT runtime_version FROM ai_jobs WHERE id=?",Long.class,first.id()));
    }
    @Test void runnerAndProviderReceiveImmutableInputOutputTimeoutSnapshot(){
        policy.change(admin,limits(1,0),0);enqueue(owner);var work=ai.claim();policy.change(other,new AiRuntimePolicy.Limits(1,0,1000,128,256,1),1);
        when(provider.generate(any())).thenReturn(reply());try(var guard=new AiExecutionGuard(1,0)){var runner=new AiJobRunner(ai,provider,guard,config,attempts,false,true);try{runner.run(work);}finally{runner.close();}}
        var captor=org.mockito.ArgumentCaptor.forClass(AiProvider.Request.class);verify(provider).generate(captor.capture());assertEquals(2048,captor.getValue().maxOutputTokens());assertEquals(16000,captor.getValue().contextLimit());assertEquals(60,captor.getValue().timeoutSeconds());assertEquals(1,quota.usage(owner).used());
    }
    @Test void realExecutorHotDecreaseDoesNotInterruptAndIncreaseActuallyRunsSecondCall()throws Exception{
        policy.change(admin,limits(2,4),0);for(int i=0;i<4;i++)enqueue(user(false));
        var permits=new Semaphore(0);var calls=new AtomicInteger();var active=new AtomicInteger();var max=new AtomicInteger();
        when(provider.generate(any())).thenAnswer(inv->{calls.incrementAndGet();int count=active.incrementAndGet();max.accumulateAndGet(count,Math::max);try{assertTrue(permits.tryAcquire(8,TimeUnit.SECONDS));return reply();}finally{active.decrementAndGet();}});
        try(var guard=new AiExecutionGuard(2,0)){var runner=new AiJobRunner(ai,provider,guard,config,attempts,true,false);try{
            runner.dispatch();await(()->calls.get()==2);assertEquals(2,active.get());policy.change(admin,limits(1,0),1);runner.dispatch();assertEquals(2,active.get());assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='QUEUED'",Integer.class));
            permits.release(2);await(()->db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='RUNNING'",Integer.class)==0);Thread.sleep(40);runner.dispatch();await(()->calls.get()==3);assertEquals(1,active.get());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='QUEUED'",Integer.class));
            policy.change(other,limits(2,0),2);runner.dispatch();await(()->calls.get()==4);assertEquals(2,active.get());permits.release(2);await(()->db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='SUCCEEDED'",Integer.class)==4);assertEquals(2,max.get());assertEquals(0,guard.snapshot().queued());
        }finally{permits.release(10);runner.close();}}
    }
    @Test void increaseBetweenDispatchReadAndClaimDoesNotRejectAcceptedSecondCall()throws Exception{
        var svc=mock(AiConversationService.class);var ledger=mock(AiAttempts.class);var low=new AiRuntimePolicy.Policy(limits(1,0),0,null);var high=new AiRuntimePolicy.Policy(limits(2,0),1,null);
        when(svc.runtime()).thenReturn(low,high);when(svc.runnable(any())).thenReturn(true);when(ledger.begin(anyString(),anyInt())).thenReturn(true);
        var first=new AiConversationService.Work("race1",owner,"s1","r1","{}","TODO",AiConversationService.Intent.DIALOGUE,high.limits());var second=new AiConversationService.Work("race2",owner,"s2","r2","{}","TODO",AiConversationService.Intent.DIALOGUE,high.limits());when(svc.claim()).thenReturn(first,second,null);
        var permits=new Semaphore(0);var active=new AtomicInteger();when(provider.generate(any())).thenAnswer(inv->{active.incrementAndGet();try{assertTrue(permits.tryAcquire(8,TimeUnit.SECONDS));return reply();}finally{active.decrementAndGet();}});
        try(var guard=new AiExecutionGuard(1,0)){var runner=new AiJobRunner(svc,provider,guard,config,ledger,true,false);try{runner.dispatch();await(()->active.get()==2);assertEquals(2,guard.snapshot().active());verify(svc,never()).fail(any(),any());}finally{permits.release(2);runner.close();}}
    }
    void await(java.util.function.BooleanSupplier condition)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(10);assertTrue(condition.getAsBoolean());}
}
