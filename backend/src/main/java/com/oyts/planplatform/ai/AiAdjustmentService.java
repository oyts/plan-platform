package com.oyts.planplatform.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.*;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** No model-supplied database IDs or deletes. All calls join the conversation transaction. */
@Service
class AiAdjustmentService {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final Validator validator;
    AiAdjustmentService(JdbcTemplate db,ObjectMapper mapper,Validator validator) { this.db=db;this.mapper=mapper;this.validator=validator; }
    record Snapshot(AiDrafts.Mode mode,String targetId,AiDrafts.Draft original,Map<String,String> items,
                    Map<String,String> stages,Set<String> protectedItems,String fingerprint) {}
    record View(AiDrafts.Draft original,Set<String> protectedItems,String path) {}
    private record Data(Map<String,Object> root,List<Map<String,Object>> items,List<Map<String,Object>> stages,
                        List<Map<String,Object>> progress,List<Map<String,Object>> times) {}

    Snapshot capture(String owner,AiDrafts.Mode mode,String id) {
        if(id==null || !id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("请从具体计划、路线或待办进入 AI 调整。");
        Data data=load(owner,mode,id);
        var live=data.items.stream().filter(row->mode==AiDrafts.Mode.TODO?!bool(row,"deleted"):row.get("deleted_at")==null)
            .sorted(Comparator.<Map<String,Object>>comparingInt(row->mode==AiDrafts.Mode.TODO?0:integer(row,"sort_order")).thenComparing(row->text(row,"id"))).toList();
        if(live.size()>100 || data.stages.size()>30) throw new IllegalArgumentException("此对象超过 AI 单次调整范围（100项、30阶段），请先手动拆分；不会截断或遗漏原内容。");
        var itemIds=new LinkedHashMap<String,String>();var stageIds=new LinkedHashMap<String,String>();
        for(int i=0;i<live.size();i++) itemIds.put("existing_"+(i+1),text(live.get(i),"id"));
        var stageRows=data.stages.stream().sorted(Comparator.comparingInt(row->integer(row,"sort_order"))).toList();
        var stages=new ArrayList<AiDrafts.Stage>();
        for(int i=0;i<stageRows.size();i++) {String key="stage_"+(i+1);stageIds.put(key,text(stageRows.get(i),"id"));stages.add(new AiDrafts.Stage(key,text(stageRows.get(i),"name")));}
        Map<String,Map<String,Object>> progress=new HashMap<>();
        for(var row:data.progress) progress.put(text(row,"task_id"),row);
        Set<String> timed=new HashSet<>();for(var row:data.times) timed.add(text(row,"progress_id"));
        Set<String> protectedIds=new LinkedHashSet<>();var items=new ArrayList<AiDrafts.Item>();
        int i=0;
        for(var row:live) {
            String key="existing_"+(++i), real=text(row,"id");var state=mode==AiDrafts.Mode.PLAN?progress.get(real):row;
            boolean protect=mode==AiDrafts.Mode.TODO?bool(row,"done"):
                state==null || !"PENDING".equals(text(state,"status")) || number(state,"accumulated_seconds")!=0 ||
                state.get("started_at")!=null || state.get("completed_at")!=null ||
                !text(state,"note").isEmpty() || mode==AiDrafts.Mode.PLAN && (timed.contains(text(state,"id")) || !"[]".equals(text(state,"acceptance_json")));
            if(protect) protectedIds.add(key);
            List<String> dependencies=List.of();
            if(mode==AiDrafts.Mode.ROUTE) {
                List<String> realDependencies;
                if("EXPLICIT".equals(text(row,"dependency_mode"))) realDependencies=strings(text(row,"prerequisite_ids_json"));
                else if("NONE".equals(text(row,"dependency_mode")) || "FLEXIBLE".equals(text(data.root,"execution_mode"))) realDependencies=List.of();
                else realDependencies=live.stream().filter(r->integer(r,"sort_order")<integer(row,"sort_order")).map(r->text(r,"id")).toList();
                dependencies=realDependencies.stream().map(value->local(itemIds,value)).toList();
            }
            items.add(new AiDrafts.Item(key,text(row,"title"),text(row,mode==AiDrafts.Mode.TODO?"note":"description"),
                mode==AiDrafts.Mode.ROUTE?null:date(row,mode==AiDrafts.Mode.PLAN?"task_date":"due_date"),
                mode==AiDrafts.Mode.TODO?0:integer(row,"planned_minutes"),
                mode==AiDrafts.Mode.ROUTE && row.get("stage_id")!=null?local(stageIds,text(row,"stage_id")):null,dependencies));
        }
        var draft=new AiDrafts.Draft(1,mode,0,mode==AiDrafts.Mode.TODO?"调整今日待办":text(data.root,"name"),
            mode==AiDrafts.Mode.PLAN?date(data.root,"start_date"):null,mode==AiDrafts.Mode.PLAN?date(data.root,"end_date"):null,
            mode==AiDrafts.Mode.PLAN?restDays(integer(data.root,"rest_days_mask")):List.of(),stages,items);
        return new Snapshot(mode,id,draft,itemIds,stageIds,protectedIds,fingerprint(data));
    }
    View view(Snapshot s) { return s==null?null:new View(s.original,s.protectedItems,path(s)); }
    Snapshot decode(String source) { if(source==null)return null;try{return mapper.readValue(source,Snapshot.class);}catch(Exception invalid){throw new IllegalStateException("AI 调整快照无法读取。");} }
    String encode(Snapshot s) {return json(s);}
    AiDrafts.Draft parse(Snapshot s,String source) {
        var draft=AiDrafts.parseDraft(source,mapper,validator,s==null?Set.of():s.items.keySet());
        if(s!=null) validate(s,draft);return draft;
    }
    void validate(Snapshot s,AiDrafts.Draft draft) {
        if(draft.mode()!=s.mode) throw new IllegalArgumentException("调整不能切换业务模式。");
        var proposed=new LinkedHashMap<String,AiDrafts.Item>();draft.items().forEach(item->proposed.put(item.localId(),item));
        if(!proposed.keySet().containsAll(s.items.keySet())) throw new IllegalArgumentException("调整必须保留全部原事项，不能删除或更换原事项标识。");
        var stageKeys=draft.stages().stream().map(AiDrafts.Stage::localId).toList();
        if(!stageKeys.containsAll(s.stages.keySet())) throw new IllegalArgumentException("调整必须保留原路线阶段。");
        if(!draft.items().stream().map(AiDrafts.Item::localId).filter(s.items::containsKey).toList().equals(s.original.items().stream().map(AiDrafts.Item::localId).toList()))
            throw new IllegalArgumentException("请保留原事项顺序，通过日期或前置依赖调整节奏。");
        if(s.mode==AiDrafts.Mode.TODO && !draft.title().equals(s.original.title())) throw new IllegalArgumentException("待办调整请修改事项，不修改分组名称。");
        boolean datesChanged=!Objects.equals(s.original.startDate(),draft.startDate()) || !Objects.equals(s.original.endDate(),draft.endDate());
        if(s.mode==AiDrafts.Mode.PLAN && datesChanged && (!s.original.startDate().equals(draft.startDate()) || draft.endDate().isBefore(s.original.endDate())))
            throw new IllegalArgumentException("调整保留原周期起点，结束日期只能延长，不能缩短已有任务和复盘的历史范围。");
        for(var old:s.original.items()) {
            var next=proposed.get(old.localId());
            if(s.protectedItems.contains(old.localId()) && !old.equals(next)) throw new IllegalArgumentException("有执行记录或已完成的事项受到保护，不能改写："+old.title());
            if(s.mode==AiDrafts.Mode.PLAN) {
                boolean legacyUnchanged=Objects.equals(old.dueDate(),next.dueDate()) && !datesChanged && s.original.restDays().equals(draft.restDays());
                if(!s.protectedItems.contains(old.localId()) && !legacyUnchanged &&
                    (next.dueDate().isBefore(draft.startDate()) || next.dueDate().isAfter(draft.endDate()) || draft.restDays().contains(next.dueDate().getDayOfWeek().getValue())))
                    throw new IllegalArgumentException("调整后的未开始任务必须在周期内，且不能落在休息日。");
                if(s.protectedItems.contains(old.localId()) && datesChanged && (old.dueDate().isBefore(draft.startDate()) || old.dueDate().isAfter(draft.endDate())))
                    throw new IllegalArgumentException("新周期不能排除已有执行历史的日期。");
            }
        }
    }
    AiDraftApplier.Applied apply(String owner,Snapshot s,AiDrafts.Draft draft) {
        validate(s,draft);
        Data current=load(owner,s.mode,s.targetId);
        if(!s.fingerprint.equals(fingerprint(current))) throw new ConflictException("原对象、事项、进度或依赖已被其他设备修改，请重新创建调整对话；当前草稿仍保留。");
        Map<String,AiDrafts.Item> originals=new HashMap<>();s.original.items().forEach(item->originals.put(item.localId(),item));
        Map<String,AiDrafts.Stage> originalStages=new HashMap<>();s.original.stages().forEach(stage->originalStages.put(stage.localId(),stage));
        int count=0;
        Map<String,String> realStages=new HashMap<>(s.stages),realItems=new HashMap<>(s.items);
        int stageOrder=current.stages.stream().mapToInt(row->integer(row,"sort_order")).max().orElse(-1)+1;
        for(var stage:draft.stages()) {
            String real=realStages.get(stage.localId());
            if(real==null) {real=UUID.randomUUID().toString();realStages.put(stage.localId(),real);db.update("INSERT INTO mainline_stages(id,mainline_id,name,sort_order,version,created_at,updated_at) VALUES (?,?,?,?,0,?,?)",real,s.targetId,stage.title(),stageOrder++,now(),now());count++;}
            else if(!stage.equals(originalStages.get(stage.localId()))) {db.update("UPDATE mainline_stages SET name=?,version=version+1,updated_at=? WHERE id=?",stage.title(),now(),real);count++;}
        }
        for(var item:draft.items()) if(!realItems.containsKey(item.localId())) realItems.put(item.localId(),UUID.randomUUID().toString());
        int order=current.items.stream().mapToInt(row->s.mode==AiDrafts.Mode.TODO?0:integer(row,"sort_order")).max().orElse(-1)+1;
        // Insert all new route nodes before setting any dependency IDs.
        for(var item:draft.items()) {
            var old=originals.get(item.localId());String real=realItems.get(item.localId());
            if(old!=null && old.equals(item)) continue;
            if(old==null) {
                switch(s.mode) {
                    case PLAN -> {
                        db.update("INSERT INTO plan_tasks(id,plan_id,task_date,planned_minutes,category,title,description,acceptance_json,sort_order,version,created_at,updated_at) VALUES (?,?,?,?,'AI规划',?,?,'[]',?,0,?,?)",real,s.targetId,item.dueDate(),item.plannedMinutes(),item.title(),item.note(),order++,now(),now());
                        db.update("INSERT INTO task_progress(id,task_id,status,acceptance_json,version,created_at,updated_at) VALUES (?,?,'PENDING','[]',0,?,?)",UUID.randomUUID().toString(),real,now(),now());
                    }
                    case TODO -> db.update("INSERT INTO todos(id,owner_id,title,note,due_date,version,created_at,updated_at) VALUES (?,?,?,?,?,0,?,?)",real,owner,item.title(),item.note(),item.dueDate(),now(),now());
                    case ROUTE -> db.update("INSERT INTO mainline_items(id,mainline_id,title,description,acceptance_json,planned_minutes,sort_order,status,version,created_at,updated_at) VALUES (?,?,?,?,'[]',?,?,'PENDING',0,?,?)",real,s.targetId,item.title(),item.note(),item.plannedMinutes(),order++,now(),now());
                }
            } else switch(s.mode) {
                case PLAN -> db.update("UPDATE plan_tasks SET title=?,description=?,task_date=?,planned_minutes=?,version=version+1,updated_at=? WHERE id=?",item.title(),item.note(),item.dueDate(),item.plannedMinutes(),now(),real);
                case TODO -> db.update("UPDATE todos SET title=?,note=?,due_date=?,version=version+1,updated_at=? WHERE id=?",item.title(),item.note(),item.dueDate(),now(),real);
                case ROUTE -> db.update("UPDATE mainline_items SET title=?,description=?,planned_minutes=?,version=version+1,updated_at=? WHERE id=?",item.title(),item.note(),item.plannedMinutes(),now(),real);
            }
            count++;
        }
        if(s.mode==AiDrafts.Mode.ROUTE) {
            for(var item:draft.items()) {
                var old=originals.get(item.localId());
                if(old!=null && Objects.equals(old.stageId(),item.stageId()) && old.prerequisites().equals(item.prerequisites())) continue;
                var ids=item.prerequisites().stream().map(realItems::get).toList();
                db.update("UPDATE mainline_items SET stage_id=?,dependency_mode=?,prerequisite_ids_json=? WHERE id=?",item.stageId()==null?null:realStages.get(item.stageId()),ids.isEmpty()?"NONE":"EXPLICIT",json(ids),realItems.get(item.localId()));
            }
            validateWholeRoute(s.targetId);
        }
        boolean rootChanged=!s.original.title().equals(draft.title()) || !Objects.equals(s.original.startDate(),draft.startDate()) || !Objects.equals(s.original.endDate(),draft.endDate()) || !s.original.restDays().equals(draft.restDays());
        if(count==0 && !rootChanged) throw new IllegalArgumentException("没有需要应用的修改。");
        if(s.mode==AiDrafts.Mode.PLAN) db.update("UPDATE plans SET name=?,start_date=?,end_date=?,rest_days_mask=?,version=version+1,updated_at=? WHERE id=?",draft.title(),draft.startDate(),draft.endDate(),restMask(draft.restDays()),now(),s.targetId);
        if(s.mode==AiDrafts.Mode.ROUTE) db.update("UPDATE mainlines SET name=?,version=version+1,updated_at=? WHERE id=?",draft.title(),now(),s.targetId);
        return new AiDraftApplier.Applied(s.mode.name(),path(s),count+(rootChanged?1:0));
    }
    void assertCurrent(String owner,Snapshot s) {
        if(s!=null && !s.fingerprint.equals(fingerprint(load(owner,s.mode,s.targetId))))
            throw new ConflictException("所选对象已经变化，请重新创建调整对话；不会为过期快照发起 AI 调用。");
    }
    private Data load(String owner,AiDrafts.Mode mode,String id) {
        String table=mode==AiDrafts.Mode.PLAN?"plans":mode==AiDrafts.Mode.ROUTE?"mainlines":"todos";
        var roots=rows("SELECT * FROM "+table+" WHERE id=? AND owner_id=?"+(mode==AiDrafts.Mode.TODO?" AND deleted=false":" AND deleted_at IS NULL AND archived=false")+" FOR UPDATE",id,owner);
        if(roots.isEmpty()) throw new NotFoundException("所选对象不存在或不可调整。");
        var root=roots.getFirst();
        if(mode==AiDrafts.Mode.TODO) return new Data(root,List.of(root),List.of(),List.of(),List.of());
        String child=mode==AiDrafts.Mode.PLAN?"plan_tasks":"mainline_items",fk=mode==AiDrafts.Mode.PLAN?"plan_id":"mainline_id";
        var rows=rows("SELECT * FROM "+child+" WHERE "+fk+"=? ORDER BY id LIMIT 1002 FOR UPDATE",id);
        if(rows.size()>1000) throw new IllegalArgumentException("所选对象历史记录过多，请手动拆分后调整。");
        var stages=mode==AiDrafts.Mode.ROUTE?rows("SELECT * FROM mainline_stages WHERE mainline_id=? ORDER BY id FOR UPDATE",id):List.<Map<String,Object>>of();
        var progress=mode==AiDrafts.Mode.PLAN?rows("SELECT p.* FROM task_progress p JOIN plan_tasks t ON t.id=p.task_id WHERE t.plan_id=? ORDER BY p.id FOR UPDATE",id):List.<Map<String,Object>>of();
        var times=mode==AiDrafts.Mode.PLAN?rows("SELECT e.progress_id,COUNT(*) AS entry_count,SUM(e.seconds) AS seconds FROM time_entries e JOIN task_progress p ON p.id=e.progress_id JOIN plan_tasks t ON t.id=p.task_id WHERE t.plan_id=? GROUP BY e.progress_id ORDER BY e.progress_id",id):List.<Map<String,Object>>of();
        return new Data(root,rows,stages,progress,times);
    }
    private void validateWholeRoute(String id) {
        var root=rows("SELECT execution_mode FROM mainlines WHERE id=?",id).getFirst();
        var rows=rows("SELECT * FROM mainline_items WHERE mainline_id=? ORDER BY sort_order",id);
        Map<String,List<String>> graph=new HashMap<>();
        for(var row:rows) {
            var ids="EXPLICIT".equals(text(row,"dependency_mode"))?strings(text(row,"prerequisite_ids_json")):
                "NONE".equals(text(row,"dependency_mode")) || "FLEXIBLE".equals(text(root,"execution_mode"))?List.<String>of():
                rows.stream().filter(r->integer(r,"sort_order")<integer(row,"sort_order")).map(r->text(r,"id")).toList();
            graph.put(text(row,"id"),ids);
        }
        Set<String> visiting=new HashSet<>(),done=new HashSet<>();for(String node:graph.keySet()) visit(node,graph,visiting,done);
    }
    private void visit(String id,Map<String,List<String>> graph,Set<String> visiting,Set<String> done) {
        if(done.contains(id))return;if(!graph.containsKey(id) || !visiting.add(id))throw new IllegalArgumentException("调整会破坏现有或回收站路线依赖，请手动处理。");
        for(String next:graph.get(id))visit(next,graph,visiting,done);visiting.remove(id);done.add(id);
    }
    private String fingerprint(Data data) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(data).getBytes(StandardCharsets.UTF_8)));}catch(Exception invalid){throw new IllegalStateException("无法生成调整快照。");}}
    private List<Map<String,Object>> rows(String sql,Object... args) {
        return db.query(sql,(rs,index)->{
            Map<String,Object> row=new LinkedHashMap<>();var metadata=rs.getMetaData();
            for(int i=1;i<=metadata.getColumnCount();i++) {
                Object value=rs.getObject(i);
                if(value instanceof java.sql.Clob clob) { if(clob.length()>512000) throw new IllegalArgumentException("原对象内容过长，请手动处理。");value=clob.getSubString(1,(int)clob.length()); }
                row.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT),value);
            }
            return row;
        },args);
    }
    private String path(Snapshot s) {return switch(s.mode){case PLAN->"/plans/"+s.targetId;case ROUTE->"/mainlines?line="+s.targetId;case TODO->"/todos";};}
    private String json(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception invalid){throw new IllegalStateException("无法序列化调整内容。");}}
    private List<String> strings(String source) {try{return mapper.readValue(source,new TypeReference<List<String>>(){});}catch(Exception invalid){throw new IllegalArgumentException("现有路线依赖格式异常，请先手动修复。");}}
    private static String local(Map<String,String> ids,String real) {return ids.entrySet().stream().filter(e->e.getValue().equals(real)).map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("现有路线引用了不可用的阶段或步骤，请先手动修复。"));}
    private static String text(Map<String,Object> row,String key) {return row.get(key)==null?"":row.get(key).toString();}
    private static long number(Map<String,Object> row,String key) {return row.get(key)==null?0:((Number)row.get(key)).longValue();}
    private static int integer(Map<String,Object> row,String key) {return (int)number(row,key);}
    private static boolean bool(Map<String,Object> row,String key) {var value=row.get(key);return value instanceof Boolean b?b:value instanceof Number n&&n.intValue()!=0;}
    private static LocalDate date(Map<String,Object> row,String key) {return row.get(key) instanceof java.sql.Date d?d.toLocalDate():LocalDate.parse(text(row,key));}
    private static List<Integer> restDays(int mask) {var result=new ArrayList<Integer>();for(int i=1;i<=7;i++)if((mask&(1<<(i-1)))!=0)result.add(i);return result;}
    private static int restMask(List<Integer> days) {int result=0;for(int day:days)result|=1<<(day-1);return result;}
    private static Timestamp now() {return Timestamp.from(Instant.now());}
}
