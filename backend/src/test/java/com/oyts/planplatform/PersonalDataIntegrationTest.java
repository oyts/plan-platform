package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oyts.planplatform.common.CsvCell;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.security.registration-enabled=true") @AutoConfigureMockMvc
class PersonalDataIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    record Account(String token,String id){}
    private Account account()throws Exception{var row=send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/register"),null,Map.of("email","data-"+UUID.randomUUID()+"@example.test","password","PrivateDataTest123!","displayName","数据中心验收"),201);return new Account(row.path("accessToken").asText(),row.path("user").path("id").asText());}
    private JsonNode send(MockHttpServletRequestBuilder request,Account a,Object body,int code)throws Exception{
        if(a!=null)request.header("Authorization","Bearer "+a.token());if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(request).andExpect(status().is(code)).andReturn().getResponse();return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());
    }
    private JsonNode post(Account a,String path,Object body,int code)throws Exception{return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api"+path),a,body,code);}
    private JsonNode put(Account a,String path,Object body)throws Exception{return send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api"+path),a,body,200);}
    private JsonNode read(Account a,String path)throws Exception{return send(get("/api"+path),a,null,200);}
    private JsonNode backup(Account a)throws Exception{return read(a,"/data/backup");}
    private JsonNode preview(Account a,JsonNode file)throws Exception{return post(a,"/data/preview",file,200);}
    private Map<String,Object> restoreBody(JsonNode file,String checksum,String key){return Map.of("backup",file,"checksum",checksum,"operationKey",key,"confirmed",true);}
    private JsonNode restore(Account a,JsonNode file)throws Exception{return post(a,"/data/restore",restoreBody(file,preview(a,file).path("checksum").asText(),UUID.randomUUID().toString()),200);}
    private JsonNode fixture(Account a)throws Exception{
        var plan=post(a,"/plans",Map.of("name","合成备份周期","description","中文说明","startDate","2026-10-01","endDate","2026-11-30","restDays",List.of(7)),201);String planId=plan.path("id").asText();
        var template=post(a,"/templates",Map.of("name","可恢复模板","category","STUDY","title","模板任务","description","说明","plannedMinutes",20,"acceptance",List.of("标准"),"resourceUrl","https://example.test/lesson","required",true),201);
        var task=post(a,"/plans/"+planId+"/tasks",Map.of("title","完成的合成任务","date","2026-10-18","plannedMinutes",20,"category","STUDY","acceptance",List.of("标准"),"required",true,"sortOrder",0),201);
        String taskId=task.path("id").asText();jdbc.update("update plan_tasks set template_id=?,recurrence_id=? where id=?",template.path("id").asText(),UUID.randomUUID().toString(),taskId);
        var timed=post(a,"/tasks/"+taskId+"/manual-time",Map.of("seconds",120,"version",0),200);
        post(a,"/tasks/"+taskId+"/complete",Map.of("note","已完成说明","acceptanceChecks",List.of(true),"version",timed.path("version").asLong()),200);
        var running=post(a,"/plans/"+planId+"/tasks",Map.of("title","运行中任务","date","2026-10-18","plannedMinutes",20,"category","STUDY","acceptance",List.of(),"required",true,"sortOrder",1),201);
        post(a,"/tasks/"+running.path("id").asText()+"/timer/start",Map.of("version",0),200);
        post(a,"/plans/"+planId+"/milestones",Map.of("name","合成里程碑","date","2026-10-25","description","验收","acceptance",List.of("标准")),201);
        put(a,"/plans/"+planId+"/reflections/2026-10-18",Map.of("completedSummary","每日收获","unfinishedSummary","未完成","blockers","无","firstTaskTomorrow","阅读","energy",4,"note","备注"));
        var line=post(a,"/mainlines",Map.of("name","合成吉他路线","description","长期学习","color","#002FA7","executionMode","SEQUENTIAL"),201);String lineId=line.path("id").asText();
        var stage=post(a,"/mainlines/"+lineId+"/stages",Map.of("name","基础阶段","description","阶段说明","sortOrder",0),201);
        var first=post(a,"/mainlines/"+lineId+"/items",Map.of("title","认识乐理","description","说明","acceptance",List.of("标准"),"plannedMinutes",5000,"sortOrder",0,"routing",Map.of("stageId",stage.path("id").asText(),"dependencyMode","NONE","prerequisiteIds",List.of())),201);
        post(a,"/mainlines/"+lineId+"/items",Map.of("title","和弦","description","说明","acceptance",List.of(),"plannedMinutes",20,"sortOrder",1,"routing",Map.of("stageId",stage.path("id").asText(),"dependencyMode","EXPLICIT","prerequisiteIds",List.of(first.path("id").asText()))),201);
        post(a,"/mainlines/items/"+first.path("id").asText()+"/complete",Map.of("note","路线完成说明","version",first.path("version").asLong()),200);
        post(a,"/todos",Map.of("title","合成待办","note","待办备注","dueDate","2026-10-18"),201);
        put(a,"/weekly/reviews/2026-10-12",Map.of("wins","来源账号的周收获","blockers","时间少","nextFocus","每天少量练习"));
        return backup(a);
    }
    @Test void completeRoundTripRemapsRelationsPreservesHistoryAndNeverOverwritesWeeklyReview()throws Exception{
        var a=account();var b=account();var file=fixture(a);
        put(b,"/weekly/reviews/2026-10-12",Map.of("wins","目标账号原复盘","blockers","","nextFocus",""));
        post(b,"/todos",Map.of("title","目标账号原待办","dueDate","2026-10-18"),201);
        var result=restore(b,file);String planId=result.path("planIds").get(0).asText(),lineId=result.path("routeIds").get(0).asText();
        assertThat(planId).isNotEqualTo(file.path("records").path("plans").get(0).path("id").asText());
        var plans=read(b,"/plans");assertThat(plans.get(0).path("restDays").toString()).isEqualTo("[7]");
        assertThat(jdbc.queryForObject("select count(*) from task_progress g join plan_tasks t on t.id=g.task_id where t.plan_id=? and g.status='RUNNING'",Long.class,planId)).isZero();
        assertThat(jdbc.queryForObject("select accumulated_seconds from task_progress g join plan_tasks t on t.id=g.task_id where t.plan_id=? and t.title='完成的合成任务'",Long.class,planId)).isEqualTo(120);
        assertThat(jdbc.queryForObject("select count(*) from time_entries e join task_progress g on g.id=e.progress_id join plan_tasks t on t.id=g.task_id where t.plan_id=?",Long.class,planId)).isEqualTo(1);
        var restored=backup(b);var items=restored.path("records").path("mainline_items");JsonNode dependent=null,first=null;for(var item:items){if(item.path("title").asText().equals("和弦"))dependent=item;else first=item;}
        assertThat(dependent.path("prerequisite_ids_json").get(0).asText()).isEqualTo(first.path("id").asText());assertThat(first.path("mainline_id").asText()).isEqualTo(lineId);assertThat(first.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(restored.path("records").path("weekly_reviews").size()).isEqualTo(2);
        String originalCompleted=file.path("records").path("task_progress").findValues("completed_at").stream().filter(JsonNode::isTextual).findFirst().orElseThrow().asText();
        assertThat(restored.path("records").path("task_progress").findValues("completed_at").stream().filter(JsonNode::isTextual).findFirst().orElseThrow().asText()).isEqualTo(originalCompleted);
        assertThat(restored.path("records").path("time_entries").get(0).path("created_at")).isEqualTo(file.path("records").path("time_entries").get(0).path("created_at"));
        String restoredTemplate=restored.path("records").path("task_templates").get(0).path("id").asText();
        assertThat(restored.path("records").path("task_templates").get(0).path("restored_original_name").asText()).isEqualTo("可恢复模板");
        assertThat(jdbc.queryForObject("select template_id from plan_tasks where plan_id=? and title='完成的合成任务'",String.class,planId)).isEqualTo(restoredTemplate);
        assertThat(read(b,"/weekly/reviews/2026-10-12").path("wins").asText()).isEqualTo("目标账号原复盘");
        assertThat(read(b,"/data/restored-weekly-reviews").path("content").get(0).path("wins").asText()).isEqualTo("来源账号的周收获");
        assertThat(jdbc.queryForObject("select count(*) from todos where owner_id=?",Long.class,b.id())).isEqualTo(2);
        assertThat(backup(a).path("records")).isEqualTo(file.path("records"));
    }
    @Test void backupV2PreservesEverStartedAndLegacyV1StillRestoresWithSameChecksum()throws Exception{
        var a=account();var b=account();var file=fixture(a);assertThat(file.path("schemaVersion").asInt()).isEqualTo(3);
        var progress=file.path("records").path("task_progress");((ObjectNode)progress.get(0)).put("status","PENDING");((ObjectNode)progress.get(0)).put("accumulated_seconds",0);((ObjectNode)progress.get(0)).put("ever_started",true);
        var restored=restore(b,file);String plan=restored.path("planIds").get(0).asText();assertThat(jdbc.queryForObject("select count(*) from task_progress g join plan_tasks t on t.id=g.task_id where t.plan_id=? and g.ever_started=true",Long.class,plan)).isEqualTo(2);
        ObjectNode old=(ObjectNode)file.deepCopy();old.put("schemaVersion",1);((ObjectNode)old.path("records")).remove("daily_reviews");old.path("records").path("weekly_reviews").forEach(row->((ObjectNode)row).remove(List.of("energy","burden","reviewed","open_to_increase")));old.path("records").path("task_progress").forEach(row->((ObjectNode)row).remove("ever_started"));
        String checksum=preview(b,old).path("checksum").asText();var first=restore(b,old);var again=restore(b,old);assertThat(again.path("batchId")).isEqualTo(first.path("batchId"));assertThat(preview(b,old).path("checksum").asText()).isEqualTo(checksum);
    }
    @Test void previewIsReadOnlyAndOwnershipIsNeverExported()throws Exception{
        var a=account();var b=account();post(a,"/todos",Map.of("title","合成私有记录","dueDate","2026-10-18"),201);post(b,"/todos",Map.of("title","别人的私有记录","dueDate","2026-10-18"),201);
        var file=backup(a);var result=preview(a,file);assertThat(result.path("totalRecords").asInt()).isEqualTo(1);
        assertThat(file.toString()).doesNotContain("owner_id","password","accessToken","别人的私有记录");
        assertThat(read(a,"/data/restores").path("totalElements").asInt()).isZero();
        mvc.perform(get("/api/data/backup").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        send(get("/api/data/backup"),null,null,401);
    }
    @Test void repeatedAndConcurrentRestoresHaveOneBatchAndOneSetOfNewRows()throws Exception{
        var a=account();var b=account();post(a,"/todos",Map.of("title","防重测试","dueDate","2026-10-18"),201);var file=backup(a);String checksum=preview(b,file).path("checksum").asText(),key=UUID.randomUUID().toString();
        try(var pool=Executors.newFixedThreadPool(3)){var futures=new ArrayList<Future<JsonNode>>();for(int i=0;i<3;i++)futures.add(pool.submit(()->post(b,"/data/restore",restoreBody(file,checksum,key),200)));String first=futures.getFirst().get().path("batchId").asText();for(var f:futures)assertThat(f.get().path("batchId").asText()).isEqualTo(first);}
        post(b,"/data/restore",restoreBody(file,checksum,UUID.randomUUID().toString()),200);
        assertThat(read(b,"/data/restores").path("totalElements").asInt()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from todos where owner_id=?",Long.class,b.id())).isEqualTo(1);
        assertThat(read(a,"/data/restores").path("totalElements").asInt()).isZero();
    }
    @Test void alteredFilesForeignReferencesCyclesAndUnknownFieldsAreRejected()throws Exception{
        var a=account();var file=fixture(a);var modified=(ObjectNode)file.deepCopy();
        ((ObjectNode)modified.path("records").path("plans").get(0)).put("owner_id",UUID.randomUUID().toString());post(a,"/data/preview",modified,400);
        modified=(ObjectNode)file.deepCopy();((ObjectNode)modified.path("records").path("plan_tasks").get(0)).put("plan_id",UUID.randomUUID().toString());post(a,"/data/preview",modified,400);
        modified=(ObjectNode)file.deepCopy();var items=modified.path("records").path("mainline_items");var x=(ObjectNode)items.get(0);var y=(ObjectNode)items.get(1);x.put("dependency_mode","EXPLICIT");x.putArray("prerequisite_ids_json").add(y.path("id").asText());y.put("dependency_mode","EXPLICIT");y.putArray("prerequisite_ids_json").add(x.path("id").asText());post(a,"/data/preview",modified,400);
        modified=(ObjectNode)file.deepCopy();modified.put("schemaVersion",4294967297L);post(a,"/data/preview",modified,400);
        var checksum=preview(a,file).path("checksum").asText();modified=(ObjectNode)file.deepCopy();((ObjectNode)modified.path("records").path("todos").get(0)).put("title","已更改");post(a,"/data/restore",restoreBody(modified,checksum,UUID.randomUUID().toString()),409);
        assertThat(read(a,"/data/restores").path("totalElements").asInt()).isZero();
    }
    @Test void databaseFailureRollsBackEarlierParentsAndBatchMetadata()throws Exception{
        var a=account();var b=account();var file=fixture(a);((ObjectNode)file.path("records").path("todos").get(0)).put("title","ROLLBACK_SYNTHETIC_ONLY");
        String checksum=preview(b,file).path("checksum").asText();
        jdbc.execute("alter table todos add constraint data_restore_synthetic_failure check(title <> 'ROLLBACK_SYNTHETIC_ONLY')");
        try{post(b,"/data/restore",restoreBody(file,checksum,UUID.randomUUID().toString()),409);}finally{jdbc.execute("alter table todos drop constraint data_restore_synthetic_failure");}
        assertThat(jdbc.queryForObject("select count(*) from plans where owner_id=?",Long.class,b.id())).isZero();assertThat(jdbc.queryForObject("select count(*) from mainlines where owner_id=?",Long.class,b.id())).isZero();assertThat(read(b,"/data/restores").path("totalElements").asInt()).isZero();
    }
    @Test void duplicateJsonFieldsAndOversizedRequestsFailWithoutWrites()throws Exception{
        var a=account();mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/data/preview").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content("{\"schemaVersion\":1,\"schemaVersion\":2}")).andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/data/preview").header("Authorization","Bearer "+a.token()).contentType(MediaType.APPLICATION_JSON).content(" ".repeat(5*1024*1024+4097))).andExpect(status().isBadRequest());
        assertThat(read(a,"/data/restores").path("totalElements").asInt()).isZero();
    }
    @Test void csvEscapesFormulasQuotesAndUnicodeInNewAndExistingExports()throws Exception{
        assertThat(CsvCell.encode("  =1+1")).isEqualTo("\"'  =1+1\"");assertThat(CsvCell.encode("\t@SUM(A1)犬")).startsWith("\"'");assertThat(CsvCell.encode("中文,\"说明\"\n第二行")).isEqualTo("\"中文,\"\"说明\"\"\n第二行\"");
        var a=account();post(a,"/todos",Map.of("title","=1+1","note","中文,备注","dueDate","2026-10-18"),201);
        var response=mvc.perform(get("/api/data/export/todos").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("'=1+1","中文,备注");
        var plan=post(a,"/plans",Map.of("name","导出验收","startDate","2026-10-01","endDate","2026-10-31"),201);String id=plan.path("id").asText();
        post(a,"/plans/"+id+"/tasks",Map.of("title","=1+1","date","2026-10-18","plannedMinutes",10,"category","STUDY","acceptance",List.of(),"required",true,"sortOrder",0),201);
        var legacy=mvc.perform(get("/api/plans/"+id+"/tasks/export?from=2026-10-01&to=2026-10-31").header("Authorization","Bearer "+a.token())).andExpect(status().isOk()).andReturn().getResponse();
        assertThat(legacy.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("'=1+1");
    }
    @Test void databaseTimestampRangeAndPrecisionAreCheckedBeforeRestore()throws Exception{
        var a=account();post(a,"/todos",Map.of("title","时间验证","dueDate","2026-10-18"),201);var file=(ObjectNode)backup(a);var row=(ObjectNode)file.path("records").path("todos").get(0);
        row.put("created_at","2040-01-01T00:00:00Z");post(a,"/data/preview",file,400);
        row.put("created_at","2026-10-18T00:00:00.123456789Z");post(a,"/data/preview",file,400);
    }
    @Test void oversizedExportStopsDuringReadingInsteadOfAccumulatingAnUnboundedFile()throws Exception{
        var a=account();var rows=new ArrayList<Object[]>();var now=java.sql.Timestamp.from(java.time.Instant.now());
        for(int i=0;i<900;i++)rows.add(new Object[]{UUID.randomUUID().toString(),a.id(),"合成容量 "+i,"文".repeat(2000),java.sql.Date.valueOf("2026-10-18"),now,now});
        jdbc.batchUpdate("insert into todos(id,owner_id,title,note,due_date,done,deleted,version,created_at,updated_at) values(?,?,?,?,?,false,false,0,?,?)",rows);
        send(get("/api/data/backup"),a,null,400);send(get("/api/data/export/todos"),a,null,400);
        assertThat(jdbc.queryForObject("select count(*) from todos where owner_id=?",Long.class,a.id())).isEqualTo(900);
    }
}
