package com.oyts.planplatform.operations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable at-most-once attempt. SMTP acceptance is not inbox delivery. */
@Service
class OperationsMailDelivery {
    private static final Set<String> CODES=Set.of("OPS_DATA_UNAVAILABLE","BACKUP_STALE","OSS_UNVERIFIED","RESTORE_UNVERIFIED","DISK_LOW","CATALOG_INCOMPLETE","BACKUP_FAILED","OSS_FAILED","RESTORE_FAILED");
    private final JdbcTemplate db;
    private final ObjectProvider<JavaMailSender> senders;
    private final TransactionTemplate transaction;
    private final boolean enabled,scheduler;
    private final String recipient,from;
    record Status(boolean enabled,boolean configured,Map<String,Long> deliveries,String note){}
    OperationsMailDelivery(JdbcTemplate db,ObjectProvider<JavaMailSender> senders,PlatformTransactionManager manager,
        @Value("${app.operations.enabled:false}")boolean operations,
        @Value("${app.operations.mail-alerts-enabled:false}")boolean enabled,
        @Value("${app.operations.scheduler-enabled:true}")boolean scheduler,
        @Value("${app.operations.mail-to:}")String recipient,@Value("${app.mail-from:}")String from){
        this.db=db;this.senders=senders;this.enabled=operations&&enabled;this.scheduler=scheduler;
        this.recipient=recipient.trim();this.from=from.trim();
        transaction=new TransactionTemplate(manager);transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    boolean configured(){return recipient.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")&&!from.isBlank()&&senders.getIfAvailable()!=null;}
    boolean enabled(){return enabled&&configured();}
    Status status(){
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String state:List.of("PENDING","SENDING","ACCEPTED","UNKNOWN","SUPERSEDED"))counts.put(state,db.queryForObject("select count(*) from operations_mail_delivery where state=?",Long.class,state));
        return new Status(enabled,configured(),counts,"ACCEPTED仅代表发件服务器接受；UNKNOWN不自动重试，避免重复发信。页面读取不发送邮件。");
    }
    // Called inside the evaluation transaction while holding the operations-policy lock.
    void opened(String incident,String code){
        if(!enabled()||!CODES.contains(code))return;
        if(db.queryForObject("select count(*) from operations_mail_delivery where incident_id=? and event_kind='OPENED'",Long.class,incident)==0)
            db.update("insert into operations_mail_delivery(id,incident_id,code,event_kind,state,created_at,updated_at) values (?,?,?,'OPENED','PENDING',?,?)",UUID.randomUUID().toString(),incident,code,now(),now());
    }
    void resolved(String incident,String code){
        // An unsent short-lived incident must not produce a stale failure message.
        db.update("update operations_mail_delivery set state='SUPERSEDED',updated_at=? where incident_id=? and event_kind='OPENED' and state='PENDING'",now(),incident);
        if(!enabled()||!CODES.contains(code))return;
        long attempted=db.queryForObject("select count(*) from operations_mail_delivery where incident_id=? and event_kind='OPENED' and state in ('SENDING','ACCEPTED','UNKNOWN')",Long.class,incident);
        if(attempted>0&&db.queryForObject("select count(*) from operations_mail_delivery where incident_id=? and event_kind='RESOLVED'",Long.class,incident)==0)
            db.update("insert into operations_mail_delivery(id,incident_id,code,event_kind,state,created_at,updated_at) values (?,?,?,'RESOLVED','PENDING',?,?)",UUID.randomUUID().toString(),incident,code,now(),now());
    }
    @Scheduled(fixedDelay=60000,initialDelay=90000)
    public void scheduled(){if(scheduler)dispatch();}
    synchronized void dispatch(){
        if(!enabled())return;
        List<Map<String,Object>> batch=transaction.execute(state->{
            // Shared mutex with evaluate: a transition and its mail event are atomic.
            db.queryForList("select id from operations_policy where id=1 for update");
            db.update("update operations_mail_delivery set state='UNKNOWN',updated_at=? where state='SENDING' and updated_at<?",now(),Timestamp.from(Instant.now().minusSeconds(600)));
            var rows=db.queryForList("select id,incident_id,code,event_kind,created_at from operations_mail_delivery where state='PENDING' order by created_at,id limit 20");
            for(var row:rows)db.update("update operations_mail_delivery set state='SENDING',updated_at=? where id=? and state='PENDING'",now(),row.get("id"));
            return rows;
        });
        if(batch==null||batch.isEmpty())return;
        String outcome="UNKNOWN";
        try{
            StringBuilder body=new StringBuilder("计划舱运行状态变化（同批事件合并发送）：\n");
            for(var event:batch){String code=Objects.toString(event.get("code"));if(!CODES.contains(code))throw new IllegalStateException("Unknown operational code");
                body.append("RESOLVED".equals(event.get("event_kind"))?"已恢复：":"需要关注：").append(code).append("\n事件编号：").append(event.get("incident_id")).append("\n");}
            body.append("\n请在管理员的运行与成员治理页面核对。邮件不包含用户计划、账号链接、数据库口令、原始日志或备份正文。此通知不会执行清理。");
            var mail=new SimpleMailMessage();mail.setFrom(from);mail.setTo(recipient);mail.setSubject("计划舱：运行状态通知");mail.setText(body.toString());
            senders.getObject().send(mail);outcome="ACCEPTED";
        }catch(RuntimeException failure){/* Do not log SMTP payload/credentials or retry an ambiguous send. */}
        final String result=outcome;
        transaction.executeWithoutResult(state->{for(var row:batch)db.update("update operations_mail_delivery set state=?,updated_at=? where id=? and state='SENDING'",result,now(),row.get("id"));});
    }
    private Timestamp now(){return Timestamp.from(Instant.now());}
}
