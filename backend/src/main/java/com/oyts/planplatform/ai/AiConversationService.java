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

@Service
public class AiConversationService {
    private final JdbcTemplate db; private final AiQuotaService quotas; private final AiProperties config;
    private final ObjectMapper mapper; private final Validator validator; private final AiDraftApplier applier; private final AiAdjustmentService adjustments;
    AiConversationService(JdbcTemplate db,AiQuotaService quotas,AiProperties config,ObjectMapper mapper,Validator validator,AiDraftApplier applier,AiAdjustmentService adjustments) {
        this.db=db;this.quotas=quotas;this.config=config;this.mapper=mapper;this.validator=validator;this.applier=applier;this.adjustments=adjustments;
    }
    public record Summary(String id,String mode,long version,Instant createdAt,Instant expiresAt,boolean applied,boolean adjustment,boolean processing) {}
    public record Turn(String id,String state,String message,String reply,String error,Instant createdAt) {}
    public record Session(String id,String mode,String recommendedMode,long version,AiDrafts.Draft draft,
                          JsonNode applied,Instant expiresAt,List<Turn> turns,AiAdjustmentService.View adjustment) {}
    public record Job(String id,String state,String error) {}
    record Work(String id,String owner,String sessionId,String reservation,String context,String mode) {}
    private record Row(String id,String mode,String recommendedMode,long version,String draft,String applied,Instant expiresAt,String adjustment) {}
    boolean available() { return config.enabled() && !config.apiKeyFile().isBlank(); }
    private void lock() { db.queryForObject("SELECT id FROM ai_quota_policy WHERE id=1 FOR UPDATE",Integer.class); }
    private void enabledUser(String owner) { if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND enabled=true",Integer.class,owner)!=1) throw new NotFoundException("账号不存在。"); }
    private Row owned(String owner,String id,boolean locking) {
        enabledUser(owner);
        var rows=db.query("SELECT id,selected_mode,recommended_mode,version,draft_json,applied_json,expires_at,adjustment_json FROM ai_sessions WHERE id=? AND owner_id=? AND expires_at>?"+(locking?" FOR UPDATE":""),
            (r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getTimestamp(7).toInstant(),r.getString(8)),id,owner,now());
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
        return db.query("SELECT s.id,s.selected_mode,s.version,s.created_at,s.expires_at,s.applied_json,s.adjustment_json IS NOT NULL,EXISTS(SELECT 1 FROM ai_jobs j WHERE j.session_id=s.id AND j.state IN ('QUEUED','RUNNING')) FROM ai_sessions s WHERE s.owner_id=? AND s.expires_at>? ORDER BY s.created_at DESC,s.id LIMIT 20",
            (r,n)->new Summary(r.getString(1),r.getString(2),r.getLong(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),r.getString(6)!=null,r.getBoolean(7),r.getBoolean(8)),owner,now());
    }
    @Transactional
    public Session create(String owner,AiDrafts.Mode mode,boolean consent) {
        if(!consent) throw new IllegalArgumentException("请先同意将本次对话和草稿发送给 DeepSeek 处理。");
        lock();enabledUser(owner);
        if(db.queryForObject("SELECT COUNT(*) FROM ai_sessions WHERE owner_id=? AND expires_at>?",Integer.class,owner,now())>=20) throw new ConflictException("最多保留 20 个对话，请先删除不需要的对话。");
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_sessions(id,owner_id,selected_mode,created_at,expires_at) VALUES (?,?,?,?,?)",id,owner,mode==null?null:mode.name(),now(),Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
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
            (r,n)->new Turn(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getTimestamp(6).toInstant()),id);
        var snapshot=adjustments.decode(row.adjustment);
        return new Session(id,row.mode,row.recommendedMode,row.version,row.draft==null?null:adjustments.parse(snapshot,row.draft),row.applied==null?null:json(row.applied),row.expiresAt,turns,adjustments.view(snapshot));
    }
    @Transactional
    public Session choose(String owner,String id,long version,AiDrafts.Mode mode) {
        lock();var row=owned(owner,id,true);editable(row,version);
        if(row.adjustment!=null) throw new ConflictException("已有对象的调整不能切换模式。");
        if(row.draft!=null && !mode.name().equals(row.mode)) throw new ConflictException("已有草稿不能直接换模式，请新建对话。");
        db.update("UPDATE ai_sessions SET selected_mode=?,version=version+1 WHERE id=?",mode.name(),id);return read(owner,id);
    }
    @Transactional
    public Session saveDraft(String owner,String id,long version,String source) {
        lock();var row=owned(owner,id,true);editable(row,version);
        var draft=adjustments.parse(adjustments.decode(row.adjustment),source);
        if(row.mode==null || !draft.mode().name().equals(row.mode)) throw new IllegalArgumentException("请先选择草稿对应的模式。");
        draft=revision(draft,row.version+1);
        db.update("UPDATE ai_sessions SET draft_json=?,version=version+1 WHERE id=?",write(draft),id);return read(owner,id);
    }
    @Transactional
    public JsonNode apply(String owner,String id,long version,boolean confirmed) {
        if(!confirmed) throw new IllegalArgumentException("请先确认预览内容。");
        lock();var row=owned(owner,id,true);
        // Replay returns the same result, including after a lost response. Never create twice.
        if(row.applied!=null) return json(row.applied);
        editable(row,version);
        if(row.draft==null || row.mode==null) throw new IllegalArgumentException("请先生成并保存草稿。");
        var snapshot=adjustments.decode(row.adjustment);
        var draft=adjustments.parse(snapshot,row.draft);
        if(!draft.mode().name().equals(row.mode)) throw new ConflictException("草稿模式已变化。");
        var result=snapshot==null?applier.apply(owner,draft):adjustments.apply(owner,snapshot,draft);
        String encoded=write(result);db.update("UPDATE ai_sessions SET applied_json=?,version=version+1 WHERE id=?",encoded,id);
        return json(encoded);
    }
    @Transactional
    public void delete(String owner,String id,long version) {
        lock();var row=owned(owner,id,true);
        if(row.version!=version) throw new ConflictException("对话已变化，请刷新后删除。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,id)>0) throw new ConflictException("请求正在处理，暂不能删除。");
        db.update("DELETE FROM ai_sessions WHERE id=?",id);
    }
    @Transactional
    public Job enqueue(String owner,String id,long version,String operation,String message) {
        if(!available()) throw new AiFailure(AiFailure.Code.DISABLED,false);
        if(message==null || message.isBlank() || message.length()>Math.min(config.maxInputChars(),8000)) throw new IllegalArgumentException("请缩短消息后重试。");
        if(operation==null || !operation.matches("[A-Za-z0-9._:-]{8,100}")) throw new IllegalArgumentException("请求标识无效。");
        lock();var row=owned(owner,id,true);String fingerprint=hash(id+"\n"+version+"\n"+message);
        var prior=db.query("SELECT id,state,error_code,fingerprint FROM ai_jobs WHERE owner_id=? AND operation_key=?",
            (r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3),r.getString(4)},owner,operation);
        if(!prior.isEmpty()) { var first=prior.getFirst();if(!fingerprint.equals(first[3])) throw new ConflictException("同一请求标识不能用于不同内容。");return new Job(first[0],first[1],first[2]); }
        editable(row,version);
        adjustments.assertCurrent(owner,adjustments.decode(row.adjustment));
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE owner_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,owner)>0) throw new TooManyRequestsException("你已有一个 AI 请求正在处理，请在最近对话中等待结果。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=?",Integer.class,id)>=30) throw new ConflictException("本次对话已达 30 轮，请保存草稿或新建对话。");
        if(db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state IN ('QUEUED','RUNNING')",Integer.class)>=config.maxConcurrent()+config.queueCapacity()) throw new AiFailure(AiFailure.Code.BUSY,false);
        String context=context(row,message);
        var reservation=quotas.reserve(owner,operation,fingerprint);
        if(!reservation.acquired()) throw new ConflictException("该请求已经处理，不能重复调用模型。");
        String job=UUID.randomUUID().toString();
        db.update("INSERT INTO ai_jobs(id,session_id,owner_id,operation_key,fingerprint,reservation_id,state,user_message,context_json,created_at,updated_at) VALUES (?,?,?,?,?,?,'QUEUED',?,?,?,?)",job,id,owner,operation,fingerprint,reservation.id(),message,context,now(),now());
        db.update("UPDATE ai_sessions SET version=version+1 WHERE id=?",id);return new Job(job,"QUEUED",null);
    }
    private String context(Row row,String message) {
        var history=db.query("SELECT user_message,reply_text FROM ai_jobs WHERE session_id=? AND state='SUCCEEDED' ORDER BY created_at DESC,id DESC LIMIT 6",(r,n)->Map.of("user",r.getString(1),"assistant",r.getString(2)),row.id);
        var turns=new ArrayList<>(history);Collections.reverse(turns);
        Map<String,Object> context=new LinkedHashMap<>();context.put("today",LocalDate.now(AiQuotaService.ZONE).toString());context.put("selectedMode",row.mode);context.put("currentDraft",row.draft==null?null:json(row.draft));context.put("history",turns);context.put("message",message);
        var snapshot=adjustments.decode(row.adjustment);
        if(snapshot!=null) {
            context.put("intent","ADJUST_EXISTING");context.put("existingItemIds",snapshot.items().keySet());context.put("existingStageIds",snapshot.stages().keySet());context.put("protectedItemIds",snapshot.protectedItems());
            if(row.draft==null) context.put("currentDraft",snapshot.original());
        }
        String result=write(context);
        while(result.length()>config.maxInputChars() && !turns.isEmpty()) { turns.removeFirst();result=write(context); }
        if(result.length()>config.maxInputChars()) throw new IllegalArgumentException("消息和草稿超过当前 AI 上下文限制，请手动编辑或缩短内容后重试。");
        return result;
    }
    @Transactional
    public Work claim() {
        lock();
        if(!available() || db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='RUNNING'",Integer.class)>=config.maxConcurrent()) return null;
        var ids=db.query("SELECT id FROM ai_jobs WHERE state='QUEUED' ORDER BY created_at,id LIMIT 1",(r,n)->r.getString(1));if(ids.isEmpty()) return null;
        var work=work(ids.getFirst());
        db.update("UPDATE ai_jobs SET state='RUNNING',updated_at=? WHERE id=?",now(),work.id);return work;
    }
    private Work work(String id) { return db.queryForObject("SELECT j.id,j.owner_id,j.session_id,j.reservation_id,j.context_json,s.selected_mode FROM ai_jobs j JOIN ai_sessions s ON s.id=j.session_id WHERE j.id=?",(r,n)->new Work(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),id); }
    @Transactional
    public void succeed(Work work,AiProvider.Result result) {
        // Untrusted JSON cannot escape into entities; validation never makes network calls.
        JsonNode output=json(result.text());
        if(output==null || !output.isObject()) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        Set<String> fields=new HashSet<>();output.fieldNames().forEachRemaining(fields::add);
        if(!fields.equals(Set.of("message","recommendedMode","draft")) || !output.path("message").isTextual() || output.path("message").asText().isBlank() || output.path("message").asText().length()>4000) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        String recommendation=output.path("recommendedMode").asText();
        try { AiDrafts.Mode.valueOf(recommendation); } catch(Exception invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
        lock();var row=owned(work.owner,work.sessionId,true);
        AiDrafts.Draft draft;
        try { draft=output.path("draft").isNull()?null:adjustments.parse(adjustments.decode(row.adjustment),write(output.get("draft"))); }
        catch(IllegalArgumentException invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
        if(draft!=null && (work.mode==null || !draft.mode().name().equals(work.mode))) throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);
        if(!"RUNNING".equals(db.queryForObject("SELECT state FROM ai_jobs WHERE id=?",String.class,work.id))) return;
        db.update("UPDATE ai_sessions SET recommended_mode=?,draft_json=?,version=version+1 WHERE id=?",recommendation,draft==null?row.draft:write(revision(draft,row.version+1)),row.id);
        db.update("UPDATE ai_jobs SET state='SUCCEEDED',context_json=NULL,reply_text=?,input_tokens=?,output_tokens=?,updated_at=? WHERE id=?",output.path("message").asText(),result.inputTokens(),result.outputTokens(),now(),work.id);
        usageMetadata(work,"SUCCEEDED",null,result.inputTokens(),result.outputTokens());
        quotas.settle(work.owner,work.reservation,"SUCCEEDED");
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
        var created=db.queryForObject("SELECT created_at FROM ai_jobs WHERE id=?",Timestamp.class,work.id).toInstant();
        var completed=Instant.now();
        db.update("UPDATE ai_quota_requests SET terminal_state=?,failure_code=?,input_tokens=?,output_tokens=?,completed_at=?,duration_ms=? WHERE id=? AND owner_id=?",
            state,code,input,output,Timestamp.from(completed),Math.max(0,Duration.between(created,completed).toMillis()),work.reservation,work.owner);
    }
    @Transactional
    public void maintain() {
        lock();
        var stale=db.query("SELECT id FROM ai_jobs WHERE state='RUNNING' AND updated_at<?",(r,n)->r.getString(1),Timestamp.from(Instant.now().minusSeconds(config.timeoutSeconds()+30L)));
        for(String id:stale) fail(work(id),new AiFailure(AiFailure.Code.TIMEOUT,true));
        if(!available()) for(String id:db.query("SELECT id FROM ai_jobs WHERE state='QUEUED'",(r,n)->r.getString(1))) fail(work(id),new AiFailure(AiFailure.Code.DISABLED,false));
        db.update("DELETE FROM ai_sessions WHERE expires_at<? AND id NOT IN (SELECT session_id FROM ai_jobs WHERE state IN ('QUEUED','RUNNING'))",now());
        // Bounded cleanup. Expired/deleted conversations cannot be replayed; current quota is untouched.
        var expired=db.query("SELECT id FROM ai_quota_requests WHERE quota_day<? AND id NOT IN (SELECT reservation_id FROM ai_jobs) LIMIT 200",
            (r,n)->r.getString(1),LocalDate.now(AiQuotaService.ZONE).minusDays(90));
        for(String id:expired) db.update("DELETE FROM ai_quota_requests WHERE id=?",id);
    }
    private static AiDrafts.Draft revision(AiDrafts.Draft d,long revision) { return new AiDrafts.Draft(1,d.mode(),revision,d.title(),d.startDate(),d.endDate(),d.restDays(),d.stages(),d.items()); }
    private JsonNode json(String source) { try { return mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION,com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(source); } catch(Exception invalid) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); } }
    private String write(Object value) { try{return mapper.writeValueAsString(value);}catch(Exception invalid){throw new AiFailure(AiFailure.Code.INVALID_OUTPUT);} }
    private static String hash(String source) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable");} }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
