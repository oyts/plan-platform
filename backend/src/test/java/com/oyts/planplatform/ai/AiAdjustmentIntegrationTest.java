package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
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
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.ai.enabled=true","app.ai.api-key-file=/fake-test-key","app.ai.worker-enabled=false"})
@AutoConfigureMockMvc
class AiAdjustmentIntegrationTest {
    @Autowired AiConversationService sessions; @Autowired AiQuotaService quotas; @Autowired AiAdjustmentService adjustments;
    @Autowired JdbcTemplate db; @Autowired ObjectMapper mapper; @Autowired UserAccountRepository users; @Autowired MockMvc mvc;
    @MockBean AiProvider provider;
    String owner,other,admin;
    String user(boolean isAdmin){var u=new UserAccount("adjust-"+UUID.randomUUID()+"@example.test","unused-test-hash","调整验收");if(isAdmin)u.makeAdmin();return users.saveAndFlush(u).getId();}
    @BeforeEach void setup(){db.update("DELETE FROM ai_sessions");db.update("DELETE FROM ai_quota_audit");db.update("DELETE FROM ai_quota_requests");db.update("DELETE FROM ai_user_quota");db.update("UPDATE ai_quota_policy SET daily_limit=0,version=0 WHERE id=1");owner=user(false);other=user(false);admin=user(true);quotas.changePolicy(admin,20,0);}
    @AfterEach void clean(){db.update("DELETE FROM ai_sessions");}
    JsonNode create(String path,Map<String,Object> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api"+path).with(jwt().jwt(j->j.subject(owner))).contentType("application/json").content(mapper.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    String fixture(AiDrafts.Mode mode) throws Exception {
        if(mode==AiDrafts.Mode.TODO) return create("/todos",Map.of("title","原待办","note","原说明","dueDate","2026-10-16")).path("id").asText();
        if(mode==AiDrafts.Mode.PLAN) {
            String id=create("/plans",Map.of("name","原周期","description","保留说明","startDate","2026-10-01","endDate","2026-10-31","restDays",List.of(7))).path("id").asText();
            for(int i=1;i<=2;i++) create("/plans/"+id+"/tasks",Map.of("date","2026-10-16","title","原任务"+i,"category","学习","description","原说明","plannedMinutes",30,"acceptance",List.of("保留验收"),"resourceUrl","https://example.test/resource","required",true,"sortOrder",i));
            return id;
        }
        String id=create("/mainlines",Map.of("name","原路线","description","保留说明","color","#002FA7","executionMode","SEQUENTIAL")).path("id").asText();
        String stage=create("/mainlines/"+id+"/stages",Map.of("name","原阶段","description","阶段说明","sortOrder",0)).path("id").asText();
        for(int i=1;i<=2;i++) create("/mainlines/"+id+"/items",Map.of("title","原步骤"+i,"description","原说明","plannedMinutes",30,"sortOrder",i,"acceptance",List.of("保留验收"),"routing",Map.of("stageId",stage,"dependencyMode","INHERIT","prerequisiteIds",List.of())));
        return id;
    }
    ObjectNode draft(AiConversationService.Session session){return mapper.valueToTree(session.draft());}
    ObjectNode item(ObjectNode draft,int index){return (ObjectNode)draft.withArray("items").get(index);}
    AiConversationService.Session save(AiConversationService.Session session,ObjectNode draft){return sessions.saveDraft(owner,session.id(),session.version(),draft.toString());}
    String real(AiConversationService.Session s,int i){return adjustments.decode(db.queryForObject("SELECT adjustment_json FROM ai_sessions WHERE id=?",String.class,s.id())).items().get(s.draft().items().get(i).localId());}

    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void updatesOnlySelectedObjectAndReplaysWithoutDuplicatingOrLosingMetadata(AiDrafts.Mode mode) throws Exception {
        String id=fixture(mode);var initial=sessions.createAdjustment(owner,mode,id,true);var proposal=draft(initial);
        String firstId=real(initial,0);item(proposal,0).put("title","调整后的标题").put("note","新的执行说明");
        var ready=save(initial,proposal);assertThrows(NotFoundException.class,()->sessions.read(other,ready.id()));
        assertThrows(IllegalArgumentException.class,()->sessions.apply(owner,ready.id(),ready.version(),false));
        var result=sessions.apply(owner,ready.id(),ready.version(),true);assertEquals(result,sessions.apply(owner,ready.id(),ready.version(),true));
        String table=mode==AiDrafts.Mode.PLAN?"plan_tasks":mode==AiDrafts.Mode.ROUTE?"mainline_items":"todos";
        assertEquals("调整后的标题",db.queryForObject("SELECT title FROM "+table+" WHERE id=?",String.class,firstId));
        assertEquals(mode==AiDrafts.Mode.TODO?1:2,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+(mode==AiDrafts.Mode.PLAN?"plan_id":mode==AiDrafts.Mode.ROUTE?"mainline_id":"owner_id")+"=?",Integer.class,mode==AiDrafts.Mode.TODO?owner:id));
        if(mode!=AiDrafts.Mode.TODO) assertEquals("[\"保留验收\"]",db.queryForObject("SELECT acceptance_json FROM "+table+" WHERE id=?",String.class,firstId));
        if(mode==AiDrafts.Mode.PLAN) assertEquals("https://example.test/resource",db.queryForObject("SELECT resource_url FROM plan_tasks WHERE id=?",String.class,firstId));
        if(mode==AiDrafts.Mode.ROUTE) assertEquals("INHERIT",db.queryForObject("SELECT dependency_mode FROM mainline_items WHERE id=?",String.class,firstId));
        sessions.delete(owner,ready.id(),sessions.read(owner,ready.id()).version());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=?",Integer.class,firstId));
        assertEquals(0,quotas.usage(owner).used());
    }
    @Test void rejectsObjectOwnershipMissingConsentDeletesAndChangingMode() throws Exception {
        String id=fixture(AiDrafts.Mode.PLAN);
        assertThrows(NotFoundException.class,()->sessions.createAdjustment(other,AiDrafts.Mode.PLAN,id,true));
        assertThrows(IllegalArgumentException.class,()->sessions.createAdjustment(owner,AiDrafts.Mode.PLAN,id,false));
        var initial=sessions.createAdjustment(owner,AiDrafts.Mode.PLAN,id,true);
        var proposal=draft(initial);proposal.withArray("items").remove(0);
        assertThrows(IllegalArgumentException.class,()->save(initial,proposal));
        assertThrows(ConflictException.class,()->sessions.choose(owner,initial.id(),0,AiDrafts.Mode.ROUTE));
        mvc.perform(post("/api/ai/adjustments").with(jwt().jwt(j->j.subject(other))).contentType("application/json").content("{\"mode\":\"PLAN\",\"targetId\":\""+id+"\",\"consent\":true}")).andExpect(status().isNotFound());
    }
    @ParameterizedTest @EnumSource(AiDrafts.Mode.class)
    void progressOrChildChangesInvalidateSnapshotEvenWithoutParentVersionChange(AiDrafts.Mode mode) throws Exception {
        String id=fixture(mode);var initial=sessions.createAdjustment(owner,mode,id,true);var proposal=draft(initial);item(proposal,0).put("title","不应覆盖");
        var ready=save(initial,proposal);String firstId=real(initial,0);
        if(mode==AiDrafts.Mode.PLAN) db.update("UPDATE task_progress SET status='RUNNING',started_at=CURRENT_TIMESTAMP,version=version+1 WHERE task_id=?",firstId);
        else db.update("UPDATE "+(mode==AiDrafts.Mode.ROUTE?"mainline_items":"todos")+" SET title='其他设备已修改',version=version+1 WHERE id=?",firstId);
        assertThrows(ConflictException.class,()->sessions.apply(owner,ready.id(),ready.version(),true));
        assertNull(sessions.read(owner,ready.id()).applied());assertNotNull(sessions.read(owner,ready.id()).draft());
        assertNotEquals("不应覆盖",db.queryForObject("SELECT title FROM "+(mode==AiDrafts.Mode.PLAN?"plan_tasks":mode==AiDrafts.Mode.ROUTE?"mainline_items":"todos")+" WHERE id=?",String.class,firstId));
    }
    @Test void staleSnapshotCannotReserveQuotaOrSubmitPaidWork() throws Exception {
        String id=fixture(AiDrafts.Mode.TODO);var initial=sessions.createAdjustment(owner,AiDrafts.Mode.TODO,id,true);
        db.update("UPDATE todos SET title='另一设备修改',version=version+1 WHERE id=?",id);
        assertThrows(ConflictException.class,()->sessions.enqueue(owner,initial.id(),0,"stale-adjustment-job","请调整"));
        assertEquals(0,quotas.usage(owner).pending());assertEquals(0,quotas.usage(owner).used());
        assertTrue(sessions.read(owner,initial.id()).turns().isEmpty());
    }
    @Test void protectsCompletedTimedAndAnnotatedPlanTasksAndKeepsLegacyRestDay() throws Exception {
        String id=fixture(AiDrafts.Mode.PLAN);
        String task=db.queryForObject("SELECT id FROM plan_tasks WHERE plan_id=? AND sort_order=1",String.class,id);
        db.update("UPDATE plan_tasks SET task_date='2026-10-18' WHERE id=?",task);
        db.update("UPDATE task_progress SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,note='完成记录',version=version+1 WHERE task_id=?",task);
        var initial=sessions.createAdjustment(owner,AiDrafts.Mode.PLAN,id,true);
        String alias=initial.draft().items().stream().filter(i->i.dueDate().getDayOfWeek().getValue()==7).findFirst().orElseThrow().localId();
        assertTrue(initial.adjustment().protectedItems().contains(alias));
        var proposal=draft(initial);for(var n:proposal.withArray("items")) if(n.path("localId").asText().equals(alias)) ((ObjectNode)n).put("title","不能改写历史");
        assertThrows(IllegalArgumentException.class,()->save(initial,proposal));
        var safe=draft(initial);for(var n:safe.withArray("items")) if(!n.path("localId").asText().equals(alias)) ((ObjectNode)n).put("plannedMinutes",15);
        var ready=save(initial,safe);sessions.apply(owner,ready.id(),ready.version(),true);
        assertEquals("完成记录",db.queryForObject("SELECT note FROM task_progress WHERE task_id=?",String.class,task));
        assertEquals("原任务1",db.queryForObject("SELECT title FROM plan_tasks WHERE id=?",String.class,task));
    }
    @Test void rejectsShrinkingRangeAndNewRestDaySchedulingButCanExtendPeriod() throws Exception {
        var initial=sessions.createAdjustment(owner,AiDrafts.Mode.PLAN,fixture(AiDrafts.Mode.PLAN),true);
        var shrink=draft(initial);shrink.put("endDate","2026-10-30");assertThrows(IllegalArgumentException.class,()->save(initial,shrink));
        var rest=draft(initial);rest.putArray("restDays").add(5);assertThrows(IllegalArgumentException.class,()->save(initial,rest));
        var extend=draft(initial);extend.put("endDate","2026-11-07");item(extend,0).put("dueDate","2026-11-02");var ready=save(initial,extend);
        sessions.apply(owner,ready.id(),ready.version(),true);
        assertEquals("2026-11-07",db.queryForObject("SELECT end_date FROM plans WHERE owner_id=?",String.class,owner));
    }
    @Test void twoWeekPacingPreservesHistoryAndAppliesOnlyPendingChanges() throws Exception {
        String id=fixture(AiDrafts.Mode.PLAN);
        db.update("UPDATE plans SET start_date='2026-10-12',end_date='2026-10-25' WHERE id=?",id);
        String completed=db.queryForObject("SELECT id FROM plan_tasks WHERE plan_id=? AND sort_order=1",String.class,id);
        String pending=db.queryForObject("SELECT id FROM plan_tasks WHERE plan_id=? AND sort_order=2",String.class,id);
        db.update("UPDATE task_progress SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP,note='原完成记录',version=version+1 WHERE task_id=?",completed);
        var initial=sessions.createAdjustment(owner,AiDrafts.Mode.PLAN,id,true);
        var proposal=draft(initial);proposal.put("endDate","2026-11-01");
        for(var n:proposal.withArray("items"))if(!initial.adjustment().protectedItems().contains(n.path("localId").asText()))((ObjectNode)n).put("dueDate","2026-10-26").put("plannedMinutes",15).put("note","放慢后的练习");
        var ready=save(initial,proposal);
        assertEquals("2026-10-25",db.queryForObject("SELECT end_date FROM plans WHERE id=?",String.class,id));
        assertEquals(30,db.queryForObject("SELECT planned_minutes FROM plan_tasks WHERE id=?",Integer.class,pending));
        var applied=sessions.apply(owner,ready.id(),ready.version(),true);
        assertEquals(applied,sessions.apply(owner,ready.id(),ready.version(),true));
        assertEquals("2026-11-01",db.queryForObject("SELECT end_date FROM plans WHERE id=?",String.class,id));
        assertEquals("2026-10-26",db.queryForObject("SELECT task_date FROM plan_tasks WHERE id=?",String.class,pending));
        assertEquals(15,db.queryForObject("SELECT planned_minutes FROM plan_tasks WHERE id=?",Integer.class,pending));
        assertEquals("2026-10-16",db.queryForObject("SELECT task_date FROM plan_tasks WHERE id=?",String.class,completed));
        assertEquals(30,db.queryForObject("SELECT planned_minutes FROM plan_tasks WHERE id=?",Integer.class,completed));
        assertEquals("原完成记录",db.queryForObject("SELECT note FROM task_progress WHERE task_id=?",String.class,completed));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM plan_tasks WHERE plan_id=?",Integer.class,id));
        assertEquals(0,quotas.usage(owner).used());
    }
    @Test void newStepsAndStagesAreAtomicAndDependenciesMapToRealIds() throws Exception {
        String id=fixture(AiDrafts.Mode.ROUTE);var initial=sessions.createAdjustment(owner,AiDrafts.Mode.ROUTE,id,true);var proposal=draft(initial);
        proposal.withArray("stages").add(mapper.createObjectNode().put("localId","new_stage").put("title","新阶段"));
        var added=item(proposal,0).deepCopy().put("localId","new_step").put("title","新增步骤").put("stageId","new_stage");
        added.putArray("prerequisites").add(initial.draft().items().getLast().localId());proposal.withArray("items").add(added);
        var ready=save(initial,proposal);sessions.apply(owner,ready.id(),ready.version(),true);
        String stage=db.queryForObject("SELECT id FROM mainline_stages WHERE mainline_id=? AND name='新阶段'",String.class,id);
        assertEquals(stage,db.queryForObject("SELECT stage_id FROM mainline_items WHERE mainline_id=? AND title='新增步骤'",String.class,id));
        assertTrue(db.queryForObject("SELECT prerequisite_ids_json FROM mainline_items WHERE mainline_id=? AND title='新增步骤'",String.class,id).contains(real(initial,1)));
        assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM mainline_items WHERE mainline_id=?",Integer.class,id));
    }
    @Test void batchFailureRollsBackOldEditsNewItemsAndApplyMarker() throws Exception {
        String id=fixture(AiDrafts.Mode.TODO);var initial=sessions.createAdjustment(owner,AiDrafts.Mode.TODO,id,true);var proposal=draft(initial);item(proposal,0).put("title","应回滚修改");
        var added=item(proposal,0).deepCopy().put("localId","new_todo").put("title","故意失败");proposal.withArray("items").add(added);var ready=save(initial,proposal);
        db.execute("ALTER TABLE todos ADD CONSTRAINT ai_adjust_batch_failure CHECK (owner_id <> '"+owner+"' OR title <> '故意失败')");
        try {assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->sessions.apply(owner,ready.id(),ready.version(),true));
            assertEquals("原待办",db.queryForObject("SELECT title FROM todos WHERE id=?",String.class,id));assertNull(sessions.read(owner,ready.id()).applied());
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM todos WHERE owner_id=?",Integer.class,owner));
        }finally{db.execute("ALTER TABLE todos DROP CONSTRAINT ai_adjust_batch_failure");}
    }
    @Test void providerGetsOnlyConsentedSelectedContentAndCannotRewriteProtectedHistory() throws Exception {
        String id=fixture(AiDrafts.Mode.TODO);db.update("UPDATE todos SET done=true WHERE id=?",id);
        var initial=sessions.createAdjustment(owner,AiDrafts.Mode.TODO,id,true);var proposal=draft(initial);item(proposal,0).put("title","无效历史修改");
        sessions.enqueue(owner,initial.id(),0,"adjust-protected-test","请修改");var work=sessions.claim();assertNotNull(work);
        assertTrue(work.context().contains("ADJUST_EXISTING"));assertFalse(work.context().contains(id));assertFalse(work.context().contains(owner));
        assertThrows(AiFailure.class,()->sessions.succeed(work,new AiProvider.Result("{\"message\":\"修改建议\",\"recommendedMode\":\"TODO\",\"draft\":"+proposal+"}",10,20)));
        sessions.fail(work,new AiFailure(AiFailure.Code.INVALID_OUTPUT));assertEquals("FAILED",sessions.read(owner,initial.id()).turns().getFirst().state());
        assertEquals(0,quotas.usage(owner).used());assertEquals(0,quotas.usage(owner).pending());
        assertEquals("原待办",db.queryForObject("SELECT title FROM todos WHERE id=?",String.class,id));
    }
}
