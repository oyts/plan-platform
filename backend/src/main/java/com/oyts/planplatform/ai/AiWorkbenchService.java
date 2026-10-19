package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.common.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicitly saved planning material, not chat retention, an AI job, or a business write. */
@Service
class AiWorkbenchService {
    private final JdbcTemplate db; private final ObjectMapper mapper;
    private final AiConversationService sessions; private final AiAdjustmentService adjustments;
    AiWorkbenchService(JdbcTemplate db,ObjectMapper mapper,AiConversationService sessions,AiAdjustmentService adjustments) {
        this.db=db;this.mapper=mapper;this.sessions=sessions;this.adjustments=adjustments;
    }
    record Saved(String id,String name,JsonNode content,long version,Instant updatedAt,boolean processing,boolean adjustment) {}
    record Summary(String id,String name,long version,Instant updatedAt,boolean processing,boolean adjustment) {}
    record Selection(String id,long version) {}
    record ClearResult(List<String> deleted,List<String> skipped) {}
    private record Row(String id,String name,String content,String source,String adjustment,long version,Instant updatedAt) {}
    private void lock(String owner) {
        if(db.query("SELECT id FROM users WHERE id=? AND enabled=true FOR UPDATE",(r,n)->r.getString(1),owner).isEmpty()) throw new NotFoundException("账号不存在。");
    }
    private Row owned(String owner,String id) {
        return db.query("SELECT id,name,content_json,source_session_id,adjustment_json,version,updated_at FROM ai_saved_drafts WHERE id=? AND owner_id=?",
            (r,n)->new Row(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getLong(6),r.getTimestamp(7).toInstant()),id,owner).stream().findFirst().orElseThrow(()->new NotFoundException("保存的草稿不存在。"));
    }
    private boolean processing(String source) { return source!=null && db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE session_id=? AND state IN ('QUEUED','RUNNING')",Integer.class,source)>0; }
    @Transactional(readOnly=true) List<Summary> list(String owner) {
        return db.query("SELECT id FROM ai_saved_drafts WHERE owner_id=? ORDER BY updated_at DESC,id LIMIT 30",(r,n)->r.getString(1),owner).stream().map(id->{var r=owned(owner,id);return new Summary(id,r.name,r.version,r.updatedAt,processing(r.source),r.adjustment!=null);}).toList();
    }
    @Transactional(readOnly=true) Saved read(String owner,String id) { var r=owned(owner,id);return new Saved(id,r.name,json(r.content),r.version,r.updatedAt,processing(r.source),r.adjustment!=null); }
    @Transactional Saved save(String owner,String id,String name,long version,String source) {
        lock(owner);if(name==null || name.isBlank() || name.length()>120)throw new IllegalArgumentException("草稿名称须为 1–120 字。");JsonNode content=validate(source);
        var exists=db.queryForObject("SELECT COUNT(*) FROM ai_saved_drafts WHERE id=? AND owner_id=?",Integer.class,id,owner)>0;
        Row prior=exists?owned(owner,id):null;
        if(prior==null && version!=-1 || prior!=null && prior.version!=version) throw new ConflictException("云端草稿已变化，本机修改仍保留，请比较后另存或重新确认。");
        String sessionId=content.path("sourceSessionId").isNull()?null:content.path("sourceSessionId").asText();String snapshot=null;
        if(sessionId!=null) {
            // Ownership is checked even for expired sessions; no resurrection and no paid request.
            var rows=db.query("SELECT adjustment_json FROM ai_sessions WHERE id=? AND owner_id=?",(r,n)->r.getString(1),sessionId,owner);
            if(rows.isEmpty()) {
                snapshot=prior!=null && Objects.equals(prior.source,sessionId)?prior.adjustment:"UNAVAILABLE_SOURCE";
            } else snapshot=rows.getFirst();
        }
        String encoded=write(content);var now=Timestamp.from(Instant.now());
        if(prior==null) {
            if(db.queryForObject("SELECT COUNT(*) FROM ai_saved_drafts WHERE owner_id=?",Integer.class,owner)>=30) throw new ConflictException("最多独立保存 30 份草稿，请先导出或删除不需要的草稿。");
            db.update("INSERT INTO ai_saved_drafts(id,owner_id,name,content_json,source_session_id,adjustment_json,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",id,owner,name,encoded,sessionId,snapshot,now,now);
        } else db.update("UPDATE ai_saved_drafts SET name=?,content_json=?,source_session_id=?,adjustment_json=?,version=version+1,updated_at=? WHERE id=? AND owner_id=?",name,encoded,sessionId,snapshot,now,id,owner);
        return read(owner,id);
    }
    @Transactional ClearResult clear(String owner,List<Selection> selected) {
        // Same order as AI mutations, preventing a request from being admitted between check/delete.
        db.queryForObject("SELECT id FROM ai_quota_policy WHERE id=1 FOR UPDATE",Integer.class);lock(owner);
        var deleted=new ArrayList<String>();var skipped=new ArrayList<String>();var seen=new HashSet<String>();
        for(var pick:selected) {if(!seen.add(pick.id))continue;var row=owned(owner,pick.id);if(row.version!=pick.version || processing(row.source)){skipped.add(row.id);continue;}
            db.update("DELETE FROM ai_saved_drafts WHERE id=? AND owner_id=?",row.id,owner);deleted.add(row.id);}
        return new ClearResult(deleted,skipped);
    }
    @Transactional AiConversationService.Session resume(String owner,String id,long version,boolean consent) {
        db.queryForObject("SELECT id FROM ai_quota_policy WHERE id=1 FOR UPDATE",Integer.class);lock(owner);
        var row=owned(owner,id);if(row.version!=version || processing(row.source)) throw new ConflictException("草稿已变化或原请求仍在处理，请先刷新核对。");
        if(!consent) throw new IllegalArgumentException("请重新确认本次对话处理说明。");
        var content=json(row.content);AiDrafts.Mode mode=content.path("mode").isNull()?null:AiDrafts.Mode.valueOf(content.path("mode").asText());
        AiConversationService.Session next;
        if(row.adjustment!=null) {
            if("UNAVAILABLE_SOURCE".equals(row.adjustment))throw new ConflictException("原对话已经过期且无可验证快照。草稿可编辑导出；请从原对象重新进入 AI 调整，或明确新建规划，不会自动复活旧对话。");
            var old=adjustments.decode(row.adjustment);
            // Includes progress/timers/dependencies, not only visible titles. Never rebase silently.
            adjustments.assertCurrent(owner,old);
            next=sessions.createAdjustment(owner,old.mode(),old.targetId(),true);
        } else next=sessions.create(owner,mode,true);
        if(!content.path("draft").isNull()) next=sessions.saveDraft(owner,next.id(),next.version(),write(content.get("draft")));
        return next; // Caller still needs an explicit send or apply. Old operation keys are never copied.
    }
    private JsonNode validate(String source) {
        if(source==null || source.getBytes(StandardCharsets.UTF_8).length>245760) throw new IllegalArgumentException("草稿超过 240 KiB，未覆盖已存内容。");
        var node=json(source);Set<String> fields=new HashSet<>();node.fieldNames().forEachRemaining(fields::add);
        if(!node.isObject() || !fields.equals(Set.of("schemaVersion","mode","message","importJson","draft","sourceSessionId","sourceVersion")) || !node.path("schemaVersion").isInt() || node.path("schemaVersion").asInt()!=1 || !node.path("message").isTextual() || node.path("message").asText().length()>8000 || !node.path("importJson").isTextual() || node.path("importJson").asText().length()>128000 || !(node.path("draft").isNull() || node.path("draft").isObject()) || !(node.path("mode").isNull() || node.path("mode").isTextual() && Set.of("PLAN","ROUTE","TODO").contains(node.path("mode").asText())) || !(node.path("sourceSessionId").isNull() || node.path("sourceSessionId").isTextual() && node.path("sourceSessionId").asText().matches("[a-f0-9-]{36}")) || !(node.path("sourceVersion").isNull() || node.path("sourceVersion").isIntegralNumber() && node.path("sourceVersion").canConvertToLong() && node.path("sourceVersion").asLong()>=0)) throw new IllegalArgumentException("保存的工作区格式无效。");
        // Incomplete edits can be kept; strict AiDrafts/adjustment validation happens on explicit resume.
        return node;
    }
    private JsonNode json(String source) {try{return mapper.copy().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(source);}catch(Exception e){throw new IllegalArgumentException("草稿 JSON 无效。");}}
    private String write(Object value) {try{return mapper.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("草稿无法保存。");}}
}
