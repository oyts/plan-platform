package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.registration-enabled=true") @AutoConfigureMockMvc
@Import(NativeReminderIntegrationTest.TimeConfig.class)
class NativeReminderIntegrationTest {
    static class Time extends Clock {Instant value;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return value;}}
    @TestConfiguration static class TimeConfig {@Bean Time reminderClock(){return new Time();}}
    @Autowired Time time;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;
    @BeforeEach void time(){time.value=Instant.parse("2026-10-20T01:25:00Z");}
    record Account(String id,String token){}
    private JsonNode call(Account a,String method,String path,Object body,int status)throws Exception{
        var r=switch(method){case"POST"->post("/api"+path);case"PUT"->put("/api"+path);case"DELETE"->delete("/api"+path);default->get("/api"+path);};
        if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String out=mvc.perform(r).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();return out.isBlank()?json.nullNode():json.readTree(out);
    }
    private Account account()throws Exception{var r=call(null,"POST","/auth/register",Map.of("email","reminder-"+UUID.randomUUID()+"@example.test","password","ReminderTest123!","displayName","合成提醒验收"),201);return new Account(r.path("user").path("id").asText(),r.path("accessToken").asText());}
    private ObjectNode prefs(Account a)throws Exception{return (ObjectNode)call(a,"GET","/reminders/preferences",null,200);}
    private JsonNode device(Account a)throws Exception{return call(a,"POST","/reminders/devices",Map.of("platform","ANDROID","label","合成设备"),200);}
    private JsonNode poll(String token,int status)throws Exception{return json.readTree(mvc.perform(post("/api/reminders/poll").header("X-Reminder-Key",token)).andExpect(status().is(status)).andReturn().getResponse().getContentAsString());}
    private void enable(Account a,boolean advance,boolean start,boolean todo)throws Exception{var p=prefs(a);p.put("advanceEnabled",advance);p.put("startEnabled",start);p.put("todoEnabled",todo);call(a,"PUT","/reminders/preferences",p,200);}
    private JsonNode planTask(Account a,String title)throws Exception{var p=call(a,"POST","/plans",Map.of("name","合成日程","startDate","2026-10-19","endDate","2026-10-25","restDays",List.of(2)),201);return call(a,"POST","/plans/"+p.path("id").asText()+"/tasks",Map.of("date","2026-10-20","startTime","09:30","endTime","10:00","plannedMinutes",30,"title",title,"category","STUDY","required",true,"sortOrder",0,"acceptance",List.of()),201);}
    @Test void defaultOffIndependentRulesNoStoreAndConflict()throws Exception{
        var a=account();var p=prefs(a);assertThat(p.path("advanceEnabled").asBoolean()).isFalse();assertThat(p.path("advanceMinutes").asInt()).isEqualTo(5);assertThat(p.path("todoTime").asText()).isEqualTo("22:30");assertThat(p.path("hideDetails").asBoolean()).isTrue();
        mvc.perform(get("/api/reminders/preferences").header("Authorization","Bearer "+a.token())).andExpect(header().string("Cache-Control","no-store"));
        var saved=call(a,"PUT","/reminders/preferences",p,200);assertThat(saved.path("version").asInt()).isZero();call(a,"PUT","/reminders/preferences",p,409);
        var invalid=(ObjectNode)saved.deepCopy();invalid.put("advanceMinutes",0);call(a,"PUT","/reminders/preferences",invalid,400);
        assertThat(poll(device(a).path("token").asText(),200).path("notices")).isEmpty();
    }
    @Test void advanceAndStartAreSeparateFreshAndDeduplicated()throws Exception{
        var a=account();var task=planTask(a,"绝不出现在默认通知中的合成标题");enable(a,true,true,false);String key=device(a).path("token").asText();
        var before=poll(key,200);assertThat(before.path("notices").size()).isEqualTo(1);assertThat(before.toString()).doesNotContain(task.path("title").asText());
        var repeat=poll(key,200);assertThat(repeat.path("notices")).isEmpty();assertThat(repeat.path("activeKeys").size()).isEqualTo(1);time.value=time.value.plusSeconds(300);
        assertThat(poll(key,200).path("notices").get(0).path("title").asText()).contains("是否开始");assertThat(poll(key,200).path("notices")).isEmpty();
    }
    @Test void pausedEvenZeroDurationAndResetPendingNeverBecomeUnstarted()throws Exception{
        var a=account();var t=planTask(a,"暂停任务");enable(a,true,true,false);String token=device(a).path("token").asText();
        db.update("update task_progress set ever_started=true,status='PENDING',started_at=null,accumulated_seconds=0 where task_id=?",t.path("id").asText());
        assertThat(poll(token,200).path("notices")).isEmpty();time.value=time.value.plusSeconds(300);assertThat(poll(token,200).path("notices")).isEmpty();
    }
    @Test void completionArchiveDeletionAndDeferralSuppressOriginalTime()throws Exception{
        var a=account();var t=planTask(a,"已完成");enable(a,true,true,false);String token=device(a).path("token").asText();
        call(a,"POST","/tasks/"+t.path("id").asText()+"/complete",Map.of("note","","acceptanceChecks",List.of(),"version",t.path("progress").path("version").asLong()),200);
        assertThat(poll(token,200).path("notices")).isEmpty();
        var deferred=planTask(a,"改期");db.update("update task_progress set defer_to='2026-10-21',status='DEFERRED' where task_id=?",deferred.path("id").asText());assertThat(poll(token,200).path("notices")).isEmpty();
        var archived=planTask(a,"归档");db.update("update plans set archived=true where id=?",archived.path("planId").asText());
        db.update("update plan_tasks set deleted_at=? where id=?",java.sql.Timestamp.from(time.value),archived.path("id").asText());assertThat(poll(token,200).path("notices")).isEmpty();
    }
    @Test void todoOnlyForSameDateOpenItemsAndNotEmptyDays()throws Exception{
        var a=account();enable(a,false,false,true);String token=device(a).path("token").asText();time.value=Instant.parse("2026-10-20T14:30:00Z");
        assertThat(poll(token,200).path("notices")).isEmpty();
        call(a,"POST","/todos",Map.of("title","昨日事项","dueDate","2026-10-19"),201);assertThat(poll(token,200).path("notices")).isEmpty();
        var t=call(a,"POST","/todos",Map.of("title","今日事项","dueDate","2026-10-20"),201);assertThat(poll(token,200).path("notices").size()).isEqualTo(1);
        String other=device(a).path("token").asText();db.update("update todos set done=true where id=?",t.path("id").asText());assertThat(poll(other,200).path("notices")).isEmpty();
    }
    @Test void quietHoursAndOldWakesDoNotBurstAfterward()throws Exception{
        var a=account();planTask(a,"静音");enable(a,true,true,false);var p=prefs(a);p.put("quietEnabled",true);p.put("quietFrom","09:00");p.put("quietUntil","10:00");call(a,"PUT","/reminders/preferences",p,200);
        String token=device(a).path("token").asText();assertThat(poll(token,200).path("notices")).isEmpty();time.value=Instant.parse("2026-10-20T02:00:00Z");assertThat(poll(token,200).path("notices")).isEmpty();
    }
    @Test void ownershipPrimaryDeviceAndRevocationEnforced()throws Exception{
        var a=account();var b=account();planTask(a,"本人");enable(a,true,true,false);var da=device(a);var dbb=device(b);var p=prefs(a);p.put("primaryDeviceId",dbb.path("id").asText());call(a,"PUT","/reminders/preferences",p,404);
        p.put("primaryDeviceId",da.path("id").asText());call(a,"PUT","/reminders/preferences",p,200);String second=device(a).path("token").asText();assertThat(poll(second,200).path("notices")).isEmpty();assertThat(poll(da.path("token").asText(),200).path("notices").size()).isEqualTo(1);
        call(b,"DELETE","/reminders/devices/"+da.path("id").asText(),null,404);call(a,"DELETE","/reminders/devices/"+da.path("id").asText(),null,204);poll(da.path("token").asText(),401);
    }
    @Test void passwordGenerationAndLogoutAllInvalidateBackgroundAuthorization()throws Exception{
        var a=account();String token=device(a).path("token").asText();db.update("update users set auth_generation=auth_generation+1 where id=?",a.id());poll(token,401);
        var b=account();String btoken=device(b).path("token").asText();call(b,"POST","/auth/logout-all",null,204);poll(btoken,401);
        var c=account();String ctoken=device(c).path("token").asText();db.update("update users set password_hash=? where id=?","changed-synthetic-password-hash",c.id());poll(ctoken,401);
    }
    @Test void noPlainCredentialInTablesOrGenericReplayCache()throws Exception{
        var a=account();var response=mvc.perform(post("/api/reminders/devices").header("Authorization","Bearer "+a.token()).header("X-Idempotency-Key","synthetic-reminder-key-"+UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{\"platform\":\"WINDOWS\",\"label\":\"合成设备\"}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();var d=json.readTree(response.getContentAsString());String token=d.path("token").asText();
        assertThat(db.queryForObject("select token_hash from native_reminder_devices where id=?",String.class,d.path("id").asText())).doesNotContain(token.substring(37));
        assertThat(db.queryForObject("select count(*) from idempotency_records where request_path='/api/reminders/devices'",Long.class)).isZero();
        poll("invalid",401);mvc.perform(get("/api/reminders/devices")).andExpect(status().isUnauthorized());
    }
}
