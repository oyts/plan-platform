package com.oyts.planplatform.organize;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.ai.AiDrafts;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Transactional(readOnly=true)
class PersonalTemplateService {
    record Capture(@NotBlank @Pattern(regexp="PLAN|ROUTE|AI_DRAFT") String source,@NotNull UUID sourceId,
        @NotBlank @Size(max=120) String name,@NotNull @Size(max=1000) String description,
        @NotNull @Size(max=60) String category,boolean favorite,UUID targetId,@PositiveOrZero Long version){}
    record Save(@NotNull @Valid Capture request,@NotBlank String checksum,@NotNull UUID operationKey,@AssertTrue boolean confirmed){}
    record Edit(@NotBlank @Size(max=120) String name,@NotNull @Size(max=1000) String description,
        @NotNull @Size(max=60) String category,boolean favorite,@NotNull @PositiveOrZero Long version){}
    record Use(@NotBlank @Size(max=120) String name,LocalDate startDate,LocalDate endDate,
        @Min(0) @Max(127) Integer restDaysMask,@NotNull @PositiveOrZero Long version){}
    record Apply(@NotNull @Valid Use request,@NotBlank String checksum,@NotNull UUID operationKey,@AssertTrue boolean confirmed){}
    record Counts(int items,int stages,int tags,int milestones){}
    record Entry(String id,String kind,String name,String description,String category,boolean favorite,long version,Instant updatedAt,Counts counts,JsonNode blueprint){}
    record Preview(String checksum,String kind,Counts counts,JsonNode blueprint,List<String> warnings){}
    record Result(String id,String kind,String path,int count){}
    private final JdbcTemplate db;private final ObjectMapper json;private final Validator validator;private final OrchestrationService copies;
    PersonalTemplateService(JdbcTemplate db,ObjectMapper json,Validator validator,OrchestrationService copies){this.db=db;this.json=json;this.validator=validator;this.copies=copies;}
    private static final List<String> WARNINGS=List.of("仅保存结构；不包含完成状态、计时流水、复盘、对话原文、通知授权或本机队列。","模板和已建立的副本互不修改。日期按起点等距平移；休息日只作标记，不自动删除或移动任务。","每人最多100份模板，每份最多1000项／256KiB；完整私有备份仍有5MiB上限。");
    private void lock(String owner){if(db.queryForList("select id from users where id=? and enabled=true for update",owner).isEmpty())throw new NotFoundException("账号不存在。");}
    private String encode(Object value){return TemplateBlueprint.encode(json,value);}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode decode(String text){return TemplateBlueprint.parse(json,text);}
    private Entry row(java.sql.ResultSet r,boolean content)throws java.sql.SQLException{
        JsonNode blueprint=decode(r.getString("blueprint_json"));String kind=r.getString("kind");TemplateBlueprint.validate(blueprint,kind);
        return new Entry(r.getString("id"),kind,r.getString("name"),r.getString("description"),r.getString("category"),r.getBoolean("favorite"),r.getLong("version"),r.getTimestamp("updated_at").toInstant(),counts(blueprint),content?blueprint:null);
    }
    Entry read(String owner,String id){return db.query("select * from personal_templates where owner_id=? and id=?",(r,n)->row(r,true),owner,id).stream().findFirst().orElseThrow(()->new NotFoundException("个人模板不存在。"));}
    PageResponse<Entry> list(String owner,int page,String search,String kind,String category,boolean favorite){
        if(page<0||page>100000||search.length()>120||category.length()>60||!Set.of("ALL","PLAN","ROUTE").contains(kind))throw new IllegalArgumentException("模板查询条件无效。");
        String where=" where owner_id=?";List<Object> args=new ArrayList<>(List.of(owner));
        if(!kind.equals("ALL")){where+=" and kind=?";args.add(kind);}if(!category.isEmpty()){where+=" and category=?";args.add(category);}if(favorite)where+=" and favorite=true";
        where+=" and lower(name) like ? escape '!'";args.add("%"+search.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        long total=db.queryForObject("select count(*) from personal_templates"+where,Long.class,args.toArray());args.add(page*12);
        var content=db.query("select * from personal_templates"+where+" order by favorite desc,updated_at desc,id limit 12 offset ?",(r,n)->row(r,false),args.toArray());return new PageResponse<>(content,page,12,total,(int)((total+11)/12));
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    Preview capturePreview(String owner,Capture request){return capturePreview(request,source(owner,request));}
    private Preview capturePreview(Capture r,JsonNode blueprint){return new Preview(hash(List.of(r,blueprint)),blueprint.path("kind").asText(),counts(blueprint),blueprint,WARNINGS);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    Result save(String owner,Save apply){
        lock(owner);String fingerprint=hash(List.of("CAPTURE",apply.request(),apply.checksum()));Result replay=replay(owner,apply.operationKey(),fingerprint);if(replay!=null)return replay;
        var r=apply.request();JsonNode blueprint=source(owner,r);Preview p=capturePreview(r,blueprint);same(p.checksum(),apply.checksum());
        Instant now=Instant.now();String id=r.targetId()==null?UUID.randomUUID().toString():r.targetId().toString();
        if(r.targetId()==null){
            if(db.queryForObject("select count(*) from personal_templates where owner_id=?",Integer.class,owner)>=100)throw new ConflictException("最多100份个人模板，请先整理；原数据未改变。");
            db.update("insert into personal_templates(id,owner_id,kind,name,description,category,favorite,blueprint_json,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?)",id,owner,p.kind(),r.name().trim(),r.description(),r.category().trim(),r.favorite(),encode(TemplateBlueprint.detach(json,blueprint)),Timestamp.from(now),Timestamp.from(now));
        }else{
            Entry previous=read(owner,id);if(previous.version()!=r.version()||!previous.kind().equals(p.kind()))throw new ConflictException("模板版本或模式已变化，请重新选择来源和预览。");
            db.update("update personal_templates set name=?,description=?,category=?,favorite=?,blueprint_json=?,version=version+1,updated_at=? where id=? and owner_id=?",r.name().trim(),r.description(),r.category().trim(),r.favorite(),encode(TemplateBlueprint.detach(json,blueprint)),Timestamp.from(now),id,owner);
        }
        Result result=new Result(id,p.kind(),"/templates?selected="+id,p.counts().items());record(owner,apply.operationKey(),fingerprint,result);return result;
    }
    @Transactional Entry edit(String owner,String id,Edit r){lock(owner);Entry e=read(owner,id);if(e.version()!=r.version())throw new ConflictException("模板已在其他设备修改，请保留输入并重新读取。");db.update("update personal_templates set name=?,description=?,category=?,favorite=?,version=version+1,updated_at=? where id=? and owner_id=?",r.name().trim(),r.description(),r.category().trim(),r.favorite(),Timestamp.from(Instant.now()),id,owner);return read(owner,id);}
    @Transactional void delete(String owner,String id,long version){lock(owner);if(read(owner,id).version()!=version)throw new ConflictException("模板已变化，未删除。请重新读取。");db.update("delete from personal_templates where id=? and owner_id=?",id,owner);}
    Preview usePreview(String owner,String id,Use r){return usePreview(id,r,read(owner,id));}
    private OrchestrationService.Copy request(String id,Use r,Entry e){if(e.version()!=r.version())throw new ConflictException("模板已变化，请重新读取并预览。");if(e.kind().equals("PLAN")&&r.restDaysMask()==null)throw new IllegalArgumentException("请为新计划明确选择休息日；可以不选择任何休息日。");if(e.kind().equals("ROUTE")&&(r.startDate()!=null||r.endDate()!=null||r.restDaysMask()!=null))throw new IllegalArgumentException("目标路线不设置周期或休息日。");return new OrchestrationService.Copy(e.kind(),id,r.name(),r.startDate(),r.endDate(),r.restDaysMask());}
    private Preview usePreview(String id,Use r,Entry e){
        copies.templatePreview(request(id,r,e),TemplateBlueprint.snapshot(json,e.blueprint()));
        ObjectNode display=e.blueprint().deepCopy();((ObjectNode)display.path("parent")).put("name",r.name().trim());
        if(e.kind().equals("PLAN")){
            long shift=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(display.path("parent").path("start_date").asText()),r.startDate());
            ((ObjectNode)display.path("parent")).put("start_date",r.startDate().toString()).put("end_date",r.endDate().toString()).put("rest_days_mask",r.restDaysMask());
            for(String table:List.of("plan_tasks","plan_milestones")){String field=table.equals("plan_tasks")?"task_date":"milestone_date";for(var row:display.path("rows").path(table))((ObjectNode)row).put(field,LocalDate.parse(row.path(field).asText()).plusDays(shift).toString());}
        }
        return new Preview(hash(List.of(id,r,e.blueprint())),e.kind(),e.counts(),display,WARNINGS);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED) Result apply(String owner,String id,Apply apply){
        lock(owner);String fingerprint=hash(List.of("USE",id,apply.request(),apply.checksum()));Result replay=replay(owner,apply.operationKey(),fingerprint);if(replay!=null)return replay;
        Entry e=read(owner,id);Preview p=usePreview(id,apply.request(),e);same(p.checksum(),apply.checksum());
        var result=copies.copyStructure(owner,request(id,apply.request(),e),TemplateBlueprint.snapshot(json,e.blueprint()));
        Result response=new Result(result.id(),e.kind(),e.kind().equals("PLAN")?"/plans/"+result.id():"/mainlines?line="+result.id(),result.changed());record(owner,apply.operationKey(),fingerprint,response);return response;
    }
    private JsonNode source(String owner,Capture r){
        if((r.targetId()==null)!=(r.version()==null))throw new IllegalArgumentException("更新模板须携带当前版本；新模板不能携带旧版本。");
        if(r.targetId()!=null&&read(owner,r.targetId().toString()).version()!=r.version())throw new ConflictException("模板已变化，请保留输入并重新读取。");
        if(!r.source().equals("AI_DRAFT"))return TemplateBlueprint.capture(json,r.source(),copies.templateSource(owner,r.source(),r.sourceId().toString()));
        var saved=db.queryForList("select content_json,adjustment_json,source_session_id from ai_saved_drafts where owner_id=? and id=? for update",owner,r.sourceId().toString());
        if(saved.isEmpty())throw new NotFoundException("本人保存的AI草稿不存在。");var row=saved.getFirst();
        if(row.get("adjustment_json")!=null)throw new IllegalArgumentException("调整草稿不直接转为整套模板，请先在本人原对象确认应用，再从完整对象保存模板。");
        if(row.get("source_session_id")!=null&&db.queryForObject("select count(*) from ai_jobs where session_id=? and state in ('QUEUED','RUNNING')",Integer.class,row.get("source_session_id"))>0)throw new ConflictException("AI仍在处理，请完成并保存草稿后再建立模板。");
        JsonNode draft=decode((String)row.get("content_json")).path("draft");AiDrafts.Draft d;
        try{d=AiDrafts.parseDraft(encode(draft),json,validator);}catch(RuntimeException ex){throw new IllegalArgumentException("AI草稿结构尚不完整，请先在工作台核对并保存；不会调用AI。");}
        if(d.mode()==AiDrafts.Mode.TODO)throw new IllegalArgumentException("首版整套模板支持周期计划和目标路线，不保存临时待办。");
        return fromDraft(d);
    }
    private JsonNode fromDraft(AiDrafts.Draft d){
        String kind=d.mode().name(),parentId=uuid("parent");Map<String,Object> parent=new LinkedHashMap<>(Map.of("id",parentId,"name",d.title(),"description",""));Map<String,List<Map<String,Object>>> rows=new LinkedHashMap<>();for(String t:TemplateBlueprint.tables(kind))rows.put(t,new ArrayList<>());
        if(kind.equals("PLAN")){
            parent.putAll(Map.of("start_date",d.startDate().toString(),"end_date",d.endDate().toString(),"rest_days_mask",d.restDays().stream().mapToInt(day->1<<(day-1)).sum()));
            rows.get("plan_tags").add(new LinkedHashMap<>(Map.of("id",uuid("overall"),"name","总计","color","#002FA7","overall_tag",true)));
            int order=0;for(var item:d.items()){
                Map<String,Object> r=new LinkedHashMap<>();r.putAll(Map.of("id",uuid("item:"+item.localId()),"task_date",item.dueDate().toString(),"planned_minutes",item.plannedMinutes(),"category","AI规划","title",item.title(),"description",item.note(),"acceptance_json","[]","resource_url","","required_task",true,"sort_order",order++));
                for(String f:List.of("tag_id","start_time","end_time","recurrence_id"))r.put(f,null);rows.get("plan_tasks").add(r);
            }
        }else{
            parent.putAll(Map.of("color","#002FA7","execution_mode","FLEXIBLE"));int order=0;for(var stage:d.stages())rows.get("mainline_stages").add(new LinkedHashMap<>(Map.of("id",uuid("stage:"+stage.localId()),"name",stage.title(),"description","","sort_order",order++)));
            order=0;for(var item:d.items()){
                Map<String,Object> r=new LinkedHashMap<>(Map.of("id",uuid("item:"+item.localId()),"title",item.title(),"description",item.note(),"acceptance_json","[]","planned_minutes",item.plannedMinutes(),"sort_order",order++,"dependency_mode",item.prerequisites().isEmpty()?"NONE":"EXPLICIT","prerequisite_ids_json",encode(item.prerequisites().stream().map(x->uuid("item:"+x)).toList())));r.put("stage_id",item.stageId()==null?null:uuid("stage:"+item.stageId()));rows.get("mainline_items").add(r);
            }
        }
        return TemplateBlueprint.capture(json,kind,new OrchestrationService.Snapshot(parent,rows));
    }
    private static String uuid(String value){return UUID.nameUUIDFromBytes(("personal-template:"+value).getBytes(StandardCharsets.UTF_8)).toString();}
    private Counts counts(JsonNode n){var rows=n.path("rows");return new Counts(rows.path(n.path("kind").asText().equals("PLAN")?"plan_tasks":"mainline_items").size(),rows.path("mainline_stages").size(),rows.path("plan_tags").size(),rows.path("plan_milestones").size());}
    private Result replay(String owner,UUID key,String fingerprint){var rows=db.queryForList("select fingerprint,result_json from personal_template_operations where owner_id=? and operation_key=?",owner,key.toString());if(rows.isEmpty())return null;same((String)rows.getFirst().get("fingerprint"),fingerprint);try{return json.readValue((String)rows.getFirst().get("result_json"),Result.class);}catch(Exception e){throw new IllegalStateException("模板应用记录无效。");}}
    private void record(String owner,UUID key,String fingerprint,Result result){db.update("insert into personal_template_operations(id,owner_id,operation_key,fingerprint,result_json,created_at) values(?,?,?,?,?,?)",UUID.randomUUID().toString(),owner,key.toString(),fingerprint,encode(result),Timestamp.from(Instant.now()));}
    private static void same(String actual,String expected){if(!Objects.equals(actual,expected))throw new ConflictException("预览已过期或操作编号内容已变化。请保留输入，核对结果后重新预览。");}
}
