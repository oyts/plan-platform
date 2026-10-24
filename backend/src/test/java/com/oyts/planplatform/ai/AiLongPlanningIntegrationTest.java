package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
class AiLongPlanningIntegrationTest {
    @Autowired AiConversationService ai;@Autowired AiQuotaService quotas;@Autowired AiAttempts attempts;@Autowired AiProperties config;
    @Autowired UserAccountRepository users;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;@Autowired AiCollaborationService collaboration;@Autowired AiWorkbenchService workbench;
    @MockBean AiProvider provider;String owner,other;
    String user(){return users.saveAndFlush(new UserAccount("long-"+UUID.randomUUID()+"@example.test","unused-hash","长规划合成验收")).getId();}
    @BeforeEach void setup(){db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");owner=user();other=user();quotas.changePolicy(owner,100,0);reset(provider);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    AiDrafts.Draft draft(AiDrafts.Mode mode,int count){var day=LocalDate.of(2026,11,2);var items=new ArrayList<AiDrafts.Item>();for(int i=0;i<count;i++)items.add(new AiDrafts.Item("t"+i,"大纲事项"+i,"待细化",mode==AiDrafts.Mode.ROUTE?null:day.plusDays(i),30,mode==AiDrafts.Mode.ROUTE?"s1":null,mode==AiDrafts.Mode.ROUTE&&i>0?List.of("t"+(i-1)):List.of()));return new AiDrafts.Draft(1,mode,0,"完整大纲",mode==AiDrafts.Mode.PLAN?day:null,mode==AiDrafts.Mode.PLAN?day.plusDays(count):null,List.of(),mode==AiDrafts.Mode.ROUTE?List.of(new AiDrafts.Stage("s1","基础")):List.of(),items);}
    AiConversationService.Session prepared(AiDrafts.Mode mode,int count)throws Exception{var s=ai.create(owner,mode,true);s=ai.saveDraft(owner,s.id(),s.version(),json.writeValueAsString(draft(mode,count)));return ai.startLongPlan(owner,s.id(),s.version(),"全部事项、日期及依赖已核对",true);}
    ObjectNode partial(AiConversationService.Session s){var node=(ObjectNode)json.valueToTree(s.draft());var next=s.longPlan().batches().stream().filter(b->!b.complete()).findFirst().orElseThrow();var items=json.createArrayNode();for(var item:s.draft().items())if(next.itemIds().contains(item.localId())){ObjectNode copy=json.valueToTree(item);copy.put("note","详细练习：执行、检查、回顾");items.add(copy);}node.set("items",items);return node;}
    String reply(JsonNode draft)throws Exception{return json.writeValueAsString(Map.of("message","仅本批已细化，尚未生成真实计划。","recommendedMode",json.nullNode(),"draft",draft));}
    void run(AiConversationService.Work work){try(var guard=new AiExecutionGuard(1,0)){var runner=new AiJobRunner(ai,provider,guard,config,attempts,false,true);try{runner.run(work);}finally{runner.close();}}}
    AiConversationService.Session batch(AiConversationService.Session s,JsonNode response)throws Exception{when(provider.generate(any())).thenReturn(new AiProvider.Result(reply(response),100,200));ai.enqueue(owner,s.id(),s.version(),UUID.randomUUID().toString(),"细化本批",AiConversationService.Intent.BATCH);run(ai.claim());return ai.read(owner,s.id());}
    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void threeModesCompleteInBatchesThenApplyOnceWithStableIdsAndDependencies(AiDrafts.Mode mode)throws Exception{
        var s=prepared(mode,21);String id=s.id();var before=s.draft();long initial=s.version();
        assertEquals(3,s.longPlan().batches().size());assertThrows(ConflictException.class,()->ai.apply(owner,id,initial,true));
        s=batch(s,partial(s));assertTrue(s.longPlan().batches().getFirst().complete());assertFalse(s.longPlan().complete());assertEquals(before.items().subList(10,21),s.draft().items().subList(10,21));
        var firstTen=s.draft().items().subList(0,10);s=batch(s,partial(s));assertEquals(firstTen,s.draft().items().subList(0,10));
        s=ai.saveBatch(owner,id,s.version(),partial(s).toString());assertTrue(s.longPlan().complete());assertEquals(2,s.longPlan().submissions());
        assertEquals(before.items().stream().map(AiDrafts.Item::localId).toList(),s.draft().items().stream().map(AiDrafts.Item::localId).toList());
        for(int i=0;i<21;i++)assertEquals(before.items().get(i).prerequisites(),s.draft().items().get(i).prerequisites());
        var result=ai.apply(owner,id,s.version(),true);assertEquals(21,result.path("count").intValue());assertEquals(result,ai.apply(owner,id,s.version(),true));assertEquals(2,quotas.usage(owner).used());
    }
    @Test void failedBatchAndFailedRepairKeepEveryPriorDetailAndBudget()throws Exception{
        var s=prepared(AiDrafts.Mode.ROUTE,11);s=batch(s,partial(s));var saved=s.draft();var bad=partial(s);bad.withArray("items").get(0).deepCopy();((ObjectNode)bad.withArray("items").get(0)).put("stageId","foreign-stage");
        s=batch(s,bad);assertEquals("FAILED",s.turns().getLast().state());assertEquals(saved,s.draft());assertFalse(s.longPlan().complete());assertEquals(2,s.longPlan().submissions());assertEquals(2,s.turns().getLast().diagnostic().attempts());
        s=batch(s,partial(s));assertTrue(s.longPlan().complete());assertEquals(3,s.longPlan().submissions());
    }
    @Test void workflowBudgetSurvivesReadsAndCannotBeResetOrAutomaticallyFanOut()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,1);String id=s.id();
        for(int i=0;i<12;i++)s=batch(s,json.createObjectNode());
        var current=ai.read(owner,id);assertEquals(12,current.longPlan().submissions());assertEquals(24,current.longPlan().reservedCalls());assertEquals(98304,current.longPlan().reservedOutputTokens());assertEquals(1536000,current.longPlan().reservedInputTokens());
        assertThrows(ConflictException.class,()->ai.enqueue(owner,id,current.version(),"over-workflow-budget","细化",AiConversationService.Intent.BATCH));
        assertThrows(ConflictException.class,()->ai.startLongPlan(owner,id,current.version(),"试图重置",true));assertEquals(12,ai.read(owner,id).turns().size());assertEquals(24,db.queryForObject("SELECT COUNT(*) FROM ai_job_attempts",Integer.class));
        var done=ai.saveBatch(owner,id,current.version(),partial(current).toString());assertTrue(done.longPlan().complete());assertEquals(12,done.longPlan().submissions());
    }
    @Test void expiredQueuedWorkNeverCallsProviderAndUnknownDoesNotRefundWorkflowBudget()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,1);ai.enqueue(owner,s.id(),s.version(),"expiry-test-job","细化",AiConversationService.Intent.BATCH);
        db.update("UPDATE ai_long_plans SET expires_at=? WHERE session_id=?",Timestamp.from(Instant.now().minusSeconds(1)),s.id());run(ai.claim());verifyNoInteractions(provider);assertEquals("FAILED",ai.read(owner,s.id()).turns().getLast().state());assertEquals(1,ai.read(owner,s.id()).longPlan().submissions());assertEquals(0,quotas.usage(owner).pending());
        var another=prepared(AiDrafts.Mode.ROUTE,1);ai.enqueue(owner,another.id(),another.version(),"unknown-test-job","细化",AiConversationService.Intent.BATCH);var work=ai.claim();ai.fail(work,new AiFailure(AiFailure.Code.TIMEOUT,true));assertEquals(1,ai.read(owner,another.id()).longPlan().submissions());assertEquals(1,quotas.usage(owner).pending());assertNull(ai.claim());
    }
    @Test void sameKeyConcurrentSubmissionsReserveExactlyOneAndForeignOrStaleCannotMutate()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,1);assertThrows(NotFoundException.class,()->ai.startLongPlan(other,s.id(),s.version(),"foreign",true));assertThrows(ConflictException.class,()->ai.saveBatch(owner,s.id(),s.version()-1,partial(s).toString()));
        try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->ai.enqueue(owner,s.id(),s.version(),"same-batch-operation","细化",AiConversationService.Intent.BATCH));var b=pool.submit(()->ai.enqueue(owner,s.id(),s.version(),"same-batch-operation","细化",AiConversationService.Intent.BATCH));assertEquals(a.get().id(),b.get().id());}
        assertEquals(1,ai.read(owner,s.id()).longPlan().submissions());assertEquals(1,quotas.usage(owner).pending());
    }
    @Test void outlineCannotBeAppliedOrResumedAroundTheBatchCompletionGate()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.PLAN,true);when(provider.generate(any())).thenReturn(new AiProvider.Result(reply(json.valueToTree(draft(AiDrafts.Mode.PLAN,11))),100,200));ai.enqueue(owner,s.id(),s.version(),"outline-only-request","完整大纲",AiConversationService.Intent.OUTLINE);run(ai.claim());var outline=ai.read(owner,s.id());assertTrue(outline.outlineOnly());assertThrows(ConflictException.class,()->ai.apply(owner,outline.id(),outline.version(),true));
        var content=json.createObjectNode().put("schemaVersion",1).put("mode","PLAN").put("message","").put("importJson","").put("sourceSessionId",outline.id()).put("sourceVersion",outline.version());content.set("draft",json.valueToTree(outline.draft()));var saved=workbench.save(owner,UUID.randomUUID().toString(),"大纲副本",-1,content.toString());assertThrows(ConflictException.class,()->workbench.resume(owner,saved.id(),saved.version(),true));
    }
    @Test void manualMutationCannotChangeIdsDatesDependenciesOrOtherBatches()throws Exception{
        var s=prepared(AiDrafts.Mode.ROUTE,11);var original=s.draft();var node=partial(s);((ObjectNode)node.withArray("items").get(0)).putArray("prerequisites").add("t10");assertThrows(AiFailure.class,()->ai.saveBatch(owner,s.id(),s.version(),node.toString()));assertEquals(original,ai.read(owner,s.id()).draft());assertEquals(0,ai.read(owner,s.id()).longPlan().submissions());
        var invalid=partial(s);invalid.withArray("items").remove(0);assertThrows(AiFailure.class,()->ai.saveBatch(owner,s.id(),s.version(),invalid.toString()));
        assertThrows(ConflictException.class,()->ai.saveDraft(owner,s.id(),s.version(),json.writeValueAsString(s.draft())));
    }
    @Test void oversizedBatchContextRejectsBeforeDailyOrWorkflowReservation()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,10);s=ai.saveRequirements(owner,s.id(),s.version(),"需求".repeat(4000));var stable=s;
        // The input message also has to fit retained requirements before paid admission.
        assertThrows(IllegalArgumentException.class,()->ai.enqueue(owner,stable.id(),stable.version(),"overflow-batch-request","新增要求",AiConversationService.Intent.BATCH));assertEquals(0,ai.read(owner,s.id()).longPlan().submissions());assertEquals(0,quotas.usage(owner).pending());
    }
    @Test void failedFinalBusinessApplyRollsBackAndKeepsCompletedDraft()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,2);s=ai.saveBatch(owner,s.id(),s.version(),partial(s).toString());
        // A transaction failure after the first insert proves no partial batch business write.
        db.execute("ALTER TABLE todos ADD CONSTRAINT synthetic_reject_second CHECK (title <> '大纲事项1')");var ready=s;
        try{assertThrows(RuntimeException.class,()->ai.apply(owner,ready.id(),ready.version(),true));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM todos WHERE owner_id=?",Integer.class,owner));assertNull(ai.read(owner,s.id()).applied());assertTrue(ai.read(owner,s.id()).longPlan().complete());}finally{db.execute("ALTER TABLE todos DROP CONSTRAINT synthetic_reject_second");}
    }
    @Test void fullCapacityAndWholeDependencyGraphStayWithinDeclaredBounds()throws Exception{
        var s=prepared(AiDrafts.Mode.ROUTE,100);assertEquals(10,s.longPlan().batches().size());for(int i=0;i<10;i++)s=ai.saveBatch(owner,s.id(),s.version(),partial(s).toString());assertTrue(s.longPlan().complete());assertEquals(100,s.draft().items().size());assertEquals(List.of("t98"),s.draft().items().getLast().prerequisites());assertEquals(0,s.longPlan().submissions());
        var extra=ai.create(owner,AiDrafts.Mode.ROUTE,true);assertThrows(AiFailure.class,()->ai.saveDraft(owner,extra.id(),0,json.writeValueAsString(draft(AiDrafts.Mode.ROUTE,101))));
    }
    @Test void progressProtectedItemsAreExcludedFromBatchesAndKeptAfterAdjustment()throws Exception{
        var s=ai.create(owner,AiDrafts.Mode.PLAN,true);s=ai.saveDraft(owner,s.id(),0,json.writeValueAsString(draft(AiDrafts.Mode.PLAN,12)));String id=ai.apply(owner,s.id(),s.version(),true).path("path").asText().substring(7);
        String task=db.queryForObject("SELECT id FROM plan_tasks WHERE plan_id=? AND title='大纲事项0'",String.class,id);db.update("UPDATE task_progress SET status='DONE',accumulated_seconds=600,version=version+1 WHERE task_id=?",task);
        var adjusted=ai.createAdjustment(owner,AiDrafts.Mode.PLAN,id,true);String protectedId=adjusted.adjustment().protectedItems().iterator().next();var flow=ai.startLongPlan(owner,adjusted.id(),adjusted.version(),"全部未开始事项，已完成项保留",true);assertFalse(flow.longPlan().batches().stream().anyMatch(b->b.itemIds().contains(protectedId)));var protectedItem=flow.draft().items().stream().filter(i->i.localId().equals(protectedId)).findFirst().orElseThrow();
        while(!flow.longPlan().complete())flow=ai.saveBatch(owner,flow.id(),flow.version(),partial(flow).toString());assertEquals(protectedItem,flow.draft().items().stream().filter(i->i.localId().equals(protectedId)).findFirst().orElseThrow());ai.apply(owner,flow.id(),flow.version(),true);assertEquals(600,db.queryForObject("SELECT accumulated_seconds FROM task_progress WHERE task_id=?",Integer.class,task));assertEquals("DONE",db.queryForObject("SELECT status FROM task_progress WHERE task_id=?",String.class,task));
    }
    @Test void providerUsageOutsideRequestedBudgetNeverTriggersAnotherPaidAttempt()throws Exception{
        var s=prepared(AiDrafts.Mode.TODO,1);when(provider.generate(any())).thenReturn(new AiProvider.Result(reply(partial(s)),100,5000));ai.enqueue(owner,s.id(),s.version(),"oversized-provider-usage","细化",AiConversationService.Intent.BATCH);run(ai.claim());var current=ai.read(owner,s.id());assertEquals("FAILED",current.turns().getLast().state());assertEquals("BUDGET_EXCEEDED",current.turns().getLast().diagnostic().reason());assertEquals(1,current.turns().getLast().diagnostic().attempts());assertFalse(current.longPlan().complete());verify(provider,times(1)).generate(any());
    }
}
