package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.mainline.domain.Mainline;
import com.oyts.planplatform.plan.domain.Plan;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
class LegacySupplementService {
    private final UserAccountRepository users; private final LegacyImportRepository imports;
    private final LegacySupplementReceiptRepository receipts; private final LegacyBackfillPreview previews;
    private final LegacyRecyclingWriter writer; private final ObjectMapper json; private final EntityManager entities;
    LegacySupplementService(UserAccountRepository users,LegacyImportRepository imports,LegacySupplementReceiptRepository receipts,
                            LegacyBackfillPreview previews,LegacyRecyclingWriter writer,ObjectMapper json,EntityManager entities) {
        this.users=users;this.imports=imports;this.receipts=receipts;this.previews=previews;this.writer=writer;this.json=json;this.entities=entities;
    }
    record Request(@NotNull UUID operationKey,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String fingerprint,@AssertTrue boolean confirmed) {}
    record Result(String importId,String status,String fingerprint,Instant importCompletedAt,int plans,int mainlines,
                  int tags,int tasks,int mainlineItems,int timeEntries,int reflections,Instant appliedAt) {}

    @Transactional(isolation=Isolation.READ_COMMITTED)
    Result apply(String owner,String batchId,Request request) {
        var user=users.lockById(owner).orElseThrow(()->new NotFoundException("用户不存在。"));
        LegacyImport batch=imports.findByIdAndOwnerId(batchId,owner).orElseThrow(()->new NotFoundException("迁移批次不存在。"));
        if(!"COMPLETED".equals(batch.getStatus()))throw conflict("原批次已回滚或尚未完成。");
        var byKey=receipts.findByOwnerIdAndOperationKey(owner,request.operationKey().toString()).orElse(null);
        var receipt=receipts.findByImportId(batchId).orElse(null);
        if(byKey!=null&&(!byKey.getImportId().equals(batchId)||!byKey.getFingerprint().equals(request.fingerprint())))throw conflict("操作编号已用于其他内容。");
        if(receipt!=null) {
            if(!receipt.getFingerprint().equals(request.fingerprint()))throw conflict("本批已经补迁，请刷新历史。");
            Result previous=read(receipt.getResultJson(),Result.class);
            if(!previous.importCompletedAt().equals(batch.getCompletedAt()))throw conflict("原批次已重新导入，不可重用旧补迁请求。");
            return previous;
        }
        var preview=previews.preview(owner,batchId);
        if(preview.alreadyIncluded()||preview.entries().isEmpty())throw conflict("没有需要补迁的快照。");
        // Serialize against concurrent parent deletion/editing, then refresh managed versions.
        preview.entries().stream().filter(e->e.targetParentId()!=null)
            .map(e->(e.type().equals("tag")?"plan:":"line:")+e.targetParentId()).distinct().sorted().forEach(key->{
                Class<?> type=key.startsWith("plan:")?Plan.class:Mainline.class;
                Object parent=entities.find(type,key.substring(5),LockModeType.PESSIMISTIC_WRITE);
                if(parent==null)throw conflict("父对象已改变，请重新预检。");
                entities.refresh(parent,LockModeType.PESSIMISTIC_WRITE);
            });
        preview=previews.preview(owner,batchId);
        if(!preview.fingerprint().equals(request.fingerprint()))throw conflict("预检后数据已改变，请重新预检；没有写入任何补迁内容。");
        JsonNode source=read(batch.getPayload(),JsonNode.class);
        var added=writer.write(user,LegacyRecycling.convert(source),preview.entries());
        ObjectNode report=read(batch.getReportJson(),ObjectNode.class);
        increment(report,"tags",added.tags());increment(report,"tasks",added.tasks());increment(report,"progressRecords",added.progress());
        increment(report,"timeEntries",added.entries());increment(report,"reflections",added.reflections());increment(report,"mainlineItems",added.items());
        append(report,"createdPlanIds",added.plans());append(report,"createdMainlineIds",added.lines());
        report.put("recyclingVersion",1);
        ArrayNode warnings=report.path("warnings").isArray()?(ArrayNode)report.path("warnings"):report.putArray("warnings");
        warnings.add("已显式补迁旧回收站快照；原迁移后的编辑不覆盖。回滚本批包括原迁移及补迁内容。");
        batch.supplement(report.toString());
        Result result=new Result(batchId,"APPLIED",preview.fingerprint(),batch.getCompletedAt(),added.plans().size(),added.lines().size(),
            added.tags(),added.tasks(),added.items(),added.entries(),added.reflections(),Instant.now());
        receipts.save(new LegacySupplementReceipt(owner,batchId,request.operationKey().toString(),preview.fingerprint(),encode(result)));
        entities.flush();return result;
    }
    private void increment(ObjectNode report,String field,int add){report.put(field,Math.addExact(report.path(field).asInt(),add));}
    private void append(ObjectNode report,String field,List<String> ids){if(!report.path(field).isArray())throw conflict("原报告缺少父对象清单。");ArrayNode list=(ArrayNode)report.path(field);ids.forEach(list::add);}
    private <T> T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw conflict("批次记录损坏，保留快照等待核对。");}}
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException("补迁回执无法保存。",e);}}
    private static ConflictException conflict(String message){return new ConflictException("旧批次补迁："+message);}
}
