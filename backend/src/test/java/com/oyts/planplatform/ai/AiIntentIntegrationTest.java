package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/synthetic","app.ai.worker-enabled=false"})
class AiIntentIntegrationTest {
    @Autowired AiConversationService ai;@Autowired AiQuotaService quotas;@Autowired AiAttempts attempts;@Autowired AiProperties config;
    @Autowired UserAccountRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    @MockBean AiProvider provider;String owner,other;
    String user(){return users.saveAndFlush(new UserAccount("intent-"+UUID.randomUUID()+"@example.test","unused-hash","意图合成验收")).getId();}
    @BeforeEach void setup(){db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");owner=user();other=user();quotas.changePolicy(owner,100,0);reset(provider);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    String reply(String draft){return "{\"message\":\"请核对建议，尚未创建真实计划。\",\"recommendedMode\":null,\"draft\":"+draft+"}";}
    AiConversationService.Session send(AiConversationService.Session s,String text,AiConversationService.Intent intent,String output){
        when(provider.generate(any())).thenReturn(new AiProvider.Result(output,100,200));
        ai.enqueue(owner,s.id(),s.version(),UUID.randomUUID().toString(),text,intent);
        var w=ai.claim();assertNotNull(w);
        try(var guard=new AiExecutionGuard(1,0)){var runner=new AiJobRunner(ai,provider,guard,config,attempts,false,true);try{runner.run(w);}finally{runner.close();}}
        return ai.read(owner,s.id());
    }
    String draft(AiDrafts.Mode mode)throws Exception{
        LocalDate from=LocalDate.of(2026,10,20),to=from.plusMonths(2);
        return json.writeValueAsString(new AiDrafts.Draft(1,mode,0,"合成规划",mode==AiDrafts.Mode.PLAN?from:null,mode==AiDrafts.Mode.PLAN?to:null,
            mode==AiDrafts.Mode.PLAN?List.of(7):List.of(),mode==AiDrafts.Mode.ROUTE?List.of(new AiDrafts.Stage("s1","基础")):List.of(),
            List.of(new AiDrafts.Item("one","第一步","仅用于合成测试",mode==AiDrafts.Mode.ROUTE?null:from,mode==AiDrafts.Mode.PLAN?120:30,mode==AiDrafts.Mode.ROUTE?"s1":null,List.of()))));
    }
    @Test void dialogueCannotOverwriteDraftEvenIfModelSendsOne()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.TODO,true);s=ai.saveDraft(owner,s.id(),s.version(),draft(AiDrafts.Mode.TODO));var original=s.draft();
        s=send(s,"先讨论，不要修改草稿",AiConversationService.Intent.DIALOGUE,reply("{\"invalid\":true}"));
        assertEquals(original,s.draft());assertEquals("SUCCEEDED",s.turns().getLast().state());assertNull(s.recommendedMode());verify(provider,times(1)).generate(any());
    }
    @Test void earlyRequirementsSurviveMoreThanSixTurnsAndEditsAreVersioned()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.PLAN,true);
        s=send(s,"我想学Java，两个月，每天两小时，星期日休息",AiConversationService.Intent.DIALOGUE,reply("null"));
        for(int i=0;i<7;i++)s=send(s,"补充练习偏好"+i,AiConversationService.Intent.DIALOGUE,reply("null"));
        assertTrue(s.requirementsText().contains("星期日休息"));
        ai.enqueue(owner,s.id(),s.version(),"final-generation","请生成",AiConversationService.Intent.GENERATE);var work=ai.claim();var context=json.readTree(work.context());
        assertTrue(context.path("requirements").asText().contains("每天两小时"));assertEquals(6,context.path("history").size());
        ai.fail(work,new AiFailure(AiFailure.Code.DISABLED,false));var latest=ai.read(owner,s.id());
        assertThrows(ConflictException.class,()->ai.saveRequirements(owner,latest.id(),latest.version()-1,"changed"));
        assertThrows(NotFoundException.class,()->ai.saveRequirements(other,latest.id(),latest.version(),"changed"));
        assertEquals("明确改为周六休息",ai.saveRequirements(owner,latest.id(),latest.version(),"明确改为周六休息").requirementsText());
    }
    @Test void intentMismatchAndNoteOverflowRejectBeforeQuotaAndSameKeyCannotChangeIntent(){
        var s=ai.create(owner,null,true);
        assertThrows(IllegalArgumentException.class,()->ai.enqueue(owner,s.id(),0,"no-mode-request","生成",AiConversationService.Intent.GENERATE));
        assertEquals(0,quotas.usage(owner).pending());
        ai.enqueue(owner,s.id(),0,"same-operation","补充",AiConversationService.Intent.DIALOGUE);
        assertThrows(ConflictException.class,()->ai.enqueue(owner,s.id(),0,"same-operation","补充",AiConversationService.Intent.AUTO));
        var w=ai.claim();ai.fail(w,new AiFailure(AiFailure.Code.DISABLED,false));var next=ai.read(owner,s.id());next=ai.saveRequirements(owner,s.id(),next.version(),"x".repeat(8000));
        var full=next;assertThrows(IllegalArgumentException.class,()->ai.enqueue(owner,full.id(),full.version(),"notes-overflow","y",AiConversationService.Intent.DIALOGUE));assertEquals(0,quotas.usage(owner).pending());
    }
    @Test void rejectedDraftRetainsReadableReplyAndNeverEnablesApply()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.PLAN,true);s=send(s,"开始生成",AiConversationService.Intent.GENERATE,reply("{}"));
        assertEquals("FAILED",s.turns().getLast().state());assertTrue(s.turns().getLast().reply().contains("请核对"));assertTrue(s.turns().getLast().diagnostic().replyOnly());
        assertEquals(2,s.turns().getLast().diagnostic().attempts());assertEquals("DRAFT_INVALID",s.turns().getLast().diagnostic().reason());assertNull(s.draft());
        var failed=s;assertThrows(IllegalArgumentException.class,()->ai.apply(owner,failed.id(),failed.version(),true));
    }
    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void syntheticModesDiscussGenerateAndReviseWithoutImplicitBusinessWrites(AiDrafts.Mode mode)throws Exception{
        for(String expression:List.of("零基础，每天两小时", "时间无所谓，在学校不能去健身房", "空闲才学习吉他，不知道何时结束", "今天有几个零散事项")){
            var s=ai.create(owner,mode,true);s=send(s,expression,AiConversationService.Intent.DIALOGUE,reply("null"));assertNull(s.draft());
            s=send(s,"请按已确认条件生成",AiConversationService.Intent.GENERATE,reply(draft(mode)));assertNotNull(s.draft());assertNull(s.applied());
            if(mode==AiDrafts.Mode.PLAN){assertEquals(List.of(7),s.draft().restDays());assertEquals(LocalDate.of(2026,12,20),s.draft().endDate());}
            if(mode==AiDrafts.Mode.ROUTE)assertNull(s.draft().endDate());
            s=send(s,"保持约束，调整节奏",AiConversationService.Intent.REVISE,reply(draft(mode)));assertNotNull(s.draft());assertNull(s.applied());
            ai.delete(owner,s.id(),s.version());
        }
    }
}
