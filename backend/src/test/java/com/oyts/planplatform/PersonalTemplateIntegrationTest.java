package com.oyts.planplatform;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
class PersonalTemplateIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
 record Account(String token,String id){}
 LocalDate today=LocalDate.now(ZoneId.of("Asia/Shanghai"));
 JsonNode send(MockHttpServletRequestBuilder r,Account a,Object body,int status)throws Exception{
  if(a!=null)r.header("Authorization","Bearer "+a.token());if(body!=null)r.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
  var response=mvc.perform(r).andExpect(status().is(status)).andReturn().getResponse();return response.getContentAsString().isBlank()?mapper.nullNode():mapper.readTree(response.getContentAsString());
 }
 Account account()throws Exception{var r=send(post("/api/auth/register"),null,Map.of("email","template-"+UUID.randomUUID()+"@example.test","password","TemplateTest123!","displayName","模板合成用户"),201);return new Account(r.path("accessToken").asText(),r.path("user").path("id").asText());}
 JsonNode read(Account a,String path)throws Exception{return send(get("/api"+path),a,null,200);}
 JsonNode create(Account a,String path,Object body)throws Exception{return send(post("/api"+path),a,body,201);}
 String plan(Account a)throws Exception{return create(a,"/plans",Map.of("name","结构来源","description","保留说明","startDate",today.toString(),"endDate",today.plusDays(30).toString(),"restDays",List.of(7))).path("id").asText();}
 JsonNode task(Account a,String p,String title,int order)throws Exception{return create(a,"/plans/"+p+"/tasks",Map.of("title",title,"description","任务结构说明","date",today.plusDays(2).toString(),"startTime","09:30","endTime","10:00","plannedMinutes",30,"category","STUDY","acceptance",List.of("定义，不是已完成勾选"),"required",true,"sortOrder",order));}
 Map<String,Object> capture(String source,String id){var r=new LinkedHashMap<String,Object>();r.putAll(Map.of("source",source,"sourceId",id,"name","我的整套模板","description","方法说明","category","学习","favorite",true));r.put("targetId",null);r.put("version",null);return r;}
 JsonNode preview(Account a,Object r)throws Exception{return send(post("/api/structure-templates/capture/preview"),a,r,200);}
 Map<String,Object> confirmation(Object r,JsonNode p){return Map.of("request",r,"checksum",p.path("checksum").asText(),"operationKey",UUID.randomUUID().toString(),"confirmed",true);}
 String save(Account a,Object r)throws Exception{return send(post("/api/structure-templates/capture"),a,confirmation(r,preview(a,r)),200).path("id").asText();}
 Map<String,Object> use(String name,long version){return Map.of("name",name,"startDate",today.plusDays(10).toString(),"endDate",today.plusDays(40).toString(),"restDaysMask",0,"version",version);}
 JsonNode usePreview(Account a,String id,Object r)throws Exception{return send(post("/api/structure-templates/"+id+"/preview"),a,r,200);}
 JsonNode apply(Account a,String id,Object r)throws Exception{return send(post("/api/structure-templates/"+id+"/apply"),a,confirmation(r,usePreview(a,id,r)),200);}

 @Test void planTemplateIsDetachedAndCopiesFullStructureWithoutExecutionHistory()throws Exception{
  var a=account();String p=plan(a);var t=task(a,p,"第一步",0);String oldTask=t.path("id").asText();
  send(post("/api/tasks/"+oldTask+"/manual-time"),a,Map.of("seconds",60,"version",0),200);
  create(a,"/plans/"+p+"/milestones",Map.of("name","检查点","date",today.plusDays(4).toString(),"acceptance",List.of("结构验收")));
  JsonNode before=read(a,"/plans/"+p+"/tasks");String id=save(a,capture("PLAN",p));JsonNode template=read(a,"/structure-templates/"+id);
  String structure=template.path("blueprint").toString();assertThat(structure).doesNotContain(oldTask,p,"accumulated_seconds","note","started_at","owner_id","template_id");
  assertThat(template.path("counts").path("milestones").asInt()).isEqualTo(1);
  var r=use("第二轮",0);var pp=usePreview(a,id,r);assertThat(pp.path("blueprint").path("rows").path("plan_tasks").get(0).path("task_date").asText()).isEqualTo(today.plusDays(12).toString());
  var body=confirmation(r,pp);JsonNode result=send(post("/api/structure-templates/"+id+"/apply"),a,body,200);String target=result.path("id").asText();
  assertThat(send(post("/api/structure-templates/"+id+"/apply"),a,body,200)).isEqualTo(result);
  var copied=read(a,"/plans/"+target+"/tasks");assertThat(copied.get(0).path("title").asText()).isEqualTo("第一步");assertThat(copied.get(0).path("startTime").asText()).startsWith("09:30");
  assertThat(copied.get(0).path("progress").path("accumulatedSeconds").asInt()).isZero();assertThat(copied.get(0).path("progress").path("status").asText()).isEqualTo("PENDING");
  assertThat(read(a,"/plans/"+p+"/tasks")).isEqualTo(before);assertThat(db.queryForObject("select count(*) from time_entries e join task_progress g on e.progress_id=g.id join plan_tasks t on t.id=g.task_id where t.plan_id=?",Long.class,target)).isZero();
 }
 @Test void routeTemplatePreservesStagesAndDependenciesAfterSourceRemoval()throws Exception{
  var a=account();String p=create(a,"/mainlines",Map.of("name","吉他路线","executionMode","FLEXIBLE","color","#002FA7")).path("id").asText();
  String stage=create(a,"/mainlines/"+p+"/stages",Map.of("name","基础","description","先熟悉工具","sortOrder",0)).path("id").asText();
  String first=create(a,"/mainlines/"+p+"/items",Map.of("title","识谱","plannedMinutes",20,"sortOrder",0,"routing",Map.of("stageId",stage,"dependencyMode","NONE","prerequisiteIds",List.of()))).path("id").asText();
  create(a,"/mainlines/"+p+"/items",Map.of("title","练曲","plannedMinutes",30,"sortOrder",1,"routing",Map.of("stageId",stage,"dependencyMode","EXPLICIT","prerequisiteIds",List.of(first))));
  send(post("/api/mainlines/items/"+first+"/complete"),a,Map.of("version",0,"note","私人执行备注"),200);
  String id=save(a,capture("ROUTE",p));db.update("update mainlines set deleted_at=CURRENT_TIMESTAMP where id=?",p);
  var r=Map.of("name","新路线","version",0);String target=apply(a,id,r).path("id").asText();JsonNode copy=null;for(JsonNode l:read(a,"/mainlines"))if(l.path("id").asText().equals(target))copy=l;
  assertThat(copy).isNotNull();assertThat(copy.path("completed").asInt()).isZero();var items=copy.path("items");assertThat(items.get(1).path("prerequisiteIds").get(0)).isEqualTo(items.get(0).get("id"));assertThat(items.get(0).path("stageId").asText()).isNotEqualTo(stage);assertThat(copy.toString()).doesNotContain("私人执行备注");
 }
 @Test void ownerPrivacyMetadataVersionsAndCategoryFavoritePagination()throws Exception{
  var a=account();var b=account();String p=plan(a),id=save(a,capture("PLAN",p));
  send(get("/api/structure-templates/"+id),b,null,404);send(delete("/api/structure-templates/"+id+"?version=0"),b,null,404);send(post("/api/structure-templates/capture/preview"),b,capture("PLAN",p),404);send(get("/api/structure-templates"),null,null,401);
  assertThat(read(b,"/structure-templates").path("totalElements").asInt()).isZero();
  mvc.perform(get("/api/structure-templates/"+id).header("Authorization","Bearer "+a.token())).andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
  var edit=Map.of("name","改名","description","仍是个人方法","category","乐器","favorite",false,"version",0);
  send(put("/api/structure-templates/"+id),a,edit,200);send(put("/api/structure-templates/"+id),a,edit,409);
  assertThat(read(a,"/structure-templates?category=乐器").path("totalElements").asInt()).isEqualTo(1);assertThat(read(a,"/structure-templates?favorite=true").path("totalElements").asInt()).isZero();
  send(delete("/api/structure-templates/"+id+"?version=0"),a,null,409);send(delete("/api/structure-templates/"+id+"?version=1"),a,null,204);assertThat(read(a,"/plans").size()).isEqualTo(1);
 }
 @Test void changedCaptureOrTemplateVersionRejectsWithoutWritesAndReplacementDoesNotTouchCopies()throws Exception{
  var a=account();String p=plan(a);task(a,p,"原结构",0);var r=capture("PLAN",p);var stale=confirmation(r,preview(a,r));task(a,p,"后加结构",1);
  send(post("/api/structure-templates/capture"),a,stale,409);assertThat(read(a,"/structure-templates").path("totalElements").asInt()).isZero();
  String id=save(a,r);var use=use("副本不跟随变化",0);var old=confirmation(use,usePreview(a,id,use));String target=apply(a,id,use).path("id").asText();
  task(a,p,"第三项",2);var replacement=new LinkedHashMap<>(r);replacement.put("targetId",id);replacement.put("version",0);
  send(post("/api/structure-templates/capture"),a,confirmation(replacement,preview(a,replacement)),200);send(post("/api/structure-templates/"+id+"/apply"),a,old,409);
  assertThat(read(a,"/structure-templates/"+id).path("counts").path("items").asInt()).isEqualTo(3);assertThat(read(a,"/plans/"+target+"/tasks").size()).isEqualTo(2);
 }
 @Test void duplicateCaptureAndApplyAreSerializedAndKeysCannotChangeMeaning()throws Exception{
  var a=account();String p=plan(a);task(a,p,"并发结构",0);var r=capture("PLAN",p);var body=confirmation(r,preview(a,r));
  try(var pool=Executors.newFixedThreadPool(3)){var futures=new ArrayList<Future<JsonNode>>();for(int i=0;i<3;i++)futures.add(pool.submit(()->send(post("/api/structure-templates/capture"),a,body,200)));String id=futures.getFirst().get().path("id").asText();for(var f:futures)assertThat(f.get().path("id").asText()).isEqualTo(id);var use=use("并发新副本",0);var apply=confirmation(use,usePreview(a,id,use));futures.clear();for(int i=0;i<3;i++)futures.add(pool.submit(()->send(post("/api/structure-templates/"+id+"/apply"),a,apply,200)));String target=futures.getFirst().get().path("id").asText();for(var f:futures)assertThat(f.get().path("id").asText()).isEqualTo(target);}
  var changed=new HashMap<>(body);changed.put("checksum","0".repeat(64));send(post("/api/structure-templates/capture"),a,changed,409);
  assertThat(read(a,"/plans").size()).isEqualTo(2);assertThat(read(a,"/structure-templates").path("totalElements").asInt()).isEqualTo(1);
 }
 @Test void databaseConstraintRollsBackAllCopiedRowsAndOperationRecord()throws Exception{
  var a=account();String p=plan(a);task(a,p,"会被约束拒绝",0);String id=save(a,capture("PLAN",p));var use=use("不可留下空计划",0);var body=confirmation(use,usePreview(a,id,use));
  String constraint="template_atomic_"+UUID.randomUUID().toString().replace("-","");db.execute("alter table plan_tasks add constraint "+constraint+" check (title<>'会被约束拒绝' or plan_id='"+p+"')");
  try{send(post("/api/structure-templates/"+id+"/apply"),a,body,409);}finally{db.execute("alter table plan_tasks drop constraint "+constraint);}
  assertThat(read(a,"/plans").size()).isEqualTo(1);assertThat(db.queryForObject("select count(*) from personal_template_operations where owner_id=?",Long.class,a.id())).isEqualTo(1);
 }
 @Test void backupV4RestoresPrivateTemplateAsNewRecordAndRejectsNestedPrivateFields()throws Exception{
  var a=account();var b=account();String p=plan(a);task(a,p,"可恢复结构",0);String id=save(a,capture("PLAN",p));ObjectNode backup=(ObjectNode)read(a,"/data/backup");assertThat(backup.path("schemaVersion").asInt()).isEqualTo(4);
  JsonNode preview=send(post("/api/data/preview"),b,backup,200);var body=Map.of("backup",backup,"checksum",preview.path("checksum").asText(),"operationKey",UUID.randomUUID().toString(),"confirmed",true);
  var restored=send(post("/api/data/restore"),b,body,200);assertThat(send(post("/api/data/restore"),b,body,200)).isEqualTo(restored);
  var templates=read(b,"/structure-templates");assertThat(templates.path("totalElements").asInt()).isEqualTo(1);assertThat(templates.path("content").get(0).path("id").asText()).isNotEqualTo(id);
  ObjectNode old=backup.deepCopy();old.put("schemaVersion",3);((ObjectNode)old.path("records")).remove("personal_templates");send(post("/api/data/preview"),b,old,200);
  ObjectNode template=(ObjectNode)backup.path("records").path("personal_templates").get(0);ObjectNode nested=(ObjectNode)mapper.readTree(template.path("blueprint_json").asText());((ObjectNode)nested.path("parent")).put("owner_id",a.id());template.put("blueprint_json",nested.toString());send(post("/api/data/preview"),b,backup,400);
 }
 @Test void onlyCompleteNewPlanningDraftsBecomeTemplatesWithoutConversations()throws Exception{
  var a=account();String draftId=UUID.randomUUID().toString();var draft=Map.of("schemaVersion",1,"mode","ROUTE","revision",0,"title","AI方法","startDate",mapper.nullNode(),"endDate",mapper.nullNode(),"restDays",List.of(),"stages",List.of(Map.of("localId","s","title","基础")),"items",List.of(Map.of("localId","i","title","练习","note","结构说明","dueDate",mapper.nullNode(),"plannedMinutes",20,"stageId","s","prerequisites",List.of())));
  var content=new LinkedHashMap<String,Object>(Map.of("schemaVersion",1,"mode","ROUTE","message","不可收进模板的原对话","importJson","","draft",draft));content.put("sourceSessionId",null);content.put("sourceVersion",null);
  send(put("/api/ai/saved-drafts/"+draftId),a,Map.of("name","已保存的新建草稿","version",-1,"contentJson",mapper.writeValueAsString(content)),200);
  String id=save(a,capture("AI_DRAFT",draftId));assertThat(read(a,"/structure-templates/"+id).toString()).doesNotContain("不可收进模板的原对话",draftId,"sourceSessionId");
  assertThat(apply(a,id,Map.of("name","AI结构新副本","version",0)).path("count").asInt()).isEqualTo(1);
  db.update("update ai_saved_drafts set adjustment_json='UNAVAILABLE_SOURCE' where id=?",draftId);send(post("/api/structure-templates/capture/preview"),a,capture("AI_DRAFT",draftId),400);
 }
}
