package com.oyts.planplatform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.registration-enabled=true") @AutoConfigureMockMvc
class DailyExperienceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    record Account(String token,String id) {}
    private Account account() throws Exception {
        var r=post(null,"/auth/register",Map.of("email","daily-"+UUID.randomUUID()+"@example.test","password","ExperienceTest123!","displayName","阶段验收"),201);
        return new Account(r.path("accessToken").asText(),r.path("user").path("id").asText());
    }
    private JsonNode post(Account a,String path,Object value,int code) throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api"+path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value));
        if(a!=null)request.header("Authorization","Bearer "+a.token());
        return mapper.readTree(mvc.perform(request).andExpect(status().is(code)).andReturn().getResponse().getContentAsString());
    }
    private JsonNode put(Account a,String path,Object value,int code) throws Exception {
        return mapper.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api"+path).header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(value))).andExpect(status().is(code)).andReturn().getResponse().getContentAsString());
    }
    private JsonNode read(Account a,String path) throws Exception {
        return mapper.readTree(mvc.perform(get("/api"+path).header("Authorization","Bearer "+a.token())).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    private JsonNode todo(Account a,String title,String date) throws Exception { return post(a,"/todos",Map.of("title",title,"dueDate",date),201); }
    private JsonNode plan(Account a) throws Exception { return post(a,"/plans",Map.of("name","周期验收","startDate","2026-10-01","endDate","2026-11-30","restDays",List.of(7)),201); }
    private JsonNode task(Account a,String plan,String date,String title) throws Exception {
        return post(a,"/plans/"+plan+"/tasks",Map.of("date",date,"plannedMinutes",20,"category","STUDY","title",title,"acceptance",List.of(),"required",true,"sortOrder",0),201);
    }
    private Map<String,Object> preferences(boolean enabled,Long version) {
        var data=new HashMap<String,Object>();data.put("enabled",enabled);data.put("includePlans",true);data.put("includeTodos",true);data.put("showOverdue",true);data.put("lookAheadDays",3);data.put("version",version);return data;
    }
    @Test void overviewSeparatesModesDatesDeferralsAndOwners() throws Exception {
        var a=account();var other=account();String plan=plan(a).path("id").asText();
        task(a,plan,"2026-10-18","今天任务");var old=task(a,plan,"2026-10-17","改期任务");task(a,plan,"2026-10-16","逾期任务");
        post(a,"/tasks/"+old.path("id").asText()+"/defer",Map.of("deferTo","2026-10-20","version",old.path("progress").path("version").asLong()),200);
        todo(a,"今天待办","2026-10-18");todo(a,"早期待办","2025-01-01");todo(a,"未来待办","2026-10-21");todo(a,"太远待办","2026-11-01");
        todo(other,"不可泄漏","2026-10-18");
        var summary=read(a,"/daily/summary?date=2026-10-18&days=3");
        assertThat(summary.path("plan").path("today").asInt()).isEqualTo(1);assertThat(summary.path("plan").path("overdue").asInt()).isEqualTo(1);assertThat(summary.path("plan").path("upcoming").asInt()).isEqualTo(1);
        assertThat(summary.path("todo").path("overdue").asInt()).isEqualTo(1);
        var page=read(a,"/daily/items?date=2026-10-18&bucket=TODAY&size=1");assertThat(page.path("totalElements").asInt()).isEqualTo(2);assertThat(page.path("content").size()).isEqualTo(1);
        var next=read(a,"/daily/items?date=2026-10-18&bucket=TODAY&size=1&page=1");assertThat(next.path("content").get(0).path("id")).isNotEqualTo(page.path("content").get(0).path("id"));
        var upcoming=read(a,"/daily/items?date=2026-10-18&bucket=UPCOMING&mode=PLAN&days=3");assertThat(upcoming.path("content").get(0).path("dueDate").asText()).isEqualTo("2026-10-20");
        assertThat(read(other,"/daily/summary?date=2026-10-18").path("plan").path("today").asInt()).isZero();
    }
    @Test void completedSkippedDeletedAndArchivedAreNotRemindedAndRoutesHaveNoDueDate() throws Exception {
        var a=account();String plan=plan(a).path("id").asText();var done=task(a,plan,"2026-10-18","已完成");
        post(a,"/tasks/"+done.path("id").asText()+"/complete",Map.of("note","","acceptanceChecks",List.of(),"version",done.path("progress").path("version").asLong()),200);
        var todo=todo(a,"已完成待办","2026-10-18");put(a,"/todos/"+todo.path("id").asText(),Map.of("title","已完成待办","dueDate","2026-10-18","done",true,"deleted",false,"version",todo.path("version").asLong()),200);
        var line=post(a,"/mainlines",Map.of("name","吉他路线","description","无期限","color","#002FA7","executionMode","FLEXIBLE"),201);
        var overview=read(a,"/daily/summary?date=2026-10-18");assertThat(overview.path("plan").path("today").asInt()).isZero();assertThat(overview.path("todo").path("today").asInt()).isZero();assertThat(overview.path("activeRoutes").asInt()).isEqualTo(1);
        var routes=read(a,"/daily/routes");assertThat(routes.path("content").get(0).path("id").asText()).isEqualTo(line.path("id").asText());assertThat(routes.toString()).doesNotContain("dueDate");
        task(a,plan,"2026-10-18","归档不提醒");jdbc.update("update plans set archived=true where id=?",plan);
        assertThat(read(a,"/daily/items?date=2026-10-18").path("totalElements").asInt()).isZero();
    }
    @Test void remindersDefaultOffAreVersionedIsolatedAndCanBeDisabled() throws Exception {
        var a=account();var other=account();assertThat(read(a,"/daily/reminders").path("enabled").asBoolean()).isFalse();
        var saved=put(a,"/daily/reminders",preferences(true,null),200);assertThat(saved.path("enabled").asBoolean()).isTrue();
        put(a,"/daily/reminders",preferences(false,null),409);
        put(a,"/daily/reminders",preferences(false,saved.path("version").asLong()),200);
        assertThat(read(other,"/daily/reminders").path("version").isNull()).isTrue();
        var invalid=preferences(true,saved.path("version").asLong());invalid.put("includePlans",false);invalid.put("includeTodos",false);put(a,"/daily/reminders",invalid,400);
        invalid=preferences(true,null);invalid.put("lookAheadDays",15);put(other,"/daily/reminders",invalid,400);
    }
    @Test void weeklyNotesArePrivateVersionedAndReplayableWithoutBusinessWrites() throws Exception {
        var a=account();var other=account();String path="/weekly/reviews/2026-10-12";
        assertThat(read(a,path).path("version").isNull()).isTrue();
        var draft=Map.of("wins","完成了一个步骤","blockers","时间不足","nextFocus","每天少量练习");
        var saved=put(a,path,draft,200);assertThat(saved.path("version").asLong()).isZero();
        put(a,path,draft,409);assertThat(read(a,path).path("wins").asText()).isEqualTo("完成了一个步骤");
        assertThat(read(other,path).path("wins").asText()).isEmpty();assertThat(read(a,"/weekly/reviews").path("totalElements").asInt()).isEqualTo(1);
        var updated=new HashMap<String,Object>(draft);updated.put("version",saved.path("version").asLong());updated.put("nextFocus","调整为周末练习");put(a,path,updated,200);
        String key=UUID.randomUUID().toString();String body=mapper.writeValueAsString(draft);
        for(int i=0;i<2;i++)mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/weekly/reviews/2026-10-19").header("Authorization","Bearer "+a.token()).header("X-Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from plans where owner_id=?",Long.class,a.id())).isZero();
    }
    @Test void weeklyMetricsUseShanghaiCompletionDatesAndKeepLegacyUnknownsHonest() throws Exception {
        var a=account();var old=todo(a,"旧完成事项","2026-10-13");var recent=todo(a,"新完成事项","2026-10-13");
        jdbc.update("update todos set done=true,completed_at=null where id=?",old.path("id").asText());
        // Sunday UTC, but already Monday in Shanghai.
        jdbc.update("update todos set done=true,completed_at=? where id=?",java.sql.Timestamp.from(Instant.parse("2026-10-11T16:00:00Z")),recent.path("id").asText());
        var summary=read(a,"/weekly/summary?start=2026-10-12");assertThat(summary.path("todoCompleted").asInt()).isEqualTo(1);assertThat(summary.path("legacyCompletedTodosUntimed").asInt()).isEqualTo(1);
        assertThat(read(a,"/weekly/summary?start=2026-10-05").path("todoCompleted").asInt()).isZero();
        // Undo/recomplete tracks the new completion, but editing a completed todo does not invent a date.
        put(a,"/todos/"+old.path("id").asText(),Map.of("title","编辑旧事项","dueDate","2026-10-13","done",true,"deleted",false,"version",old.path("version").asLong()),200);
        assertThat(read(a,"/weekly/summary?start=2026-10-12").path("legacyCompletedTodosUntimed").asInt()).isEqualTo(1);
    }
    @Test void rejectsUnboundedOrInvalidRequestsAndAnonymousReads() throws Exception {
        var a=account();for(String path:List.of("/daily/items?date=2026-10-18&size=51","/daily/items?date=2026-10-18&mode=ROUTE","/daily/items?date=2026-10-18&bucket=BAD","/daily/summary?date=2026-10-18&days=15","/weekly/summary?start=2026-10-18"))mvc.perform(get("/api"+path).header("Authorization","Bearer "+a.token())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/daily/summary?date=2026-10-18")).andExpect(status().isUnauthorized());
    }
    @Test void weeklyMetricsIncludeRecordedTimeCorrectionsAndRouteCompletionsWithoutMixingModes() throws Exception {
        var a=account();String plan=plan(a).path("id").asText();var task=task(a,plan,"2026-10-13","本周阅读");String id=task.path("id").asText();
        var progress=post(a,"/tasks/"+id+"/manual-time",Map.of("seconds",300,"version",task.path("progress").path("version").asLong()),200);
        progress=post(a,"/tasks/"+id+"/manual-time",Map.of("seconds",-60,"version",progress.path("version").asLong()),200);
        post(a,"/tasks/"+id+"/complete",Map.of("note","完成","acceptanceChecks",List.of(),"version",progress.path("version").asLong()),200);
        var at=java.sql.Timestamp.from(Instant.parse("2026-10-13T02:00:00Z"));
        jdbc.update("update task_progress set completed_at=? where task_id=?",at,id);
        jdbc.update("update time_entries set started_at=?,ended_at=? where progress_id=(select id from task_progress where task_id=?)",at,at,id);
        var line=post(a,"/mainlines",Map.of("name","无期限路线","color","#002FA7","executionMode","FLEXIBLE"),201);
        var item=post(a,"/mainlines/"+line.path("id").asText()+"/items",Map.of("title","学习琴弦","plannedMinutes",30,"sortOrder",0,"acceptance",List.of()),201);
        post(a,"/mainlines/items/"+item.path("id").asText()+"/complete",Map.of("note","完成","acceptanceChecks",List.of(),"version",item.path("version").asLong()),200);
        jdbc.update("update mainline_items set completed_at=? where id=?",at,item.path("id").asText());
        var result=read(a,"/weekly/summary?start=2026-10-12");
        assertThat(result.path("planScheduled").asInt()).isEqualTo(1);assertThat(result.path("planCompleted").asInt()).isEqualTo(1);
        assertThat(result.path("recordedPlanSeconds").asLong()).isEqualTo(240);assertThat(result.path("routeCompleted").asInt()).isEqualTo(1);assertThat(result.path("todoCompleted").asInt()).isZero();
        assertThat(read(account(),"/weekly/summary?start=2026-10-12").path("recordedPlanSeconds").asLong()).isZero();
    }
}
