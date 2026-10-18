package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import java.util.*;
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
class OfflineSnapshotIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    record Account(String token,String id){}
    private JsonNode send(MockHttpServletRequestBuilder r,Account a,Object body,int code)throws Exception{if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));var response=mvc.perform(r).andExpect(status().is(code)).andReturn().getResponse();return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());}
    private Account account()throws Exception{var row=send(post("/api/auth/register"),null,Map.of("email","offline-"+UUID.randomUUID()+"@example.test","password","OfflineSnapshot123!","displayName","离线合成验收"),201);return new Account(row.path("accessToken").asText(),row.path("user").path("id").asText());}
    private JsonNode create(Account a,String path,Object body)throws Exception{return send(post("/api"+path),a,body,201);}
    private JsonNode snapshot(Account a,String path)throws Exception{return send(get("/api/sync/record").param("path",path),a,null,200);}
    private JsonNode plan(Account a)throws Exception{return create(a,"/plans",Map.of("name","缓存周期","startDate","2026-10-01","endDate","2026-11-30","restDays",List.of(7)));}
    private Map<String,Object> task(){return Map.of("title","云端原标题","date","2026-10-18","plannedMinutes",20,"category","STUDY","acceptance",List.of("验收"),"required",true,"sortOrder",0);}
    @Test void selectedSnapshotsAreReadOnlyPrivateAndExcludeExecutionAndAccountFields()throws Exception{
        var a=account();var b=account();String p=plan(a).path("id").asText();var t=create(a,"/plans/"+p+"/tasks",task());String path="/tasks/"+t.path("id").asText();
        var value=snapshot(a,path);assertThat(value.path("fields").path("title").asText()).isEqualTo("云端原标题");assertThat(value.path("fields").path("acceptance").toString()).isEqualTo("[\"验收\"]");assertThat(value.toString()).doesNotContain("owner","password","progress","accessToken");
        mvc.perform(get("/api/sync/record").param("path",path).header("Authorization","Bearer "+a.token())).andExpect(header().string("Cache-Control","no-store"));
        send(get("/api/sync/record").param("path",path),b,null,404);send(get("/api/sync/record").param("path",path),null,null,401);send(get("/api/sync/record").param("path","/auth/me"),a,null,400);
        assertThat(snapshot(a,"/plans/"+p).path("fields").path("restDays").toString()).isEqualTo("[7]");
        send(delete("/api/tasks/"+t.path("id").asText()),a,null,204);send(get("/api/sync/record").param("path",path),a,null,404);
    }
    @Test void freshSnapshotVersionSupportsExplicitRetryButNeverBypassesALaterConflict()throws Exception{
        var a=account();var t=create(a,"/todos",Map.of("title","共同基础","note","","dueDate","2026-10-18"));String id=t.path("id").asText();
        send(put("/api/todos/"+id),a,Map.of("title","其他设备修改","note","云端备注","dueDate","2026-10-18","done",false,"deleted",false,"version",0),200);
        var cloud=snapshot(a,"/todos/"+id);var body=mapper.convertValue(cloud.path("fields"),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});body.put("title","人工选择本机标题");body.put("version",0);send(put("/api/todos/"+id),a,body,409);
        body.put("version",cloud.path("version").asLong());var saved=send(put("/api/todos/"+id),a,body,200);assertThat(saved.path("note").asText()).isEqualTo("云端备注");send(put("/api/todos/"+id),a,body,409);
    }
    @Test void routeAndTemplateEditableSnapshotsDoNotExposeTimerOrTopologyMutations()throws Exception{
        var a=account();var line=create(a,"/mainlines",Map.of("name","目标路线","color","#002FA7","executionMode","FLEXIBLE"));String l=line.path("id").asText();var step=create(a,"/mainlines/"+l+"/items",Map.of("title","合成步骤","plannedMinutes",20,"sortOrder",0,"acceptance",List.of("练习")));
        var value=snapshot(a,"/mainlines/items/"+step.path("id").asText());assertThat(value.path("fields").size()).isEqualTo(5);assertThat(value.toString()).doesNotContain("routing","status","startedAt","accumulatedSeconds","stageId");
        var template=create(a,"/templates",Map.of("name","合成模板","title","练习","category","STUDY","plannedMinutes",20,"acceptance",List.of("标准"),"required",true));assertThat(snapshot(a,"/templates/"+template.path("id").asText()).path("fields").path("required").asBoolean()).isTrue();
    }
    @Test void carryoverQueryIsDatabaseBoundedAndDoesNotTruncateOrLeakOwners()throws Exception{
        var a=account();var b=account();String plan=plan(a).path("id").asText();String source=create(a,"/plans/"+plan+"/tasks",task()).path("id").asText();
        send(post("/api/tasks/"+source+"/defer"),a,Map.of("deferTo","2026-10-20","version",0),200);
        var response=send(get("/api/tasks/carryover").param("from","2026-10-20").param("to","2026-10-20"),a,null,200);assertThat(response.size()).isEqualTo(1);assertThat(send(get("/api/tasks/carryover").param("from","2026-10-20").param("to","2026-10-20"),b,null,200).size()).isZero();
        for(int i=0;i<500;i++){String id=UUID.randomUUID().toString();db.update("insert into plan_tasks(id,plan_id,task_date,planned_minutes,category,title,acceptance_json,version,created_at,updated_at) values (?,?,?,20,'STUDY','合成延期','[]',0,current_timestamp,current_timestamp)",id,plan,"2026-10-18");db.update("insert into task_progress(id,task_id,status,defer_to,acceptance_json,version,created_at,updated_at) values (?,?,'DEFERRED',?,'[]',0,current_timestamp,current_timestamp)",UUID.randomUUID().toString(),id,"2026-10-20");}
        send(get("/api/tasks/carryover").param("from","2026-10-20").param("to","2026-10-20"),a,null,400);
        assertThat(db.queryForObject("select count(*) from plan_tasks where plan_id=?",Long.class,plan)).isEqualTo(501);
    }
}
