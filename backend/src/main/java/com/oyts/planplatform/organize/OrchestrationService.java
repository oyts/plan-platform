package com.oyts.planplatform.organize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.*;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Explicit structural copies and previewed edits. Never accepts client-supplied SQL, rows or ownership. */
@Service @Transactional(readOnly=true)
class OrchestrationService {
    record Copy(@NotBlank @Pattern(regexp="PLAN|ROUTE") String kind,@NotBlank String sourceId,
                @NotBlank @Size(max=120) String name,LocalDate startDate,LocalDate endDate,
                @Min(0) @Max(127) Integer restDaysMask){}
    record CopyApply(@NotNull @Valid Copy request,@NotBlank String checksum,@NotNull UUID operationKey,@AssertTrue boolean confirmed){}
    record Batch(@NotBlank String planId,@NotBlank String recurrenceId,@NotNull LocalDate from,
                 @Min(-3660) @Max(3660) int shiftDays,@Min(1) @Max(1440) Integer plannedMinutes,boolean replaceTag,String tagId){}
    record BatchApply(@NotNull @Valid Batch request,@NotBlank String checksum,@NotNull UUID operationKey,@AssertTrue boolean confirmed){}
    record Change(String id,String title,String beforeDate,String afterDate,int beforeMinutes,int afterMinutes,String beforeTag,String afterTag){}
    record Protected(String id,String title,String reason){}
    record Preview(String checksum,int tasks,int stages,int tags,int milestones,List<Change> changes,List<Protected> protectedItems,List<String> warnings){}
    record Result(String id,int changed,String kind){}
    record RouteSummary(String id,String name,String description,String color,long version,boolean paused,boolean archived,String deletedAt,String executionMode,long total,long completed){}
    private record Snapshot(Map<String,Object> parent,Map<String,List<Map<String,Object>>> rows){}
    private final JdbcTemplate db;private final EntityManager em;private final ObjectMapper mapper;
    OrchestrationService(JdbcTemplate db,EntityManager em,ObjectMapper mapper){this.db=db;this.em=em;this.mapper=mapper;}

    com.oyts.planplatform.plan.PlanDtos.PageResponse<RouteSummary> routes(String owner,int page,int size,String search,String state){
        if(page<0||page>100000||size<1||size>100||search.length()>120||!Set.of("ACTIVE","PAUSED","ARCHIVED","TRASH").contains(state))throw new IllegalArgumentException("路线查询条件无效。");
        String where=" where l.owner_id=? and "+switch(state){case "TRASH"->"l.deleted_at is not null";case "ARCHIVED"->"l.deleted_at is null and l.archived=true";case "PAUSED"->"l.deleted_at is null and l.archived=false and l.paused=true";default->"l.deleted_at is null and l.archived=false";};
        String pattern="%"+search.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";where+=" and lower(l.name) like ? escape '!'";
        long total=db.queryForObject("select count(*) from mainlines l"+where,Long.class,owner,pattern);
        var content=db.query("select l.*,(select count(*) from mainline_items i where i.mainline_id=l.id and i.deleted_at is null) as total,(select count(*) from mainline_items i where i.mainline_id=l.id and i.deleted_at is null and i.status='COMPLETED') as completed from mainlines l"+where+" order by l.created_at desc,l.id limit ? offset ?",(rs,n)->new RouteSummary(rs.getString("id"),rs.getString("name"),rs.getString("description"),rs.getString("color"),rs.getLong("version"),rs.getBoolean("paused"),rs.getBoolean("archived"),rs.getTimestamp("deleted_at")==null?null:rs.getTimestamp("deleted_at").toInstant().toString(),rs.getString("execution_mode"),rs.getLong("total"),rs.getLong("completed")),owner,pattern,size,(long)page*size);
        return new com.oyts.planplatform.plan.PlanDtos.PageResponse<>(content,page,size,total,(int)((total+size-1)/size));
    }

    Preview copyPreview(String owner,Copy request){return copyPreview(request,snapshot(owner,request.kind(),request.sourceId(),false));}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    Result copy(String owner,CopyApply apply){
        lockOwner(owner);String fingerprint=hash(List.of("COPY",apply.request(),apply.checksum()));
        Result replay=replay(owner,apply.operationKey(),fingerprint);if(replay!=null)return replay;
        Copy r=apply.request();Snapshot s=snapshot(owner,r.kind(),r.sourceId(),true);Preview preview=copyPreview(r,s);
        check(preview.checksum(),apply.checksum());String target=id();Map<String,String> ids=new HashMap<>();ids.put(text(s.parent(),"id"),target);
        for(var rows:s.rows().values())for(var row:rows)ids.put(text(row,"id"),id());
        if(r.kind().equals("PLAN")){
            long shift=ChronoUnit.DAYS.between(date(s.parent(),"start_date"),r.startDate());
            var plan=structural(s.parent(),List.of("name","description","start_date","end_date","archived","rest_days_mask","deleted_at"));
            plan.putAll(Map.of("id",target,"owner_id",owner,"name",r.name().trim(),"start_date",r.startDate(),"end_date",r.endDate(),"archived",false));
            plan.put("deleted_at",null);if(r.restDaysMask()!=null)plan.put("rest_days_mask",r.restDaysMask());insert("plans",plan);
            for(var row:s.rows().get("plan_tags")){
                var copy=structural(row,List.of("name","color","overall_tag"));copy.putAll(Map.of("id",ids.get(text(row,"id")),"plan_id",target));insert("plan_tags",copy);
            }
            Map<String,String> batches=new HashMap<>();
            for(var row:s.rows().get("plan_tasks")){
                var copy=structural(row,List.of("start_time","end_time","planned_minutes","category","title","description","acceptance_json","resource_url","required_task","sort_order","template_id"));
                String taskId=ids.get(text(row,"id"));copy.putAll(Map.of("id",taskId,"plan_id",target,"task_date",date(row,"task_date").plusDays(shift)));
                copy.put("tag_id",row.get("tag_id")==null?null:ids.get(text(row,"tag_id")));
                copy.put("recurrence_id",row.get("recurrence_id")==null?null:batches.computeIfAbsent(text(row,"recurrence_id"),ignored->id()));insert("plan_tasks",copy);
                var progress=structural(Map.of(),List.of());progress.putAll(Map.of("id",id(),"task_id",taskId,"status","PENDING","accumulated_seconds",0,"acceptance_json","[]","note","","skip_reason",""));insert("task_progress",progress);
            }
            for(var row:s.rows().get("plan_milestones")){
                var copy=structural(row,List.of("name","description","acceptance_json"));copy.putAll(Map.of("id",ids.get(text(row,"id")),"plan_id",target,"milestone_date",date(row,"milestone_date").plusDays(shift),"checks_json","[]","note",""));insert("plan_milestones",copy);
            }
        }else{
            var line=structural(s.parent(),List.of("description","color","execution_mode"));line.putAll(Map.of("id",target,"owner_id",owner,"name",r.name().trim(),"archived",false,"paused",false));insert("mainlines",line);
            for(var row:s.rows().get("mainline_stages")){
                var copy=structural(row,List.of("name","description","sort_order"));copy.putAll(Map.of("id",ids.get(text(row,"id")),"mainline_id",target));insert("mainline_stages",copy);
            }
            for(var row:s.rows().get("mainline_items")){
                var copy=structural(row,List.of("title","description","acceptance_json","planned_minutes","sort_order","dependency_mode"));
                List<String> dependencies=readIds(text(row,"prerequisite_ids_json"));
                if(dependencies.stream().anyMatch(key->!ids.containsKey(key)))throw new ConflictException("路线仍引用回收站中的前置步骤，请先处理依赖后复制。");
                copy.putAll(Map.of("id",ids.get(text(row,"id")),"mainline_id",target,"status","PENDING","accumulated_seconds",0,"note","","prerequisite_ids_json",json(dependencies.stream().map(ids::get).toList())));
                copy.put("stage_id",row.get("stage_id")==null?null:ids.get(text(row,"stage_id")));insert("mainline_items",copy);
            }
        }
        Result result=new Result(target,preview.tasks(),r.kind());save(owner,apply.operationKey(),fingerprint,result);return result;
    }
    Preview batchPreview(String owner,Batch r){return batchPreview(r,snapshot(owner,"PLAN",r.planId(),false));}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    Result batch(String owner,BatchApply apply){
        lockOwner(owner);String fingerprint=hash(List.of("BATCH",apply.request(),apply.checksum()));Result replay=replay(owner,apply.operationKey(),fingerprint);if(replay!=null)return replay;
        Batch r=apply.request();Snapshot s=snapshot(owner,"PLAN",r.planId(),true);Preview preview=batchPreview(r,s);check(preview.checksum(),apply.checksum());
        for(Change change:preview.changes()){
            var row=s.rows().get("plan_tasks").stream().filter(task->text(task,"id").equals(change.id())).findFirst().orElseThrow();
            int updated=write("update plan_tasks set task_date=?,planned_minutes=?,tag_id=?,version=version+1,updated_at=? where id=? and version=?",LocalDate.parse(change.afterDate()),change.afterMinutes(),change.afterTag(),Timestamp.from(Instant.now()),change.id(),row.get("version"));
            if(updated!=1)throw new ConflictException("任务已变化，整批调整未应用。请重新预览。");
        }
        Result result=new Result(r.planId(),preview.changes().size(),"BATCH");save(owner,apply.operationKey(),fingerprint,result);return result;
    }
    private Preview copyPreview(Copy r,Snapshot s){
        if(r.kind().equals("PLAN")){
            if(r.startDate()==null||r.endDate()==null||r.endDate().isBefore(r.startDate()))throw new IllegalArgumentException("请选择有效的新计划周期。");
            long shift=ChronoUnit.DAYS.between(date(s.parent(),"start_date"),r.startDate());
            for(var entry:Map.of("plan_tasks","task_date","plan_milestones","milestone_date").entrySet())for(var row:s.rows().get(entry.getKey())){
                LocalDate day=date(row,entry.getValue()).plusDays(shift);
                if(day.isBefore(r.startDate())||day.isAfter(r.endDate()))throw new IllegalArgumentException("新周期不能容纳全部任务和里程碑，请延长结束日期。不会静默丢弃任务。");
            }
        }
        return new Preview(hash(List.of(r,s)),s.rows().get(r.kind().equals("PLAN")?"plan_tasks":"mainline_items").size(),s.rows().getOrDefault("mainline_stages",List.of()).size(),s.rows().getOrDefault("plan_tags",List.of()).size(),s.rows().getOrDefault("plan_milestones",List.of()).size(),List.of(),List.of(),List.of("只复制未删除的结构；完成状态、实际用时、复盘和执行记录均不复制。","日期按起点等距平移；休息日仅作标记，不自动移动或删除任务。"));
    }
    private Preview batchPreview(Batch r,Snapshot s){
        if(r.from().isBefore(today().plusDays(1)))throw new IllegalArgumentException("只能批量调整明天及以后的未开始实例。");
        if(r.replaceTag()&&r.tagId()!=null&&s.rows().get("plan_tags").stream().noneMatch(tag->text(tag,"id").equals(r.tagId())))throw new NotFoundException("标签不属于当前计划。");
        Map<String,Map<String,Object>> progress=new HashMap<>();s.rows().get("task_progress").forEach(row->progress.put(text(row,"task_id"),row));
        Set<String> timed=new HashSet<>();s.rows().get("time_entries").forEach(row->timed.add(text(row,"progress_id")));
        List<Change> changes=new ArrayList<>();List<Protected> protectedItems=new ArrayList<>();boolean found=false;
        for(var row:s.rows().get("plan_tasks")){
            if(!r.recurrenceId().equals(row.get("recurrence_id")))continue;found=true;if(date(row,"task_date").isBefore(r.from()))continue;
            var p=progress.get(text(row,"id"));String reason=null;
            if(p==null)throw new ConflictException("任务进度缺失，请先联系管理员检查。");
            if(!text(p,"status").equals("PENDING")||number(p,"accumulated_seconds")!=0||p.get("started_at")!=null||p.get("completed_at")!=null||!text(p,"note").isBlank()||p.get("defer_to")!=null||!text(p,"skip_reason").isBlank()||text(p,"acceptance_json").contains("true")||timed.contains(text(p,"id")))reason="已有执行、验收或计时记录，保持不变";
            LocalDate after=date(row,"task_date").plusDays(r.shiftDays());
            if(reason!=null){protectedItems.add(new Protected(text(row,"id"),text(row,"title"),reason));continue;}
            if(!after.isAfter(today())||after.isBefore(date(s.parent(),"start_date"))||after.isAfter(date(s.parent(),"end_date")))throw new IllegalArgumentException("调整后日期必须在计划周期内且晚于今天；整批未应用。");
            String tag=r.replaceTag()?r.tagId():(String)row.get("tag_id");int minutes=r.plannedMinutes()==null?(int)number(row,"planned_minutes"):r.plannedMinutes();
            if(!after.equals(date(row,"task_date"))||minutes!=number(row,"planned_minutes")||!Objects.equals(tag,row.get("tag_id")))changes.add(new Change(text(row,"id"),text(row,"title"),date(row,"task_date").toString(),after.toString(),(int)number(row,"planned_minutes"),minutes,(String)row.get("tag_id"),tag));
        }
        if(!found)throw new NotFoundException("重复批次不存在。");
        return new Preview(hash(List.of(r,s,today())),changes.size(),0,0,0,changes,protectedItems,List.of("只修改此批次从指定日期起的未来未开始任务。更早历史保持不变。","休息日不会触发自动移动；预览后任务、进度、标签或流水变化将整批拒绝。"));
    }
    private Snapshot snapshot(String owner,String kind,String id,boolean lock){
        String table=kind.equals("PLAN")?"plans":"mainlines";String suffix=lock?" for update":"";
        var parents=rows("select * from "+table+" where id=? and owner_id=? and deleted_at is null and archived=false"+suffix,id,owner);
        if(parents.isEmpty())throw new NotFoundException("原记录不存在或已归档。");Map<String,List<Map<String,Object>>> records=new LinkedHashMap<>();
        if(kind.equals("PLAN")){
            for(String child:List.of("plan_tags","plan_tasks","plan_milestones"))records.put(child,rows("select * from "+child+" where plan_id=? and deleted_at is null order by id limit 2001"+suffix,id));
            records.put("task_progress",rows("select g.* from task_progress g join plan_tasks k on k.id=g.task_id where k.plan_id=? and k.deleted_at is null order by g.id limit 2001"+suffix,id));
            records.put("time_entries",rows("select t.id,t.progress_id,t.started_at,t.ended_at,t.seconds,t.source from time_entries t join task_progress g on g.id=t.progress_id join plan_tasks k on k.id=g.task_id where k.plan_id=? and k.deleted_at is null order by t.id limit 2001"+suffix,id));
        }else{
            records.put("mainline_stages",rows("select * from mainline_stages where mainline_id=? order by id limit 1001"+suffix,id));
            records.put("mainline_items",rows("select * from mainline_items where mainline_id=? and deleted_at is null order by id limit 1001"+suffix,id));
        }
        if(records.values().stream().anyMatch(value->value.size()>(kind.equals("PLAN")?2000:1000)))throw new IllegalArgumentException("此结构过大，请先按阶段拆分；原数据未修改。");
        return new Snapshot(parents.getFirst(),records);
    }
    private List<Map<String,Object>> rows(String query,Object...args){return db.query(query,(rs,n)->{Map<String,Object> row=new TreeMap<>();var meta=rs.getMetaData();for(int i=1;i<=meta.getColumnCount();i++){
        Object value=rs.getObject(i);if(value instanceof Clob clob)value=clob.getSubString(1,(int)clob.length());else if(value instanceof Timestamp time)value=time.toInstant().toString();else if(value instanceof java.sql.Date day)value=day.toLocalDate().toString();else if(value instanceof Time time)value=time.toLocalTime().toString();
        row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT),value);
    }return row;},args);}
    private Map<String,Object> structural(Map<String,Object> source,List<String> fields){Map<String,Object> result=new LinkedHashMap<>();for(String field:fields)result.put(field,source.get(field));result.put("version",0);result.put("created_at",Timestamp.from(Instant.now()));result.put("updated_at",Timestamp.from(Instant.now()));return result;}
    private int write(String sql,Object...args){try{return db.update(sql,args);}catch(org.springframework.dao.DataIntegrityViolationException failure){throw new ConflictException("数据关系已变化，整批编排未应用。请保留输入并重新预览。");}}
    private void insert(String table,Map<String,Object> row){write("insert into "+table+" ("+String.join(",",row.keySet())+") values ("+String.join(",",Collections.nCopies(row.size(),"?"))+")",row.values().toArray());}
    private void lockOwner(String owner){if(db.queryForList("select id from users where id=? for update",owner).isEmpty())throw new NotFoundException("用户不存在。");}
    private Result replay(String owner,UUID key,String fingerprint){var found=db.queryForList("select fingerprint,result_json from orchestration_operations where owner_id=? and operation_key=?",owner,key.toString());if(found.isEmpty())return null;check((String)found.getFirst().get("fingerprint"),fingerprint);try{return mapper.readValue((String)found.getFirst().get("result_json"),Result.class);}catch(Exception error){throw new IllegalStateException("应用记录无法读取。",error);}}
    private void save(String owner,UUID key,String fingerprint,Result result){write("insert into orchestration_operations (id,owner_id,operation_key,fingerprint,result_json,created_at) values (?,?,?,?,?,?)",id(),owner,key.toString(),fingerprint,json(result),Timestamp.from(Instant.now()));}
    private String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("编排数据无法读取。",e);}}
    private List<String> readIds(String json){try{return mapper.readValue(json,mapper.getTypeFactory().constructCollectionType(List.class,String.class));}catch(Exception e){throw new ConflictException("原路线依赖数据无效。");}}
    private static void check(String actual,String expected){if(!Objects.equals(actual,expected))throw new ConflictException("预览已过期或相同操作编号的内容发生变化，请保留输入并重新预览。");}
    private static LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Shanghai"));}
    private static LocalDate date(Map<String,Object> row,String key){return LocalDate.parse(text(row,key));}
    private static String text(Map<String,Object> row,String key){return Objects.toString(row.get(key),"");}
    private static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private static String id(){return UUID.randomUUID().toString();}
}
