package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.common.*;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.oyts.planplatform.reflection.ReflectionService;
import com.oyts.planplatform.reflection.ReflectionDtos;

@Service
public class AiConversationService {
    private final JdbcTemplate db; private final AiQuotaService quotas; private final AiProperties config;
    private final ObjectMapper mapper; private final Validator validator; private final AiDraftApplier applier; private final AiAdjustmentService adjustments; private final ReflectionService reflections;
    private final AiLongPlans longPlans;private final AiRuntimePolicy runtime;
    AiConversationService(JdbcTemplate db,AiQuotaService quotas,AiProperties config,ObjectMapper mapper,Validator validator,AiDraftApplier applier,AiAdjustmentService adjustments,ReflectionService reflections,AiLongPlans longPlans,AiRuntimePolicy runtime) {
        this.db=db;this.quotas=quotas;this.config=config;this.mapper=mapper;this.validator=validator;this.applier=applier;this.adjustments=adjustments;this.reflections=reflections;this.longPlans=longPlans;this.runtime=runtime;
    }
    public record Summary(String id,String mode,long version,Instant createdAt,Instant expiresAt,boolean applied,boolean adjustment,boolean processing) {}
    public enum Intent { AUTO, DIALOGUE, GENERATE, REVISE, OUTLINE, BATCH }
    public record AttemptInfo(int number,String state,String reason,Integer inputChars,Integer outputChars,Long inputTokens,Long outputTokens,Long durationMs) {}
    public record Diagnostic(String requestId,String reason,int attempts,boolean repairing,boolean replyOnly,List<AttemptInfo> details) {}
    public record Turn(String id,String state,String message,String reply,String error,Instant createdAt,Diagnostic diagnostic) {}
    public record Session(String id,String mode,String recommendedMode,long version,AiDrafts.Draft draft,
                          JsonNode applied,Instant expiresAt,List<Turn> turns,AiAdjustmentService.View adjustment,JsonNode review,String requirementsText,AiLongPlans.View longPlan,boolean outlineOnly) {}
    public record Job(String id,String state,String error) {}
    record Work(String id,String owner,String sessionId,String reservation,String context,String mode,Intent intent,AiRuntimePolicy.Limits limits) {}
    AiRuntimePolicy.Policy runtime(){return runtime.current();}
    private record Row(String id,String mode,String recommendedMode,long version,String draft,String applied,Instant expiresAt,String adjustment,String review,String requirements) {}
    boolean available() { return config.enabled() && !config.apiKeyFile().isBlank(); }
    private void lock() { db.queryForObject("SELECT id FROM ai_quota_policy WHERE id=1 FOR UPDATE",Integer.class); }
    private void enabledUser(String owner) { if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND enabled=true",Integer.class,owner)!=1) throw new NotFoundException("账号不存在。"); }
    private Row owned(String owner,String id,boolean locking) {
        enabledUser(owner);
        var rows=db.query("SELECT id,selected_mode,recommended_mode,version,draft_json,applied_json,expires_at,adjustment_json,review_context_json,requirements_text FROM ai_sessions WHERE id=? AND owner_id=? AND expires_at>?"+(locking?" FOR UPDATE":""),
            (r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getTimestamp(7).toInstant(),r.getString(8),r.getString(9),r.getString(10)),id,owner,now());
        if(rows.isEmpty()) throw new NotFoundException("对话不存在或已过期。");return rows.getFirst();
    }
    private void editable(Row row,long version) {
        if(row.version!=version) throw new ConflictException("对话或草稿已变化，请刷新后重试。");
        if(row.applied!=null) throw new ConflictException("此草稿已生成真实内容；如需新规划，请新建对话。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,row.id)>0) throw new ConflictException("AI 正在处理，请等待完成后编辑。");
    }
    @Transactional(readOnly=true)
    public List<Summary> list(String owner) {
        enabledUser(owner);
        return db.query("SELECT s.id,s.selected_mode,s.version,s.created_at,s.expires_at,s.applied_json,s.adjustment_json IS NOT NULL,EXISTS(SELECT 1 FROM ai_jobs j WHERE j.session_id=s.id AND j.state IN ('QUEUED','RUNNING')) FROM ai_sessions s WHERE s.owner_id=? AND s.expires_at>? AND s.review_context_json IS NULL ORDER BY s.created_at DESC,s.id LIMIT 20",
            (r,n)->new Summary(r.getString(1),r.getString(2),r.getLong(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),r.getString(6)!=null,r.getBoolean(7),r.getBoolean(8)),owner,now());
    }
    @Transactional
    public Session create(String owner,AiDrafts.Mode mode,boolean consent) {
        if(!consent) throw new IllegalArgumentException("请先同意将本次对话和草稿发送给 DeepSeek 处理。");
        lock();enabledUser(owner);
        if(db.queryForObject("SELECT COUNT(*) FROM ai_sessions WHERE owner_id=? AND expires_at>?",Integer.class,owner,now())>=20) throw new ConflictException("最多保留 20 个对话，请先删除不需要的对话。");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_sessions(id,owner_id,selected_mode,created_at,expires_at,requirements_text) VALUES (?,?,?,?,?,'')",id,owner,mode==null?null:mode.name(),now(),Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
        return read(owner,id);
    }
    @Transactional
    public Session createAdjustment(String owner,AiDrafts.Mode mode,String target,boolean consent) {
        if(!consent) throw new IllegalArgumentException("请先同意将所选对象的必要内容和本次对话发送给 DeepSeek。");
        var session=create(owner,mode,true);
        var snapshot=adjustments.capture(owner,mode,target);
        // All original IDs stay in the private snapshot; the model only sees local aliases.
        db.update("UPDATE ai_sessions SET adjustment_json=?,draft_json=? WHERE id=?",adjustments.encode(snapshot),snapshot.original().items().isEmpty()?null:write(snapshot.original()),session.id());
        return read(owner,session.id());
    }
    @Transactional(readOnly=true)
    public Session read(String owner,String id) {
        var row=owned(owner,id,false);
        var turns=db.query("SELECT id,state,user_message,reply_text,error_code,created_at FROM ai_jobs WHERE session_id=? ORDER BY created_at,id",
            (r,n)->new Turn(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant(),diagnostic(r.getString(1),r.getString(2),r.getString(4))),id);
        var snapshot=adjustments.decode(row.adjustment);
        return new Session(id,row.mode,row.recommendedMode,row.version,row.draft==null?null:adjustments.parse(snapshot,row.draft),row.applied==null?null:json(row.applied),row.expiresAt,turns,adjustments.view(snapshot),row.review==null?null:json(row.review),requirements(row),longPlans.view(id),Boolean.TRUE.equals(db.queryForObject("SELECT outline_only FROM ai_sessions WHERE id=?",Boolean.class,id)));
    }
    private Diagnostic diagnostic(String job,String state,String reply){
        var reasons=db.query("SELECT reason FROM ai_job_attempts WHERE job_id=? ORDER BY attempt_no DESC",(r,n)->r.getString(1),job);
        String reason=reasons.isEmpty()?null:reasons.getFirst();
        if(reason!=null)try{AiFailure.Reason.valueOf(reason);}catch(IllegalArgumentException invalid){reason=null;}
        var details=db.query("SELECT attempt_no,state,reason,input_chars,output_chars,input_tokens,output_tokens,started_at,completed_at FROM ai_job_attempts WHERE job_id=? ORDER BY attempt_no",(r,n)->new AttemptInfo(r.getInt(1)+1,r.getString(2),r.getString(3),(Integer)r.getObject(4),(Integer)r.getObject(5),(Long)r.getObject(6),(Long)r.getObject(7),r.getTimestamp(9)==null?null:Math.max(0,Duration.between(r.getTimestamp(8).toInstant(),r.getTimestamp(9).toInstant()).toMillis())),job);
        String request=db.queryForObject("SELECT reservation_id FROM ai_jobs WHERE id=?",String.class,job);
        return new Diagnostic(request,reason,reasons.size(),"RUNNING".equals(state)&&reasons.size()>1,"FAILED".equals(state)&&reply!=null,details);
    }
    private String requirements(Row row){
        if(row.requirements!=null)return row.requirements;
        // Old sessions are readable without migration-time copying of private text.
        return String.join("\n",db.query("SELECT user_message FROM ai_jobs WHERE session_id=? ORDER BY created_at,id",(r,n)->r.getString(1),row.id));
    }
    @Transactional public Session saveRequirements(String owner,String id,long version,String text){
        if(text==null||text.length()>8000)throw new IllegalArgumentException("需求笔记最多8000字，请整理后保存。");
        lock();var row=owned(owner,id,true);editable(row,version);analysisOnly(row);
        db.update("UPDATE ai_sessions SET requirements_text=?,version=version+1 WHERE id=?",text,id);return read(owner,id);
    }
    @Transactional
    public Session choose(String owner,String id,long version,AiDrafts.Mode mode) {
        lock();var row=owned(owner,id,true);editable(row,version);
        analysisOnly(row);
        if(row.adjustment!=null) throw new ConflictException("已有对象的调整不能切换模式。");
        if(row.draft!=null && !mode.name().equals(row.mode)) throw new ConflictException("已有草稿不能直接换模式，请新建对话。");
        db.update("UPDATE ai_sessions SET selected_mode=?,version=version+1 WHERE id=?",mode.name(),id);return read(owner,id);
    }
    @Transactional
    public Session saveDraft(String owner,String id,long version,String source) {
        lock();var row=owned(owner,id,true);editable(row,version);
        analysisOnly(row);
        longPlans.assertOrdinaryEdit(id);
        var draft=adjustments.parse(adjustments.decode(row.adjustment),source);
        if(row.mode==null || !draft.mode().name().equals(row.mode)) throw new IllegalArgumentException("请先选择草稿对应的模式。");
        draft=revision(draft,row.version+1);
        db.update("UPDATE ai_sessions SET draft_json=?,version=version+1 WHERE id=?",write(draft),id);return read(owner,id);
    }
    @Transactional
    public JsonNode apply(String owner,String id,long version,boolean confirmed) {
        if(!confirmed) throw new IllegalArgumentException("请先确认预览内容。");
        lock();var row=owned(owner,id,true);
        analysisOnly(row);
        // Replay returns the same result, including after a lost response. Never create twice.
        if(row.applied!=null) return json(row.applied);
        editable(row,version);
        if(row.draft==null || row.mode==null) throw new IllegalArgumentException("请先生成并保存草稿。");
        longPlans.assertOrdinaryEdit(id);
        if(Boolean.TRUE.equals(db.queryForObject("SELECT outline_only FROM ai_sessions WHERE id=?",Boolean.class,id)) && (longPlans.view(id)==null||!longPlans.view(id).complete()))throw new ConflictException("这只是大纲，请核对覆盖范围并完成所有细化批次后再生成真实内容。");
        var snapshot=adjustments.decode(row.adjustment);
        var draft=adjustments.parse(snapshot,row.draft);
        if(!draft.mode().name().equals(row.mode)) throw new ConflictException("草稿模式已变化。");
        var result=snapshot==null?applier.apply(owner,draft):adjustments.apply(owner,snapshot,draft);
        String encoded=write(result);db.update("UPDATE ai_sessions SET applied_json=?,version=version+1 WHERE id=?",encoded,id);
        return json(encoded);
    }
    @Transactional public Session startLongPlan(String owner,String id,long version,String coverage,boolean confirmed){
        lock();var row=owned(owner,id,true);editable(row,version);analysisOnly(row);
        if(row.draft==null)throw new IllegalArgumentException("请先生成或导入完整大纲，再逐项核对日期、阶段和依赖。");
        var snapshot=adjustments.decode(row.adjustment);adjustments.assertCurrent(owner,snapshot);
        var draft=adjustments.parse(snapshot,row.draft);
        longPlans.start(id,draft,snapshot==null?Set.of():snapshot.protectedItems(),coverage,confirmed,row.expiresAt);
        db.update("UPDATE ai_sessions SET version=version+1 WHERE id=?",id);return read(owner,id);
    }
    @Transactional public Session saveBatch(String owner,String id,long version,String source){
        lock();var row=owned(owner,id,true);editable(row,version);analysisOnly(row);
        var snapshot=adjustments.decode(row.adjustment);adjustments.assertCurrent(owner,snapshot);
        var merged=longPlans.merge(id,null,adjustments.parse(snapshot,row.draft),json(source),adjustments,snapshot);
        longPlans.complete(id);db.update("UPDATE ai_sessions SET draft_json=?,version=version+1 WHERE id=?",write(revision(merged,row.version+1)),id);return read(owner,id);
    }
    /** External collaboration services join this same lock before read-only preflight or mutation. */
    Session verifyEditable(String owner,String id,long version){lock();var row=owned(owner,id,true);editable(row,version);analysisOnly(row);longPlans.assertOrdinaryEdit(id);return read(owner,id);}
    AiDrafts.Draft validateExternal(String owner,String id,String source){var row=owned(owner,id,false);var snapshot=adjustments.decode(row.adjustment);adjustments.assertCurrent(owner,snapshot);return adjustments.parse(snapshot,source);}
    boolean unfinishedOutline(String owner,String id){if(id==null)return false;var rows=db.query("SELECT outline_only FROM ai_sessions WHERE id=? AND owner_id=?",(r,n)->r.getBoolean(1),id,owner);var flow=longPlans.view(id);return !rows.isEmpty()&&(rows.getFirst()||flow!=null)&& (flow==null||!flow.complete());}
    @Transactional
    public void delete(String owner,String id,long version) {
        lock();var row=owned(owner,id,true);
        if(row.version!=version) throw new ConflictException("对话已变化，请刷新后删除。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,id)>0) throw new ConflictException("请求正在处理，暂不能删除。");
        db.update("DELETE FROM ai_sessions WHERE id=?",id);
    }
    @Transactional
    public Job enqueue(String owner,String id,long version,String operation,String message) {
        return enqueue(owner,id,version,operation,message,Intent.AUTO);
    }
    @Transactional
    public Job enqueue(String owner,String id,long version,String operation,String message,Intent requestedIntent) {
        Intent intent=requestedIntent==null?Intent.AUTO:requestedIntent;
        if(!available()) throw new AiFailure(AiFailure.Code.DISABLED,false);
        if(message==null || message.isBlank() || message.length()>8000) throw new IllegalArgumentException("请缩短消息后重试。");
        if(operation==null || !operation.matches("[A-Za-z0-9._:-]{8,100}")) throw new IllegalArgumentException("请求标识无效。");
        lock();var policy=runtime.current();var limits=policy.limits();var row=owned(owner,id,true);String fingerprint=hash(id+"\n"+version+"\n"+message+(intent==Intent.AUTO?"":"\n"+intent.name()));
        var prior=db.query("SELECT id,state,error_code,fingerprint FROM ai_jobs WHERE owner_id=? AND operation_key=?",
            (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4)},owner,operation);
        if(!prior.isEmpty()) { var first=prior.getFirst();if(!fingerprint.equals(first[3])) throw new ConflictException("同一请求标识不能用于不同内容。");return new Job(first[0],first[1],first[2]); }
        editable(row,version);
        if(intent!=Intent.DIALOGUE&&intent!=Intent.BATCH)longPlans.assertOrdinaryEdit(id);
        if(intent==Intent.OUTLINE&&longPlans.view(id)!=null)throw new ConflictException("已有分批流程不能重新生成大纲，请保留已完成批次。");
        if(row.review!=null && intent!=Intent.AUTO && intent!=Intent.DIALOGUE)throw new IllegalArgumentException("复盘分析不能生成草稿。");
        if((intent==Intent.GENERATE||intent==Intent.REVISE||intent==Intent.OUTLINE||intent==Intent.BATCH) && row.mode==null)throw new IllegalArgumentException("请先选择规划模式。");
        if(intent==Intent.REVISE && row.draft==null)throw new IllegalArgumentException("请先准备草稿再修改。");
        // AUTO is the old-client protocol: do not introduce a note-editor requirement there.
        String notes=intent==Intent.AUTO?"":requirements(row),nextNotes=notes.isBlank()?message:notes+"\n"+message;
        if(intent!=Intent.AUTO && row.review==null && nextNotes.length()>8000)throw new IllegalArgumentException("需求笔记已满，请先整理并保存，尚未调用AI或扣次数。");
        adjustments.assertCurrent(owner,adjustments.decode(row.adjustment));
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE owner_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,owner)>0) throw new TooManyRequestsException("你已有一个 AI 请求正在处理，请在最近对话中等待结果。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=?",Integer.class,id)>=30) throw new ConflictException("本次对话已达 30 轮，请保存草稿或新建对话。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING')",Integer.class)>=limits.maxConcurrent()+limits.queueCapacity()) throw new AiFailure(AiFailure.Code.BUSY,false);
        String context=intent==Intent.BATCH?longPlans.context(id,adjustments.parse(adjustments.decode(row.adjustment),row.draft),notes,message):context(row,message,intent,notes,limits.maxInputChars());
        if(context.length()>limits.maxInputChars())throw new IllegalArgumentException("本批内容和需求超过AI上下文上限，请缩短笔记或手动细化；尚未调用或扣次数。");
        var reservation=quotas.reserve(owner,operation,fingerprint);
        if(!reservation.acquired()) throw new ConflictException("该请求已经处理，不能重复调用模型。");
        String job=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_jobs(id,session_id,owner_id,operation_key,fingerprint,reservation_id,state,user_message,context_json,created_at,updated_at,request_intent) VALUES (?,?,?,?,?,?,'QUEUED',?,?,?,?,?)",job,id,owner,operation,fingerprint,reservation.id(),message,context,now(),now(),intent.name());
        db.update("UPDATE ai_jobs SET runtime_input_chars=?,runtime_output_tokens=?,runtime_total_output_tokens=?,runtime_timeout_seconds=?,runtime_version=? WHERE id=?",limits.maxInputChars(),limits.maxOutputTokens(),limits.totalOutputTokens(),limits.timeoutSeconds(),policy.version(),job);
        if(intent==Intent.BATCH)longPlans.reserve(id,job);
        db.update("UPDATE ai_sessions SET requirements_text=?,version=version+1 WHERE id=?",intent==Intent.AUTO?row.requirements:row.review==null?nextNotes:notes,id);return new Job(job,"QUEUED",null);
    }
    private String context(Row row,String message,Intent intent,String notes,int maxInputChars) {
        var history=db.query("SELECT user_message,reply_text FROM ai_jobs WHERE session_id=? AND state='SUCCEEDED' ORDER BY created_at DESC,id DESC LIMIT 6",(r,n)->Map.of("user",r.getString(1),"assistant",r.getString(2)),row.id);
        var turns=new ArrayList<>(history);Collections.reverse(turns);
        Map<String,Object> context=new LinkedHashMap<>();context.put("today",LocalDate.now(AiQuotaService.ZONE).toString());context.put("selectedMode",row.mode);context.put("currentDraft",row.draft==null?null:json(row.draft));context.put("history",turns);context.put("message",message);
        context.put("requestIntent",intent.name());context.put("requirements",notes);
        context.put("conversationStartedOn",db.queryForObject("SELECT created_at FROM ai_sessions WHERE id=?",Timestamp.class,row.id).toInstant().atZone(AiQuotaService.ZONE).toLocalDate().toString());
        var snapshot=adjustments.decode(row.adjustment);
        if(snapshot!=null) {
            context.put("intent","ADJUST_EXISTING");context.put("existingItemIds",snapshot.items().keySet());context.put("existingStageIds",snapshot.stages().keySet());context.put("protectedItemIds",snapshot.protectedItems());
            if(row.draft==null) context.put("currentDraft",snapshot.original());
        }
        if(row.review!=null){context.put("intent","REVIEW_ANALYSIS");context.put("review",json(row.review));}
        String result=write(context);
        while(result.length()>maxInputChars && !turns.isEmpty()) { turns.removeFirst();result=write(context); }
        if(result.length()>maxInputChars) throw new IllegalArgumentException("消息和草稿超过当前 AI 上下文限制，请手动编辑或缩短内容后重试。");
        return result;
    }
    @Transactional
    public Work claim() {
        lock();
        if(!available() || db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='RUNNING'",Integer.class)>=runtime.current().limits().maxConcurrent()) return null;
        var ids=db.query("SELECT id FROM ai_jobs WHERE state='QUEUED' ORDER BY created_at,id LIMIT 1",(r,n)->r.getString(1));if(ids.isEmpty()) return null;
        var id=ids.getFirst();var policy=runtime.current();var limits=policy.limits();
        db.update("UPDATE ai_jobs SET runtime_input_chars=?,runtime_output_tokens=?,runtime_total_output_tokens=?,runtime_timeout_seconds=?,runtime_version=? WHERE id=? AND runtime_version IS NULL",limits.maxInputChars(),limits.maxOutputTokens(),limits.totalOutputTokens(),limits.timeoutSeconds(),policy.version(),id);
        var work=work(id);
        db.update("UPDATE ai_jobs SET state='RUNNING',updated_at=? WHERE id=?",now(),work.id);return work;
    }
    boolean runnable(Work work){return work.intent!=Intent.BATCH||longPlans.runnable(work.sessionId);}
    private Work work(String id) {var fallback=runtime.current().limits();return db.queryForObject("SELECT j.id,j.owner_id,j.session_id,j.reservation_id,j.context_json,s.selected_mode,j.request_intent,j.runtime_input_chars,j.runtime_output_tokens,j.runtime_total_output_tokens,j.runtime_timeout_seconds FROM ai_jobs j JOIN ai_sessions s ON s.id=j.session_id WHERE j.id=?",(r,n)->new Work(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),Intent.valueOf(r.getString(7)),r.getObject(8)==null?fallback:new AiRuntimePolicy.Limits(fallback.maxConcurrent(),fallback.queueCapacity(),r.getInt(8),r.getInt(9),r.getInt(10),r.getInt(11))),id); }
    @Transactional
    public void succeed(Work work,AiProvider.Result result) {
        // Untrusted JSON cannot escape into entities; validation never makes network calls.
        JsonNode output=json(result.text());
        if(output==null || !output.isObject()) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.ENVELOPE_INVALID,null);
        Set<String> fields=new HashSet<>();output.fieldNames().forEachRemaining(fields::add);
        if(!fields.equals(Set.of("message","recommendedMode","draft")) || !output.path("message").isTextual() || output.path("message").asText().isBlank() || output.path("message").asText().length()>4000) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        String recommendation=output.path("recommendedMode").asText();
        if(output.path("recommendedMode").isNull())recommendation=null;
        else try { AiDrafts.Mode.valueOf(recommendation); } catch(Exception invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
        lock();var row=owned(work.owner,work.sessionId,true);
        AiDrafts.Draft draft;
        try { draft=work.intent==Intent.BATCH?longPlans.merge(row.id,work.id,adjustments.parse(adjustments.decode(row.adjustment),row.draft),output.get("draft"),adjustments,adjustments.decode(row.adjustment)):work.intent==Intent.DIALOGUE || output.path("draft").isNull()?null:adjustments.parse(adjustments.decode(row.adjustment),write(output.get("draft"))); }
        catch(IllegalArgumentException | AiFailure invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.DRAFT_INVALID,null); }
        if(row.review!=null&&draft!=null)throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        if(draft!=null && (work.mode==null || !draft.mode().name().equals(work.mode))) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.MODE_MISMATCH,null);
        if(!"RUNNING".equals(db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,work.id))) return;
        if(work.intent==Intent.BATCH)longPlans.complete(row.id);
        db.update("UPDATE ai_sessions SET recommended_mode=?,draft_json=?,version=version+1 WHERE id=?",recommendation,draft==null?row.draft:write(revision(draft,row.version+1)),row.id);
        if(draft!=null && work.intent!=Intent.BATCH)db.update("UPDATE ai_sessions SET outline_only=? WHERE id=?",work.intent==Intent.OUTLINE,row.id);
        db.update("UPDATE ai_jobs SET state='SUCCEEDED',context_json=NULL,reply_text=?,input_tokens=?,output_tokens=?,updated_at=? WHERE id=?",output.path("message").asText(),result.inputTokens(),result.outputTokens(),now(),work.id);
        usageMetadata(work,"SUCCEEDED",null,result.inputTokens(),result.outputTokens());
        quotas.settle(work.owner,work.reservation,"SUCCEEDED");
    }
    @Transactional public void retainReadableReply(Work work,AiProvider.Result result){
        if(result==null||result.text()==null||result.text().length()>128000)return;
        JsonNode value;
        try{value=json(AiOutputText.unwrap(result.text()));}catch(AiFailure invalid){return;}
        if(!value.isObject()||!value.path("message").isTextual()||value.path("message").asText().isBlank()||value.path("message").asText().length()>4000)return;
        // Text is displayed escaped as untrusted prose, never interpreted as a validated draft.
        db.update("UPDATE ai_jobs SET reply_text=? WHERE id=? AND owner_id=? AND state='RUNNING'",value.path("message").asText(),work.id,work.owner);
    }
    @Transactional
    public void fail(Work work,AiFailure failure) {
        lock();String state=db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,work.id);
        if(!"RUNNING".equals(state) && !"QUEUED".equals(state)) return;
        db.update("UPDATE ai_jobs SET state=?,context_json=NULL,error_code=?,updated_at=? WHERE id=?",failure.uncertain()?"UNKNOWN":"FAILED",failure.code().name(),now(),work.id);
        db.update("UPDATE ai_sessions SET version=version+1 WHERE id=?",work.sessionId);
        usageMetadata(work,failure.uncertain()?"UNKNOWN":"FAILED",failure.code().name(),null,null);
        quotas.settle(work.owner,work.reservation,failure.uncertain()?"UNKNOWN":"RELEASED");
    }
    private void usageMetadata(Work work,String state,String code,Long input,Long output) {
        var attempts=db.queryForMap("SELECT COUNT(*) AS n,COUNT(input_tokens) AS known,COALESCE(SUM(input_tokens),0) AS ins,COALESCE(SUM(output_tokens),0) AS outs FROM ai_job_attempts WHERE job_id=?",work.id);
        boolean complete=input!=null && output!=null;
        if(((Number)attempts.get("n")).longValue()>0){
            complete=((Number)attempts.get("known")).longValue()==((Number)attempts.get("n")).longValue();
            input=((Number)attempts.get("known")).longValue()>0?((Number)attempts.get("ins")).longValue():null;
            output=input==null?null:((Number)attempts.get("outs")).longValue();
        }
        var created=db.queryForObject("SELECT created_at FROM ai_jobs WHERE id=?",Timestamp.class,work.id).toInstant();
        var completed=Instant.now();
        db.update("UPDATE ai_quota_requests SET terminal_state=?,failure_code=?,input_tokens=?,output_tokens=?,token_usage_complete=?,completed_at=?,duration_ms=? WHERE id=? AND owner_id=?",
            state,code,input,output,complete,Timestamp.from(completed),Math.max(0,Duration.between(created,completed).toMillis()),work.reservation,work.owner);
    }
    @Transactional
    public void maintain() {
        lock();
        var stale=db.query("SELECT id,updated_at,runtime_timeout_seconds FROM ai_jobs WHERE state='RUNNING'",(r,n)->new Object[]{r.getString(1),r.getTimestamp(2).toInstant(),r.getObject(3)}).stream().filter(r->((Instant)r[1]).isBefore(Instant.now().minusSeconds((r[2]==null?120:((Number)r[2]).longValue())+30))).map(r->(String)r[0]).toList();
        for(String id:stale) fail(work(id),new AiFailure(AiFailure.Code.TIMEOUT,true));
        if(!available()) for(String id:db.query("SELECT id FROM ai_jobs WHERE state='QUEUED'",(r,n)->r.getString(1))) fail(work(id),new AiFailure(AiFailure.Code.DISABLED,false));
        db.update("DELETE FROM ai_sessions WHERE expires_at<? AND id NOT IN (SELECT session_id FROM ai_jobs WHERE state IN ('QUEUED','RUNNING'))",now());
        // Bounded cleanup. Expired/deleted conversations cannot be replayed; current quota is untouched.
        var expired=db.query("SELECT id FROM ai_quota_requests WHERE quota_day<? AND id NOT IN (SELECT reservation_id FROM ai_jobs) LIMIT 200",
            (r,n)->r.getString(1),LocalDate.now(AiQuotaService.ZONE).minusDays(90));
        for(String id:expired) db.update("DELETE FROM ai_quota_requests WHERE id=?",id);
    }
    private static AiDrafts.Draft revision(AiDrafts.Draft d,long revision) { return new AiDrafts.Draft(1,d.mode(),revision,d.title(),d.startDate(),d.endDate(),d.restDays(),d.stages(),d.items()); }
    private void analysisOnly(Row row){if(row.review!=null)throw new ConflictException("复盘分析只提供建议，不能从此会话生成或修改计划。周节奏调整须另开已有对象调整并重新预览确认。");}
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ) public Session createReview(String owner,ReflectionDtos.Analyse r){
        if(!r.consent())throw new IllegalArgumentException("请先核对预览并同意将本次选中的汇总和文字发送给DeepSeek。");
        lock();var preview=reflections.preview(owner,r.kind(),r.start(),r.includeNotes(),r.relatedMode(),r.relatedId());
        if(!MessageDigest.isEqual(preview.fingerprint().getBytes(StandardCharsets.US_ASCII),r.fingerprint().getBytes(StandardCharsets.US_ASCII)))throw new ConflictException("复盘数据已变化，请重新预览后再同意；尚未调用AI或扣次数。");
        var session=create(owner,null,true);db.update("update ai_sessions set review_context_json=? where id=?",preview.contentJson(),session.id());return read(owner,session.id());
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> reviewList(String owner){
        enabledUser(owner);return db.query("select id,version,created_at,expires_at,review_context_json from ai_sessions where owner_id=? and review_context_json is not null and expires_at>? order by created_at desc,id limit 20",(r,n)->{
            JsonNode review=json(r.getString(5));return Map.<String,Object>of("id",r.getString(1),"version",r.getLong(2),"createdAt",r.getTimestamp(3).toInstant(),"expiresAt",r.getTimestamp(4).toInstant(),"kind",review.path("kind").asText(),"start",review.path("facts").path("start").asText());},owner,now());
    }
    private JsonNode json(String source) { try { return mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION,com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(source); } catch(Exception invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); } }
    private String write(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception invalid){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);} }
    private static String hash(String source) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable");} }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
