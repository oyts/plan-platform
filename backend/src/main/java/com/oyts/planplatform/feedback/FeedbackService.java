package com.oyts.planplatform.feedback;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.*;
import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
class FeedbackService {
    private final EntityManager em;private final ObjectMapper mapper;
    FeedbackService(EntityManager em,ObjectMapper mapper){this.em=em;this.mapper=mapper;}
    private static final Set<String> STATUSES=Set.of("OPEN","IN_PROGRESS","NEEDS_INFO","RESOLVED","CLOSED");
    private static final Set<String> DIAGNOSTIC_KEYS=Set.of("schemaVersion","clientVersion","serverVersion","apiVersion","platform","occurredAt","errorKind","requestId","queueCount","conflictCount");
    private static final Set<String> ERROR_KINDS=Set.of("NONE","NETWORK_OR_TLS","TLS","AUTH","PERMISSION","VERSION","BUSY","VALIDATION","CONFLICT","SERVER","UNKNOWN");
    private static final String VERSION="[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}([+-][A-Za-z0-9.-]{1,24})?";
    @Transactional FeedbackDtos.Detail create(String owner,FeedbackDtos.Create r) {
        var user=em.find(UserAccount.class,owner,LockModeType.PESSIMISTIC_WRITE);
        if(user==null||!user.isEnabled())throw new NotFoundException("账号不可用。");
        String diagnostic=diagnostic(r.diagnostic());
        String fingerprint=hash(json(List.of(r.category(),r.title().trim(),r.steps().trim(),r.expectedResult().trim(),r.actualResult().trim(),diagnostic==null?"":diagnostic)));
        var existing=em.createQuery("from FeedbackTicket f where f.owner.id=:owner and f.operationKey=:key",FeedbackTicket.class)
            .setParameter("owner",owner).setParameter("key",r.operationKey()).getResultList();
        if(!existing.isEmpty()) {
            if(!existing.getFirst().fingerprint.equals(fingerprint))throw new ConflictException("此提交编号已用于其他内容，请先核对已提交反馈。");
            return detail(existing.getFirst());
        }
        long daily=em.createQuery("select count(f) from FeedbackTicket f where f.owner.id=:owner and f.createdAt>=:since",Long.class)
            .setParameter("owner",owner).setParameter("since",LocalDate.now(ZoneId.of("Asia/Shanghai")).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()).getSingleResult();
        if(daily>=10)throw new TooManyRequestsException("今天已提交 10 条反馈，请在已有反馈中补充信息，或明天再提交。");
        var ticket=new FeedbackTicket(user,r,fingerprint,diagnostic);em.persist(ticket);em.flush();return detail(ticket);
    }
    PageResponse<FeedbackDtos.Summary> list(String owner,String status,int page,int size) {
        if(page<0||page>100000||size<1||size>50)throw new IllegalArgumentException("页码或每页数量无效。");
        if(!status.equals("ALL")&&!STATUSES.contains(status))throw new IllegalArgumentException("反馈状态无效。");
        String where=" where 1=1"+(owner==null?"":" and f.owner.id=:owner")+(status.equals("ALL")?"":" and f.status=:status");
        var rows=em.createQuery("from FeedbackTicket f"+where+" order by f.updatedAt desc,f.id",FeedbackTicket.class);
        var count=em.createQuery("select count(f) from FeedbackTicket f"+where,Long.class);
        if(owner!=null){rows.setParameter("owner",owner);count.setParameter("owner",owner);}
        if(!status.equals("ALL")){rows.setParameter("status",status);count.setParameter("status",status);}
        long total=count.getSingleResult();
        return new PageResponse<>(rows.setFirstResult(Math.multiplyExact(page,size)).setMaxResults(size).getResultList().stream().map(this::summary).toList(),page,size,total,(int)((total+size-1)/size));
    }
    FeedbackDtos.Detail read(String owner,String id){return detail(find(owner,id,false));}
    @Transactional FeedbackDtos.Detail reply(String owner,String id,FeedbackDtos.Reply r) {
        var ticket=find(owner,id,true);version(ticket,r.version());append(ticket,"MEMBER",r.body());
        // A member's new information is visible in the admin queue, including closed cases.
        ticket.status="OPEN";ticket.activity++;em.flush();return detail(ticket);
    }
    @Transactional FeedbackDtos.Detail handle(String id,FeedbackDtos.Handle r) {
        var ticket=find(null,id,true);version(ticket,r.version());
        if(Set.of("RESOLVED","CLOSED","NEEDS_INFO").contains(r.status())&&(r.body()==null||r.body().isBlank()))
            throw new IllegalArgumentException("请填写处理说明或需要补充的信息。");
        if(r.body()!=null&&!r.body().isBlank())append(ticket,"ADMIN",r.body());
        ticket.status=r.status();ticket.activity++;em.flush();return detail(ticket);
    }
    private FeedbackTicket find(String owner,String id,boolean lock) {
        var query=em.createQuery("from FeedbackTicket f where f.id=:id"+(owner==null?"":" and f.owner.id=:owner"),FeedbackTicket.class).setParameter("id",id);
        if(owner!=null)query.setParameter("owner",owner);if(lock)query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        return query.getResultStream().findFirst().orElseThrow(()->new NotFoundException("反馈不存在。"));
    }
    private void append(FeedbackTicket ticket,String author,String body) {
        if(em.createQuery("select count(m) from FeedbackMessage m where m.ticket.id=:id",Long.class).setParameter("id",ticket.getId()).getSingleResult()>=100)
            throw new IllegalArgumentException("此反馈已达到 100 条补充上限，请新建反馈并填写原反馈编号。");
        em.persist(new FeedbackMessage(ticket,author,body));
    }
    private void version(FeedbackTicket ticket,Long version){if(version==null||version!=ticket.getVersion())throw new ConflictException("反馈已被更新，请保留输入并读取最新处理记录后再提交。");}
    private FeedbackDtos.Summary summary(FeedbackTicket f){return new FeedbackDtos.Summary(f.getId(),f.title,f.category,f.status,f.getVersion(),f.getCreatedAt(),f.getUpdatedAt());}
    private FeedbackDtos.Detail detail(FeedbackTicket f){
        Map<String,Object> diagnostic=null;
        if(f.diagnosticJson!=null)try{diagnostic=mapper.readValue(f.diagnosticJson,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});}catch(JsonProcessingException e){throw new IllegalStateException("诊断摘要无法读取。");}
        var messages=em.createQuery("from FeedbackMessage m where m.ticket.id=:id order by m.createdAt,m.id",FeedbackMessage.class).setParameter("id",f.getId()).getResultList().stream()
            .map(m->new FeedbackDtos.Message(m.id,m.authorKind,m.body,m.createdAt)).toList();
        return new FeedbackDtos.Detail(summary(f),f.steps,f.expectedResult,f.actualResult,diagnostic,messages);
    }
    private String diagnostic(Map<String,Object> input) {
        if(input==null)return null;
        if(!DIAGNOSTIC_KEYS.containsAll(input.keySet())||!input.keySet().containsAll(Set.of("schemaVersion","clientVersion","platform","occurredAt","errorKind","queueCount","conflictCount")))
            throw new IllegalArgumentException("诊断摘要只能包含已列出的非敏感字段。");
        var data=new TreeMap<String,Object>(input);
        integer(data,"schemaVersion",1,1);integer(data,"queueCount",0,100000);integer(data,"conflictCount",0,100000);
        if(((Number)data.get("conflictCount")).intValue()>((Number)data.get("queueCount")).intValue())throw new IllegalArgumentException("队列数量无效。");
        if(!string(data,"clientVersion").matches(VERSION))throw new IllegalArgumentException("客户端版本无效。");
        if(data.get("serverVersion")!=null&&!string(data,"serverVersion").matches(VERSION))throw new IllegalArgumentException("服务版本无效。");
        if(data.get("apiVersion")!=null)integer(data,"apiVersion",1,100);
        if(!Set.of("WEB","WINDOWS","ANDROID").contains(string(data,"platform"))||!ERROR_KINDS.contains(string(data,"errorKind")))throw new IllegalArgumentException("诊断分类无效。");
        try { Instant.parse(string(data,"occurredAt")); } catch(Exception e){throw new IllegalArgumentException("诊断时间无效。");}
        if(data.get("requestId")!=null&&!string(data,"requestId").matches("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}"))throw new IllegalArgumentException("请求关联编号无效。");
        String value=json(data);if(value.length()>2000)throw new IllegalArgumentException("诊断摘要过大。");return value;
    }
    private String string(Map<String,Object> data,String key){if(!(data.get(key) instanceof String value)||value.length()>64)throw new IllegalArgumentException("诊断字段无效。");return value;}
    private void integer(Map<String,Object> data,String key,int min,int max){Object value=data.get(key);if(!(value instanceof Integer number)||number<min||number>max)throw new IllegalArgumentException("诊断数字无效。");}
    private String json(Object value){try{return mapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("反馈格式无效。");}}
    private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
