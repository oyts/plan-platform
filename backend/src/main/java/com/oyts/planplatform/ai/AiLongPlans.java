package com.oyts.planplatform.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.oyts.planplatform.common.ConflictException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Joins the conversation transaction/lock. Never schedules a request by itself. */
@Service
class AiLongPlans {
    static final int MAX_SUBMISSIONS=12, MAX_CALLS=24, OUTPUT_BUDGET=98304, INPUT_BUDGET=1536000;
    private final JdbcTemplate db; private final ObjectMapper mapper;
    AiLongPlans(JdbcTemplate db,ObjectMapper mapper){this.db=db;this.mapper=mapper;}
    record Batch(int number,List<String> itemIds,boolean complete,String lastState) {}
    record View(String coverage,List<Batch> batches,int submissions,int maxSubmissions,int reservedCalls,
                int maxCalls,int reservedInputTokens,int maxInputTokens,int reservedOutputTokens,int maxOutputTokens,int reservedComputeSeconds,int maxComputeSeconds,Instant expiresAt,boolean complete) {}
    private record Row(String coverage,String outline,String batches,String completed,int submissions,Instant expires) {}
    private Row row(String id){var rows=db.query("SELECT coverage_text,outline_json,batches_json,completed_json,submissions,expires_at FROM ai_long_plans WHERE session_id=?",(r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getInt(5),r.getTimestamp(6).toInstant()),id);return rows.isEmpty()?null:rows.getFirst();}
    View view(String id){
        var row=row(id);if(row==null)return null;var groups=groups(row);var done=done(row);var batches=new ArrayList<Batch>();
        for(int i=0;i<groups.size();i++){
            var states=db.query("SELECT j.state FROM ai_long_jobs l JOIN ai_jobs j ON j.id=l.job_id WHERE l.session_id=? AND l.batch_no=? ORDER BY j.created_at DESC,j.id DESC LIMIT 1",(r,n)->r.getString(1),id,i);
            batches.add(new Batch(i+1,groups.get(i),done.contains(i),states.isEmpty()?null:states.getFirst()));
        }
        return new View(row.coverage,batches,row.submissions,MAX_SUBMISSIONS,row.submissions*2,MAX_CALLS,row.submissions*128000,INPUT_BUDGET,row.submissions*8192,OUTPUT_BUDGET,row.submissions*120,1440,row.expires,done.size()==groups.size());
    }
    void start(String id,AiDrafts.Draft outline,Set<String> protectedIds,String coverage,boolean confirmed,Instant expiry){
        if(!confirmed||coverage==null||coverage.isBlank()||coverage.length()>1000)throw new IllegalArgumentException("请核对完整大纲并填写覆盖范围（最多1000字）。");
        if(row(id)!=null)throw new ConflictException("此对话已有分批流程，不能重置预算或覆盖已完成批次。");
        var ids=outline.items().stream().map(AiDrafts.Item::localId).filter(v->!protectedIds.contains(v)).toList();
        if(ids.isEmpty())throw new IllegalArgumentException("全部事项已有执行历史，无可细化的未开始事项。");
        var groups=new ArrayList<List<String>>();for(int i=0;i<ids.size();i+=10)groups.add(ids.subList(i,Math.min(i+10,ids.size())));
        db.update("INSERT INTO ai_long_plans(session_id,coverage_text,outline_json,batches_json,completed_json,created_at,expires_at) VALUES (?,?,?,?,'[]',?,?)",
            id,coverage,json(outline),json(groups),Timestamp.from(Instant.now()),Timestamp.from(expiry.isBefore(Instant.now().plus(Duration.ofDays(7)))?expiry:Instant.now().plus(Duration.ofDays(7))));
    }
    void assertOrdinaryEdit(String id){if(row(id)!=null&&!view(id).complete())throw new ConflictException("请先完成分批细化；整份草稿未完成前不能整体改写或应用。已完成内容仍保留。");}
    private int next(Row row){var done=done(row);for(int i=0;i<groups(row).size();i++)if(!done.contains(i))return i;throw new ConflictException("所有批次已完成，请核对整份草稿。");}
    private Row active(String id){var row=row(id);if(row==null)throw new IllegalArgumentException("请先核对大纲并开启分批细化。");if(!row.expires.isAfter(Instant.now()))throw new ConflictException("分批流程已到期，已完成草稿仍保留，可导出作为新规划参考。");return row;}
    String context(String id,AiDrafts.Draft current,String requirements,String message){
        var row=active(id);int index=next(row);
        if(row.submissions>=MAX_SUBMISSIONS)throw new ConflictException("已到本流程总调用预算，不再调用AI；可手动补齐剩余批次。");
        var selected=groups(row).get(index);var partial=new AiDrafts.Draft(1,current.mode(),current.revision(),current.title(),current.startDate(),current.endDate(),current.restDays(),current.stages(),current.items().stream().filter(i->selected.contains(i.localId())).toList());
        return json(Map.ofEntries(Map.entry("intent","REFINE_BATCH"),Map.entry("requestIntent","BATCH"),Map.entry("selectedMode",current.mode()),Map.entry("coverage",row.coverage),Map.entry("batchNumber",index+1),Map.entry("batchCount",groups(row).size()),Map.entry("requirements",requirements),Map.entry("message",message),Map.entry("currentDraft",partial),Map.entry("allItemIds",current.items().stream().map(AiDrafts.Item::localId).toList())));
    }
    void reserve(String session,String job){var row=active(session);int index=next(row);if(row.submissions>=MAX_SUBMISSIONS)throw new ConflictException("分批总预算已用尽。");db.update("UPDATE ai_long_plans SET submissions=submissions+1 WHERE session_id=?",session);db.update("INSERT INTO ai_long_jobs(job_id,session_id,batch_no) VALUES (?,?,?)",job,session,index);}
    boolean runnable(String session){var row=row(session);return row!=null&&row.expires.isAfter(Instant.now());}
    /** Partial model result is never parsed/applied on its own. Merge then validate the entire graph. */
    AiDrafts.Draft merge(String session,String job,AiDrafts.Draft current,JsonNode output,AiAdjustmentService adjustments,AiAdjustmentService.Snapshot snapshot){
        var row=active(session);int index=next(row);
        if(job!=null){var batch=db.queryForObject("SELECT batch_no FROM ai_long_jobs WHERE job_id=? AND session_id=?",Integer.class,job,session);if(batch==null||batch!=index)throw new ConflictException("批次已经变化，原草稿保留。");}
        if(output==null||!output.isObject())throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        ObjectNode expected=mapper.valueToTree(current);expected.set("items",output.path("items"));
        var candidate=output.deepCopy();((ObjectNode)candidate).put("revision",current.revision());
        if(!expected.equals(candidate)||!output.path("items").isArray())throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        var selected=groups(row).get(index);var replacement=new LinkedHashMap<String,JsonNode>();
        for(var item:output.path("items")){String key=item.path("localId").asText();if(replacement.put(key,item)!=null)throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);}
        if(!new ArrayList<>(replacement.keySet()).equals(selected))throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        var merged=mapper.valueToTree(current);var items=mapper.createArrayNode();
        for(var old:current.items()){
            var item=replacement.get(old.localId());
            if(item==null){items.add(mapper.valueToTree(old));continue;}
            JsonNode original=mapper.valueToTree(old);
            for(String field:List.of("localId","dueDate","stageId","prerequisites"))if(!Objects.equals(original.get(field),item.get(field)))throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
            items.add(item);
        }
        ((ObjectNode)merged).set("items",items);return adjustments.parse(snapshot,json(merged));
    }
    void complete(String session){var row=active(session);int index=next(row);var done=new ArrayList<>(done(row));done.add(index);db.update("UPDATE ai_long_plans SET completed_json=? WHERE session_id=?",json(done),session);}
    private List<List<String>> groups(Row row){try{return mapper.readValue(row.batches,new TypeReference<>(){});}catch(Exception invalid){throw new IllegalStateException("分批记录无法读取。");}}
    private List<Integer> done(Row row){try{return mapper.readValue(row.completed,new TypeReference<>(){});}catch(Exception invalid){throw new IllegalStateException("分批记录无法读取。");}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception invalid){throw new IllegalArgumentException("分批内容无法编码。");}}
}
