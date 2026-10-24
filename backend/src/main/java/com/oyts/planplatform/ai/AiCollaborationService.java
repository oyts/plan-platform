package com.oyts.planplatform.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.oyts.planplatform.common.ConflictException;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Files are untrusted reference material, never an authorization or a private snapshot. */
@Service
class AiCollaborationService {
    private final JdbcTemplate db;private final ObjectMapper mapper;private final Validator validator;private final AiConversationService ai;
    AiCollaborationService(JdbcTemplate db,ObjectMapper mapper,Validator validator,AiConversationService ai){this.db=db;this.mapper=mapper;this.validator=validator;this.ai=ai;}
    record Material(String format,int schemaVersion,String reference,String mode,String instructions,List<String> protectedItemIds,AiDrafts.Draft draft,String prompt) {}
    record Preview(String binding,String message,AiDrafts.Draft original,AiDrafts.Draft proposed,String fingerprint,boolean canSave) {}
    private record Binding(String ref,long version,String source,Instant expires,String acceptedHash) {}
    @Transactional Material export(String owner,String id,long version,String instructions,boolean consent){
        if(!consent)throw new IllegalArgumentException("请先确认愿意将所选草稿提供给你选择的外部AI。");
        if(instructions==null||instructions.isBlank()||instructions.length()>2000)throw new IllegalArgumentException("请填写本次外部修改要求，最多2000字。");
        var session=ai.verifyEditable(owner,id,version);if(session.draft()==null)throw new IllegalArgumentException("请先保存一份所选草稿。");
        var draft=ai.validateExternal(owner,id,json(session.draft()));
        // Aliases already contain no real business IDs; drop internal revision as well.
        draft=new AiDrafts.Draft(1,draft.mode(),0,draft.title(),draft.startDate(),draft.endDate(),draft.restDays(),draft.stages(),draft.items());
        String reference=null;String encoded=json(draft);
        var previous=db.query("SELECT local_ref,source_json,instructions_text FROM ai_collaborations WHERE session_id=? AND source_version=? AND expires_at>?",(r,n)->new String[]{r.getString(1),r.getString(2),r.getString(3)},id,version,Timestamp.from(Instant.now()));
        for(var row:previous)if(encoded.equals(row[1])&&instructions.equals(row[2])){reference=row[0];break;}
        if(reference==null){
            if(db.queryForObject("SELECT COUNT(*) FROM ai_collaborations WHERE session_id=?",Integer.class,id)>=30)throw new ConflictException("此对话已保留30份协作材料，请继续使用已有材料或明确新建规划参考；不会自动删除确认记录。");
            reference=UUID.randomUUID().toString();var expiry=Instant.now().plus(Duration.ofDays(7));if(expiry.isAfter(session.expiresAt()))expiry=session.expiresAt();
            db.update("INSERT INTO ai_collaborations(local_ref,session_id,source_version,source_json,instructions_text,expires_at) VALUES (?,?,?,?,?,?)",reference,id,version,encoded,instructions,Timestamp.from(expiry));
        }
        var protectedIds=session.adjustment()==null?List.<String>of():new ArrayList<>(session.adjustment().protectedItems());
        String prompt="请仅按instructions修改本材料的draft，最后返回本JSON对象全部字段，其他字段原样保留，不加代码围栏。不要声称已创建或已修改真实计划。"+
            "容量100事项/30阶段。周期计划保留确认的起止日期与休息日，事项日期在周期内且避开休息日；目标路线无日期，依赖存在且无环；今日待办各项日期明确。"+
            "若protectedItemIds非空，其对应事项全部字段必须保持原样。调整原对象时保留全部原事项/阶段标识、原事项顺序，不删除合并、不修改执行历史。"+
            "reference仅是协作编号，不授予访问权限；导回后由本人核对差异，再明确保存和应用。";
        return new Material("plan-cabin-ai-collaboration",1,reference,draft.mode().name(),instructions,protectedIds,draft,prompt);
    }
    private Binding binding(String owner,String session,String ref){
        var rows=db.query("SELECT c.local_ref,c.source_version,c.source_json,c.expires_at,c.accepted_hash FROM ai_collaborations c JOIN ai_sessions s ON s.id=c.session_id WHERE c.local_ref=? AND c.session_id=? AND s.owner_id=?",(r,n)->new Binding(r.getString(1),r.getLong(2),r.getString(3),r.getTimestamp(4).toInstant(),r.getString(5)),ref,session,owner);return rows.isEmpty()?null:rows.getFirst();
    }
    private JsonNode parse(String source){
        if(source==null||source.length()>160000)throw new IllegalArgumentException("协作文件最多160000字，请保留原文件并缩短说明。");
        try{
            var tree=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(source);
            if(tree==null||!tree.isObject())throw new IllegalArgumentException();
            String format=tree.path("format").asText();
            if(format.equals("plan-cabin-ai-draft"))throw new IllegalArgumentException("这是工作区备份，请使用规划草稿箱的“导入已导出草稿”；外部协作包的draft应位于顶层。");
            if(!format.equals("plan-cabin-ai-collaboration"))throw new IllegalArgumentException("不是外部AI协作JSON。纯提示词不能导入；普通草稿请使用下方“导入外部AI草稿”，个人备份请使用数据中心。");
            var fields=new HashSet<String>();tree.fieldNames().forEachRemaining(fields::add);
            if(!fields.equals(Set.of("format","schemaVersion","reference","mode","instructions","protectedItemIds","draft","prompt"))||!tree.path("schemaVersion").isIntegralNumber()||tree.path("schemaVersion").intValue()!=1||!tree.path("reference").isTextual()||!tree.path("reference").asText().matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("协作包结构不完整，请让外部AI返回原协作JSON全部字段，仅修改draft。");
            return tree;
        }catch(IllegalArgumentException expected){throw expected;}catch(Exception invalid){throw new IllegalArgumentException("协作JSON损坏、字段重复或包含额外正文，原文件未改动。");}
    }
    @Transactional Preview preview(String owner,String id,long version,String source){
        var session=ai.verifyEditable(owner,id,version);var tree=parse(source);var binding=binding(owner,id,tree.path("reference").asText());
        boolean bound=binding!=null&&binding.version==version&&binding.expires.isAfter(Instant.now());
        AiDrafts.Draft proposed;
        if(bound)proposed=ai.validateExternal(owner,id,json(tree.get("draft")));
        else proposed=AiDrafts.parseDraft(json(tree.get("draft")),mapper,validator);
        if(session.mode()==null||!session.mode().equals(proposed.mode().name()))throw new IllegalArgumentException("返回草稿模式与当前选择不同，请新建对应模式对话作为参考，不会自动切换模式。");
        var original=session.draft();
        boolean canSave=bound||session.adjustment()==null;
        return new Preview(bound?"BOUND":"REFERENCE_ONLY",bound?"来源、账号、版本及原对象已核对。只读预检完成，尚未保存或应用。":"原关联不可用或版本已变化：仅可作为新规划参考，不能据此修改旧对象。",original,proposed,hash(id+"\n"+version+"\n"+source),canSave);
    }
    @Transactional AiConversationService.Session accept(String owner,String id,long version,String source,String fingerprint,boolean confirmed){
        if(!confirmed)throw new IllegalArgumentException("请先逐项核对差异并确认保存草稿，不会直接应用真实计划。");
        var tree=parse(source);var prior=binding(owner,id,tree.path("reference").asText());String contentHash=hash(source);
        // Durable replay, only for this owner's original binding and byte-identical material.
        if(prior!=null&&contentHash.equals(prior.acceptedHash))return ai.read(owner,id);
        var preview=preview(owner,id,version,source);
        if(!Objects.equals(preview.fingerprint,fingerprint))throw new ConflictException("预检材料或版本已变化，请重新只读预检；输入保留。");
        if(!preview.canSave)throw new ConflictException("此材料不能安全应用到原对象，请在新建对话中导入作参考。");
        var result=ai.saveDraft(owner,id,version,json(preview.proposed));
        if(prior!=null)db.update("UPDATE ai_collaborations SET accepted_hash=?,accepted_version=? WHERE local_ref=?",contentHash,result.version(),prior.ref);
        return result;
    }
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(Exception invalid){throw new IllegalArgumentException("协作内容无法读取。");}}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception invalid){throw new IllegalStateException();}}
}
