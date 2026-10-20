package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.registration-enabled=true") @AutoConfigureMockMvc
@Import(NativeReminderIntegrationTest.TimeConfig.class)
class ReflectionIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired NativeReminderIntegrationTest.Time time;
 record Account(String id,String token){}
 @BeforeEach void setup(){time.value=Instant.parse("2026-10-18T14:30:00Z");}
 JsonNode call(Account a,String method,String path,Object body,int code)throws Exception{var r=switch(method){case"POST"->post("/api"+path);case"PUT"->put("/api"+path);default->org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api"+path);};if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));String out=mvc.perform(r).andExpect(status().is(code)).andReturn().getResponse().getContentAsString();return out.isEmpty()?json.nullNode():json.readTree(out);}
 Account account()throws Exception{var r=call(null,"POST","/auth/register",Map.of("email","review-"+UUID.randomUUID()+"@example.test","password","SyntheticReview123!","displayName","复盘合成验收"),201);return new Account(r.path("user").path("id").asText(),r.path("accessToken").asText());}
 JsonNode get(Account a,String path)throws Exception{return call(a,"GET",path,null,200);}
 JsonNode todo(Account a,LocalDate day)throws Exception{return call(a,"POST","/todos",Map.of("title","合成待办","dueDate",day.toString()),201);}
 Map<String,Object> notes(String text,Long version,boolean reviewed){var m=new HashMap<String,Object>();m.put("wins",text);m.put("blockers","合成阻塞");m.put("nextFocus","小步推进");m.put("energy","LOW");m.put("burden","HEAVY");m.put("reviewed",reviewed);m.put("version",version);return m;}
 @Test void emptyDayDoesNotBecomePerfectAndModesKeepSeparateMeaning()throws Exception{
  var a=account();String day=LocalDate.now().toString();var f=get(a,"/reflection/facts?kind=DAY&start="+day);assertThat(f.path("hasApplicableItems").asBoolean()).isFalse();assertThat(f.path("allApplicableComplete").asBoolean()).isFalse();assertThat(f.path("routeCompleted").asLong()).isZero();
  assertThat(call(a,"POST","/reflection/guides/claim",null,200)).isEmpty();get(a,"/reflection/items?kind=DAY&start="+day+"&mode=ROUTE&bucket=ACTUAL");call(a,"GET","/reflection/items?kind=DAY&start="+day+"&mode=ROUTE&bucket=OPEN",null,400);
 }
 @Test void actualDatesAreNotDueDatesAndUnknownLegacyTimesStayUnknown()throws Exception{
  var a=account();LocalDate day=LocalDate.of(2026,10,18);var due=todo(a,day);var prior=todo(a,day.minusDays(1));var legacy=todo(a,day.minusDays(2));
  db.update("update todos set done=true,completed_at=? where id=?",java.sql.Timestamp.from(day.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),due.path("id").asText());
  db.update("update todos set done=true,completed_at=? where id=?",java.sql.Timestamp.from(day.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()),prior.path("id").asText());db.update("update todos set done=true,completed_at=null where id=?",legacy.path("id").asText());
  var f=get(a,"/reflection/facts?kind=DAY&start="+day);assertThat(f.path("todo").path("arranged").asInt()).isEqualTo(1);assertThat(f.path("todo").path("arrangedCompleted").asInt()).isEqualTo(1);assertThat(f.path("todo").path("actuallyCompleted").asInt()).isEqualTo(1);assertThat(f.path("todo").path("completedWithoutTime").asInt()).isEqualTo(1);
  var rows=get(a,"/reflection/items?kind=DAY&start="+day+"&mode=TODO&bucket=ACTUAL");assertThat(rows.path("content").get(0).path("id").asText()).isEqualTo(prior.path("id").asText());
 }
 @Test void dailySaveConflictsOwnershipAndNoOldReflectionOverwrite()throws Exception{
  var a=account();var b=account();String day="2026-10-18";var saved=call(a,"PUT","/reflection/days/"+day,notes("自己的文字",null,true),200);assertThat(saved.path("version").asInt()).isZero();call(a,"PUT","/reflection/days/"+day,notes("冲突文字",null,true),409);
  assertThat(get(b,"/reflection/days/"+day).path("wins").asText()).isEmpty();assertThat(get(a,"/reflection/days/"+day).path("wins").asText()).isEqualTo("自己的文字");assertThat(db.queryForObject("select count(*) from daily_reflections where completed_summary='自己的文字'",Long.class)).isZero();
 }
 @Test void completionGuideIsClaimedOnceAndReviewSuppressesIt()throws Exception{
  var a=account();LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));var t=todo(a,today);assertThat(call(a,"POST","/reflection/guides/claim",null,200)).isEmpty();db.update("update todos set done=true where id=?",t.path("id").asText());assertThat(call(a,"POST","/reflection/guides/claim",null,200).size()).isEqualTo(1);db.update("update todos set done=false where id=?",t.path("id").asText());db.update("update todos set done=true where id=?",t.path("id").asText());assertThat(call(a,"POST","/reflection/guides/claim",null,200)).isEmpty();
  var b=account();var t2=todo(b,today);db.update("update todos set done=true where id=?",t2.path("id").asText());call(b,"PUT","/reflection/days/"+today,notes("已回顾",null,true),200);assertThat(call(b,"POST","/reflection/guides/claim",null,200)).isEmpty();
 }
 @Test void settingsAreIndependentDefaultTimedOffAndVersioned()throws Exception{
  var a=account();ObjectNode p=(ObjectNode)get(a,"/reflection/preferences");assertThat(p.path("dailyEnabled").asBoolean()).isFalse();assertThat(p.path("weeklyEnabled").asBoolean()).isFalse();assertThat(p.path("weekDay").asInt()).isEqualTo(7);p.put("dailyEnabled",true);p.put("dailyTime","21:10");call(a,"PUT","/reflection/preferences",p,200);call(a,"PUT","/reflection/preferences",p,409);assertThat(get(a,"/reminders/preferences").path("todoEnabled").asBoolean()).isFalse();
 }
 @Test void twoActualSavedWeeksRequiredAndDeclineStartsFourteenDayCooldown()throws Exception{
  var a=account();LocalDate last=LocalDate.now(ZoneId.of("Asia/Shanghai")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1),prior=last.minusWeeks(1);todo(a,last);todo(a,prior);
  assertThat(get(a,"/reflection/trend?start="+last).path("eligible").asBoolean()).isFalse();call(a,"PUT","/weekly/reviews/"+last,notes("第一周",null,true),200);call(a,"PUT","/weekly/reviews/"+prior,notes("上一周",null,true),200);
  var trend=get(a,"/reflection/trend?start="+last);assertThat(trend.path("eligible").asBoolean()).isTrue();assertThat(trend.path("increaseAllowed").asBoolean()).isFalse();var declined=call(a,"POST","/reflection/trend/"+last+"/decision",Map.of("decision","DECLINED"),200);assertThat(declined.path("eligible").asBoolean()).isFalse();assertThat(declined.path("lastDecision").asText()).isEqualTo("DECLINED");assertThat(Duration.between(Instant.now(),Instant.parse(declined.path("nextEligibleAt").asText())).toDays()).isBetween(13L,14L);
 }
 @Test void todoDayWeekSameInstantMergeAndSavedReviewsAreRemoved()throws Exception{
  var a=account();LocalDate day=LocalDate.of(2026,10,18);todo(a,day);
  ObjectNode np=(ObjectNode)get(a,"/reminders/preferences");np.put("todoEnabled",true);call(a,"PUT","/reminders/preferences",np,200);ObjectNode rp=(ObjectNode)get(a,"/reflection/preferences");rp.put("dailyEnabled",true);rp.put("weeklyEnabled",true);call(a,"PUT","/reflection/preferences",rp,200);
  String token=call(a,"POST","/reminders/devices",Map.of("platform","ANDROID","label","合成设备"),200).path("token").asText();var r=json.readTree(mvc.perform(post("/api/reminders/poll").header("X-Reminder-Key",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(r.path("notices").size()).isEqualTo(1);assertThat(r.path("notices").get(0).path("path").asText()).contains("kind=WEEK");
  call(a,"PUT","/reflection/days/"+day,notes("完成日复盘",null,true),200);call(a,"PUT","/weekly/reviews/2026-10-12",notes("完成周复盘",null,true),200);
  var again=json.readTree(mvc.perform(post("/api/reminders/poll").header("X-Reminder-Key",token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(again.path("notices")).isEmpty();assertThat(again.path("activeKeys").size()).isEqualTo(1);
 }
 @Test void privateV3BackupRestoresReviewsAsReadonlyAndAcceptsV2()throws Exception{
  var a=account();var b=account();call(a,"PUT","/reflection/days/2026-10-18",notes("来源日复盘",null,true),200);call(a,"PUT","/weekly/reviews/2026-10-12",notes("来源周复盘",null,true),200);var file=(ObjectNode)get(a,"/data/backup");assertThat(file.path("schemaVersion").asInt()).isEqualTo(3);var p=call(b,"POST","/data/preview",file,200);Map<String,Object> body=Map.of("backup",file,"checksum",p.path("checksum").asText(),"operationKey",UUID.randomUUID().toString(),"confirmed",true);var restored=call(b,"POST","/data/restore",body,200);assertThat(call(b,"POST","/data/restore",body,200)).isEqualTo(restored);assertThat(get(b,"/reflection/days/2026-10-18").path("wins").asText()).isEmpty();var copies=get(b,"/data/restored-daily-reviews");assertThat(copies.path("content").get(0).path("wins").asText()).isEqualTo("来源日复盘");assertThat(copies.path("content").get(0).path("energy").asText()).isEqualTo("LOW");
  ObjectNode old=file.deepCopy();old.put("schemaVersion",2);((ObjectNode)old.path("records")).remove("daily_reviews");old.path("records").path("weekly_reviews").forEach(r->((ObjectNode)r).remove(List.of("energy","burden","reviewed","open_to_increase")));call(b,"POST","/data/preview",old,200);String csv=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/data/export/daily").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("来源日复盘","精力自述");
 }
 @Test void targetsAreOwnerScopedAndPreviewIncludesOnlySelectedStructure()throws Exception{
  var a=account();var b=account();var ta=todo(a,LocalDate.now());var tb=todo(b,LocalDate.now());String base="/reflection/analysis-preview";call(a,"POST",base,Map.of("kind","DAY","start",LocalDate.now().toString(),"includeNotes",false,"relatedMode","TODO","relatedId",tb.path("id").asText()),404);
  var p=call(a,"POST",base,Map.of("kind","DAY","start",LocalDate.now().toString(),"includeNotes",false,"relatedMode","TODO","relatedId",ta.path("id").asText()),200);assertThat(p.path("contentJson").asText()).contains("selectedPlanning").doesNotContain(tb.path("id").asText(),a.id(),a.token());assertThat(get(a,"/reflection/targets?mode=TODO").path("totalElements").asInt()).isEqualTo(1);
 }
}
