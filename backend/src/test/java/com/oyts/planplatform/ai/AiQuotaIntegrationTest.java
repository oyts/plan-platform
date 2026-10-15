package com.oyts.planplatform.ai;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.TooManyRequestsException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AiQuotaIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired AiQuotaService quota;
    @Autowired UserAccountRepository users;
    @Autowired PlatformTransactionManager transactions;
    String admin, owner, other;
    static final String FINGERPRINT = "a".repeat(64);
    String user(boolean isAdmin) {
        var user = new UserAccount("ai-"+UUID.randomUUID()+"@example.test", "unused-test-hash", "额度测试");
        if (isAdmin) user.makeAdmin();
        return users.saveAndFlush(user).getId();
    }
    RequestPostProcessor as(String id, String role) { return jwt().jwt(j -> j.subject(id).claim("role",role)).authorities(new SimpleGrantedAuthority("ROLE_"+role)); }
    @BeforeEach void setup() {
        db.update("DELETE FROM ai_quota_audit"); db.update("DELETE FROM ai_quota_requests"); db.update("DELETE FROM ai_user_quota");
        db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");
        admin=user(true); owner=user(false); other=user(false);
    }
    @Test void protectsAdministrationAndKeepsAiDisabledWithoutTouchingLedger() throws Exception {
        mvc.perform(get("/api/admin/ai/settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ai/users").with(as(owner,"USER"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/ai/settings").with(as(owner,"USER")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":99,\"version\":0}" )).andExpect(status().isForbidden());
        mvc.perform(get("/api/ai/capabilities").with(as(owner,"USER"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store"))).andExpect(jsonPath("$.available").value(false)).andExpect(jsonPath("$.status").value("DISABLED")).andExpect(jsonPath("$.apiKeyFile").doesNotExist());
        mvc.perform(post("/api/ai/jobs").with(as(owner,"USER")).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isServiceUnavailable());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ai_quota_requests",Integer.class));
    }
    @Test void adminChangesAreHotVersionedAuditedAndDoNotResetUsage() throws Exception {
        mvc.perform(put("/api/admin/ai/settings").with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":3,\"version\":0}" )).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        var request=quota.reserve(owner,"first-dialogue",FINGERPRINT); quota.settle(owner,request.id(),"SUCCEEDED");
        mvc.perform(put("/api/admin/ai/users/{id}",owner).with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":1,\"paused\":false,\"version\":0}" )).andExpect(status().isOk()).andExpect(jsonPath("$.used").value(1)).andExpect(jsonPath("$.remaining").value(0));
        mvc.perform(put("/api/admin/ai/users/{id}",owner).with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":99,\"paused\":false,\"version\":0}" )).andExpect(status().isConflict());
        quota.changeUser(admin,owner,null,true,1); assertThrows(TooManyRequestsException.class,()->quota.reserve(owner,"paused-request",FINGERPRINT));
        quota.changeUser(admin,owner,null,false,2);
        assertEquals(2,quota.usage(owner).remaining()); assertEquals(1,quota.usage(owner).used());
        assertEquals(3,quota.usage(other).remaining());
        mvc.perform(get("/api/ai/quota").param("owner",other).with(as(owner,"USER"))).andExpect(status().isOk()).andExpect(jsonPath("$.used").value(1));
        mvc.perform(get("/api/admin/ai/audit").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
        mvc.perform(put("/api/admin/ai/settings").with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":-1,\"version\":1}" )).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/ai/settings").with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}" )).andExpect(status().isBadRequest());
        for (String input : new String[]{"{\"dailyLimit\":1.5,\"version\":1}","{\"dailyLimit\":\"3\",\"version\":1}","{\"dailyLimit\":3,\"version\":1.5}"})
            mvc.perform(put("/api/admin/ai/settings").with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/ai/settings").with(as(admin,"ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"dailyLimit\":4,\"version\":0}" )).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/ai/users").param("query","%_").with(as(admin,"ADMIN"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }
    @Test void idempotencyAndUncertainUpstreamCannotDoubleSpendOrAutoRefund() {
        quota.changePolicy(admin,2,0);
        var a=quota.reserve(owner,"unique-request",FINGERPRINT); assertTrue(a.acquired());
        var repeated=quota.reserve(owner,"unique-request",FINGERPRINT); assertFalse(repeated.acquired()); assertEquals(a.id(),repeated.id());
        assertThrows(ConflictException.class,()->quota.reserve(owner,"unique-request","b".repeat(64)));
        assertEquals(1,quota.usage(owner).pending());
        quota.settle(owner,a.id(),"UNKNOWN"); assertEquals(1,quota.usage(owner).pending());
        quota.settle(owner,a.id(),"SUCCEEDED"); quota.settle(owner,a.id(),"SUCCEEDED");
        assertEquals(1,quota.usage(owner).used()); assertEquals(0,quota.usage(owner).pending());
        assertThrows(ConflictException.class,()->quota.settle(owner,a.id(),"RELEASED"));
        var b=quota.reserve(owner,"known-failure",FINGERPRINT); quota.settle(owner,b.id(),"RELEASED");
        assertEquals(1,quota.usage(owner).remaining()); assertFalse(quota.reserve(owner,"known-failure",FINGERPRINT).acquired());
        assertThrows(com.oyts.planplatform.common.NotFoundException.class,()->quota.settle(other,a.id(),"SUCCEEDED"));
    }
    @Test void concurrentRequestsNeverExceedDailyLimit() throws Exception {
        quota.changePolicy(admin,1,0);
        try (var executor=Executors.newFixedThreadPool(6)) {
            List<Callable<Boolean>> calls=new ArrayList<>();
            for(int i=0;i<6;i++) { final int index=i; calls.add(()-> { try { quota.reserve(owner,"parallel-"+index,FINGERPRINT); return true; } catch(TooManyRequestsException expected) { return false; } }); }
            int admitted=0; for(var result:executor.invokeAll(calls)) if(result.get()) admitted++;
            assertEquals(1,admitted); assertEquals(1,quota.usage(owner).pending());
        }
    }
    @Test void resetsAtShanghaiMidnightAndSettlesAgainstOriginalDay() {
        var tx=new TransactionTemplate(transactions);
        var before=new AiQuotaService(db,Clock.fixed(Instant.parse("2026-10-15T15:59:59Z"),ZoneOffset.UTC));
        var after=new AiQuotaService(db,Clock.fixed(Instant.parse("2026-10-15T16:00:01Z"),ZoneOffset.UTC));
        tx.execute(s->{before.changePolicy(admin,1,0);return null;});
        var request=tx.execute(s->before.reserve(owner,"before-midnight",FINGERPRINT));
        assertEquals("2026-10-15",request.quotaDay());
        assertEquals(1,tx.execute(s->after.usage(owner)).remaining());
        tx.execute(s->{after.settle(owner,request.id(),"SUCCEEDED");return null;});
        assertEquals(0,tx.execute(s->after.usage(owner)).used());
        assertEquals(1,tx.execute(s->before.usage(owner)).used());
    }
}
