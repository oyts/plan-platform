package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single-instance bounded admission; settings contain numbers only, never credentials/content. */
@Service
public class AiRuntimePolicy {
    public static final int HARD_CONCURRENT=2,HARD_QUEUE=8;
    public record Limits(
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer maxConcurrent,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer queueCapacity,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer maxInputChars,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer maxOutputTokens,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer totalOutputTokens,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer timeoutSeconds) {
        public Limits {
            if(maxConcurrent==null||queueCapacity==null||maxInputChars==null||maxOutputTokens==null||totalOutputTokens==null||timeoutSeconds==null
                ||maxConcurrent<1||maxConcurrent>HARD_CONCURRENT||queueCapacity<0||queueCapacity>HARD_QUEUE
                ||maxInputChars<100||maxInputChars>32000||maxOutputTokens<128||maxOutputTokens>8192
                ||totalOutputTokens<maxOutputTokens||totalOutputTokens>8192||totalOutputTokens>(long)maxOutputTokens*2
                ||timeoutSeconds<1||timeoutSeconds>120)throw new IllegalArgumentException("AI参数超出安全范围，或首轮与合计输出上限不相容。");
        }
    }
    public record Policy(Limits limits,long version,Instant updatedAt) {}
    public record Audit(String id,String actor,Policy before,Policy after,String restoredFrom,Instant createdAt) {}
    private final JdbcTemplate db;private final ObjectMapper mapper;private final AiProperties defaults;
    public AiRuntimePolicy(JdbcTemplate db,ObjectMapper mapper,AiProperties defaults){this.db=db;this.mapper=mapper;this.defaults=defaults;}
    public Policy current(){return db.queryForObject("SELECT settings_json,version,updated_at FROM ai_runtime_policy WHERE id=1",(r,n)->new Policy(r.getString(1)==null?initial():decode(r.getString(1)),r.getLong(2),r.getTimestamp(3)==null?null:r.getTimestamp(3).toInstant()));}
    private Limits initial(){int output=defaults.maxOutputTokens();return new Limits(Math.min(HARD_CONCURRENT,defaults.maxConcurrent()),Math.min(HARD_QUEUE,defaults.queueCapacity()),defaults.maxInputChars(),output,Math.min(8192,output*2),defaults.timeoutSeconds());}
    private Limits decode(String text){try{return mapper.readValue(text,Limits.class);}catch(Exception e){throw new IllegalStateException("AI运行配置不可读取，已拒绝新调用。");}}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("AI运行配置无法保存。");}}
    private void lock(){db.queryForObject("SELECT id FROM ai_quota_policy WHERE id=1 FOR UPDATE",Integer.class);}
    @Transactional public Policy change(String actor,Limits proposed,long version){lock();return save(actor,proposed,version,null);}
    private Policy save(String actor,Limits proposed,long version,String restored){
        if(proposed==null||version<0)throw new IllegalArgumentException("请提供有效配置和版本。");
        var before=current();if(before.version()!=version)throw new ConflictException("运行配置已被其他管理员修改，请读取最新值后核对，未覆盖。");
        if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND enabled=TRUE AND role='ADMIN'",Integer.class,actor)!=1)throw new NotFoundException("管理员不存在。");
        var now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);var after=new Policy(proposed,version+1,now);
        // Legacy accepted jobs have no V33 snapshot. Freeze the *previous* deployment
        // policy under the same admission lock before any administrator override.
        var old=before.limits();
        db.update("UPDATE ai_jobs SET runtime_input_chars=?,runtime_output_tokens=?,runtime_total_output_tokens=?,runtime_timeout_seconds=?,runtime_version=? WHERE runtime_version IS NULL AND state IN ('QUEUED','RUNNING')",old.maxInputChars(),old.maxOutputTokens(),old.totalOutputTokens(),old.timeoutSeconds(),before.version());
        db.update("UPDATE ai_runtime_policy SET settings_json=?,version=?,updated_at=? WHERE id=1",json(proposed),after.version(),Timestamp.from(now));
        db.update("INSERT INTO ai_runtime_audit(id,actor_id,before_json,after_json,restored_from,created_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),actor,json(before),json(after),restored,Timestamp.from(now));return after;
    }
    @Transactional public Policy restore(String actor,String auditId,long version,boolean confirmed){
        if(!confirmed)throw new IllegalArgumentException("请明确确认恢复该记录的修改前配置。");lock();
        var rows=db.query("SELECT before_json FROM ai_runtime_audit WHERE id=?",(r,n)->r.getString(1),auditId);
        if(rows.isEmpty())throw new NotFoundException("配置记录不存在。");
        try{return save(actor,mapper.readValue(rows.getFirst(),Policy.class).limits(),version,auditId);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("历史配置不可读取，未更改当前值。");}
    }
    public AiQuotaService.Page<Audit> history(int page,int size){
        if(page<0||page>100000||size<1||size>50)throw new IllegalArgumentException("分页范围无效。");
        var items=db.query("SELECT a.id,u.display_name,a.before_json,a.after_json,a.restored_from,a.created_at FROM ai_runtime_audit a JOIN users u ON u.id=a.actor_id ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",(r,n)->{
            try{return new Audit(r.getString(1),r.getString(2),mapper.readValue(r.getString(3),Policy.class),mapper.readValue(r.getString(4),Policy.class),r.getString(5),r.getTimestamp(6).toInstant());}
            catch(Exception e){throw new IllegalStateException("配置审计不可读取。");}},size,(long)page*size);
        return new AiQuotaService.Page<>(items,page,size,db.queryForObject("SELECT COUNT(*) FROM ai_runtime_audit",Long.class));
    }
}
