package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import java.time.*;
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
class OrchestrationIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    record Account(String token,String id){}
    LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
    private JsonNode send(MockHttpServletRequestBuilder r,Account a,Object body,int code)throws Exception{
        if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var response=mvc.perform(r).andExpect(status().is(code)).andReturn().getResponse();return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());
    }
    private Account account()throws Exception{var row=send(post("/api/auth/register"),null,Map.of("email","organize-"+UUID.randomUUID()+"@example.test","password","OrganizeTest123!","displayName","编排合成验收"),201);return new Account(row.path("accessToken").asText(),row.path("user").path("id").asText());}
    private JsonNode create(Account a,String path,Object body)throws Exception{return send(post("/api"+path),a,body,201);}
    private JsonNode read(Account a,String path)throws Exception{return send(get("/api"+path),a,null,200);}
    private JsonNode preview(Account a,String kind,Object request)throws Exception{return send(post("/api/organize/"+kind+"/preview"),a,request,200);}
    private Map<String,Object> apply(Object request,JsonNode preview){return Map.of("request",request,"checksum",preview.path("checksum").asText(),"operationKey",UUID.randomUUID().toString(),"confirmed",true);}
    private JsonNode plan(Account a)throws Exception{return create(a,"/plans",Map.of("name","合成周期","startDate",today.toString(),"endDate",today.plusDays(60).toString(),"restDays",List.of(7)));}
    private Map<String,Object> task(String title,LocalDate day){return Map.of("title",title,"date",day.toString(),"plannedMinutes",20,"category","STUDY","acceptance",List.of("验收"),"required",true,"sortOrder",0);}
    private JsonNode route(Account a)throws Exception{return create(a,"/mainlines",Map.of("name","合成长期路线","color","#002FA7","executionMode","FLEXIBLE"));}
    private JsonNode step(Account a,String line,String title,int order,Map<String,Object> routing)throws Exception{Map<String,Object> body=new HashMap<>(Map.of("title",title,"plannedMinutes",15,"sortOrder",order,"acceptance",List.of("完成")));if(routing!=null)body.put("routing",routing);return create(a,"/mainlines/"+line+"/items",body);}

    @Test void planCopyRemapsStructureShiftsDatesAndDropsAllExecutionHistory()throws Exception{
        var a=account();var p=plan(a);String source=p.path("id").asText();
        var tag=create(a,"/plans/"+source+"/tags",Map.of("name","练习","color","#002FA7"));
        var payload=new HashMap<>(task("重复练习",today.plusDays(2)));payload.put("tagId",tag.path("id").asText());
        var repeated=create(a,"/plans/"+source+"/tasks/recurring",Map.of("task",payload,"mode","DAILY","until",today.plusDays(4).toString()));
        String taskId=repeated.get(0).path("id").asText();send(post("/api/tasks/"+taskId+"/manual-time"),a,Map.of("seconds",60,"version",0),200);
        create(a,"/plans/"+source+"/milestones",Map.of("name","阶段检查","date",today.plusDays(5).toString(),"acceptance",List.of("验收")));
        var request=Map.of("kind","PLAN","sourceId",source,"name","下一轮","startDate",today.plusDays(10).toString(),"endDate",today.plusDays(70).toString(),"restDaysMask",32);
        var before=read(a,"/plans/"+source+"/tasks");var report=preview(a,"copy",request);assertThat(report.path("tasks").asInt()).isEqualTo(3);
        var body=apply(request,report);var result=send(post("/api/organize/copy/apply"),a,body,200);String target=result.path("id").asText();
        var rows=read(a,"/plans/"+target+"/tasks");assertThat(rows.size()).isEqualTo(3);assertThat(rows.get(0).path("date").asText()).isEqualTo(today.plusDays(12).toString());assertThat(rows.get(0).path("tagId").asText()).isNotEqualTo(tag.path("id").asText());assertThat(rows.get(0).path("recurrenceId").asText()).isNotEqualTo(repeated.get(0).path("recurrenceId").asText());
        for(var row:rows){assertThat(row.path("progress").path("status").asText()).isEqualTo("PENDING");assertThat(row.path("progress").path("accumulatedSeconds").asInt()).isZero();}
        assertThat(db.queryForObject("select count(*) from time_entries e join task_progress g on g.id=e.progress_id join plan_tasks t on t.id=g.task_id where t.plan_id=?",Long.class,target)).isZero();
        assertThat(read(a,"/plans/"+source+"/tasks")).isEqualTo(before);assertThat(send(post("/api/organize/copy/apply"),a,body,200)).isEqualTo(result);
        assertThat(db.queryForObject("select rest_days_mask from plans where id=?",Integer.class,target)).isEqualTo(32);
    }
    @Test void routeCopyRemapsStagesAndExplicitDependenciesWithoutCompletionOrPause()throws Exception{
        var a=account();String line=route(a).path("id").asText();String stage=create(a,"/mainlines/"+line+"/stages",Map.of("name","基础","sortOrder",0)).path("id").asText();
        var first=step(a,line,"先修",0,Map.of("stageId",stage,"dependencyMode","NONE","prerequisiteIds",List.of()));
        step(a,line,"后续",1,Map.of("stageId",stage,"dependencyMode","EXPLICIT","prerequisiteIds",List.of(first.path("id").asText())));
        send(post("/api/mainlines/items/"+first.path("id").asText()+"/complete"),a,Map.of("note","原执行记录","version",0),200);
        send(put("/api/mainlines/"+line+"/paused"),a,Map.of("paused",true,"version",0),200);
        var request=Map.of("kind","ROUTE","sourceId",line,"name","结构副本");String target=send(post("/api/organize/copy/apply"),a,apply(request,preview(a,"copy",request)),200).path("id").asText();
        JsonNode copy=null;for(var row:read(a,"/mainlines"))if(row.path("id").asText().equals(target))copy=row;
        assertThat(copy).isNotNull();assertThat(copy.path("paused").asBoolean()).isFalse();assertThat(copy.path("completed").asInt()).isZero();
        var steps=copy.path("items");assertThat(steps.get(1).path("prerequisiteIds").get(0).asText()).isEqualTo(steps.get(0).path("id").asText());assertThat(steps.get(0).path("stageId").asText()).isNotEqualTo(stage);assertThat(steps.get(0).path("note").asText()).isEmpty();
    }
    @Test void batchProtectsHistoryAndRejectsChangedProgressAtomically()throws Exception{
        var a=account();String plan=plan(a).path("id").asText();var tasks=create(a,"/plans/"+plan+"/tasks/recurring",Map.of("task",task("每日练习",today.plusDays(1)),"mode","DAILY","until",today.plusDays(3).toString()));String batch=tasks.get(0).path("recurrenceId").asText();
        send(post("/api/tasks/"+tasks.get(0).path("id").asText()+"/manual-time"),a,Map.of("seconds",10,"version",0),200);
        var request=Map.of("planId",plan,"recurrenceId",batch,"from",today.plusDays(1).toString(),"shiftDays",2,"plannedMinutes",35,"replaceTag",false);
        var p=preview(a,"batch",request);assertThat(p.path("changes").size()).isEqualTo(2);assertThat(p.path("protectedItems").size()).isEqualTo(1);
        var body=apply(request,p);String changed=tasks.get(1).path("id").asText();send(post("/api/tasks/"+changed+"/manual-time"),a,Map.of("seconds",5,"version",0),200);
        send(post("/api/organize/batch/apply"),a,body,409);assertThat(read(a,"/plans/"+plan+"/tasks").get(2).path("plannedMinutes").asInt()).isEqualTo(20);
        var fresh=apply(request,preview(a,"batch",request));var result=send(post("/api/organize/batch/apply"),a,fresh,200);assertThat(result.path("changed").asInt()).isEqualTo(1);assertThat(send(post("/api/organize/batch/apply"),a,fresh,200)).isEqualTo(result);
        assertThat(read(a,"/plans/"+plan+"/tasks").get(2).path("plannedMinutes").asInt()).isEqualTo(35);
    }
    @Test void databaseFailureRollsBackEveryChangedRowAndTheApplicationRecord()throws Exception{
        var a=account();String plan=plan(a).path("id").asText();var tasks=create(a,"/plans/"+plan+"/tasks/recurring",Map.of("task",task("整批事务",today.plusDays(1)),"mode","DAILY","until",today.plusDays(3).toString()));
        var request=Map.of("planId",plan,"recurrenceId",tasks.get(0).path("recurrenceId").asText(),"from",today.plusDays(1).toString(),"shiftDays",1,"plannedMinutes",45,"replaceTag",false);var body=apply(request,preview(a,"batch",request));
        String last=tasks.get(2).path("id").asText();String constraint="check_organize_"+UUID.randomUUID().toString().replace("-","");
        db.execute("alter table plan_tasks add constraint "+constraint+" check (id<>'"+last+"' or planned_minutes=20)");
        try{send(post("/api/organize/batch/apply"),a,body,409);}finally{db.execute("alter table plan_tasks drop constraint "+constraint);}
        for(var row:read(a,"/plans/"+plan+"/tasks"))assertThat(row.path("plannedMinutes").asInt()).isEqualTo(20);
        assertThat(db.queryForObject("select count(*) from orchestration_operations where owner_id=?",Long.class,a.id())).isZero();
    }
    @Test void copiesRejectForeignOwnerExpiredPreviewAndShortenedPeriod()throws Exception{
        var a=account();var b=account();String plan=plan(a).path("id").asText();create(a,"/plans/"+plan+"/tasks",task("保留任务",today.plusDays(10)));
        var request=Map.of("kind","PLAN","sourceId",plan,"name","副本","startDate",today.toString(),"endDate",today.plusDays(60).toString());
        send(post("/api/organize/copy/preview"),b,request,404);var body=apply(request,preview(a,"copy",request));create(a,"/plans/"+plan+"/tasks",task("新增结构",today.plusDays(5)));send(post("/api/organize/copy/apply"),a,body,409);
        var shortRequest=new HashMap<>(request);shortRequest.put("endDate",today.plusDays(2).toString());send(post("/api/organize/copy/preview"),a,shortRequest,400);
        assertThat(read(a,"/plans").size()).isEqualTo(1);send(get("/api/organize/routes"),null,null,401);
    }
    @Test void concurrentCopyReplayCreatesOnlyOneStructureAndChangedKeyBodyIsRejected()throws Exception{
        var a=account();String plan=plan(a).path("id").asText();var request=Map.of("kind","PLAN","sourceId",plan,"name","防重副本","startDate",today.toString(),"endDate",today.plusDays(60).toString());var body=apply(request,preview(a,"copy",request));
        try(var pool=Executors.newFixedThreadPool(3)){var futures=new ArrayList<Future<JsonNode>>();for(int i=0;i<3;i++)futures.add(pool.submit(()->send(post("/api/organize/copy/apply"),a,body,200)));String target=futures.getFirst().get().path("id").asText();for(var future:futures)assertThat(future.get().path("id").asText()).isEqualTo(target);}
        assertThat(read(a,"/plans").size()).isEqualTo(2);var changed=new HashMap<>(body);changed.put("checksum","changed");send(post("/api/organize/copy/apply"),a,changed,409);
    }
    @Test void routePauseRejectsRunningTimersAndAlsoGuardsOldClientStartAndComplete()throws Exception{
        var a=account();String line=route(a).path("id").asText();var first=step(a,line,"练习",0,null);String item=first.path("id").asText();
        send(post("/api/mainlines/items/"+item+"/start?version=0"),a,null,200);send(put("/api/mainlines/"+line+"/paused"),a,Map.of("paused",true,"version",0),409);
        send(post("/api/mainlines/items/"+item+"/pause?version=1"),a,null,200);var paused=send(put("/api/mainlines/"+line+"/paused"),a,Map.of("paused",true,"version",0),200);
        send(post("/api/mainlines/items/"+item+"/start?version=2"),a,null,409);send(post("/api/mainlines/items/"+item+"/complete"),a,Map.of("version",2),409);
        send(put("/api/mainlines/"+line+"/paused"),a,Map.of("paused",false,"version",0),409);send(put("/api/mainlines/"+line+"/paused"),a,Map.of("paused",false,"version",paused.path("version").asLong()),200);send(post("/api/mainlines/items/"+item+"/start?version=2"),a,null,200);
    }
    @Test void realServerPaginationSearchHistoryAndFullGraphDependenciesRemainPrivate()throws Exception{
        var a=account();var b=account();String line=route(a).path("id").asText();var first=step(a,line,"第一个前置",0,null);for(int i=1;i<13;i++)step(a,line,"步骤"+i,i,Map.of("dependencyMode","EXPLICIT","prerequisiteIds",List.of(first.path("id").asText())));
        var page=read(a,"/mainlines/"+line+"/steps/page?page=1&size=5");assertThat(page.path("content").size()).isEqualTo(5);assertThat(page.path("totalElements").asInt()).isEqualTo(13);assertThat(page.path("content").get(0).path("unlocked").asBoolean()).isFalse();
        send(get("/api/mainlines/"+line+"/steps/page"),b,null,404);assertThat(read(b,"/organize/routes").path("totalElements").asInt()).isZero();
        for(int i=0;i<13;i++)create(a,"/todos",Map.of("title",i==12?"100%历史":"历史待办"+i,"dueDate",today.minusDays(80).toString()));create(b,"/todos",Map.of("title","别人的历史","dueDate",today.toString()));
        String range="from="+today.minusDays(365)+"&to="+today;var history=read(a,"/todos/page?"+range+"&page=1&size=5");assertThat(history.path("content").size()).isEqualTo(5);assertThat(history.path("totalElements").asInt()).isEqualTo(13);
        assertThat(send(get("/api/todos/page").param("from",today.minusDays(365).toString()).param("to",today.toString()).param("search","100%"),a,null,200).path("totalElements").asInt()).isEqualTo(1);
    }
}
