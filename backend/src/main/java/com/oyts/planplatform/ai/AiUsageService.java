package com.oyts.planplatform.ai;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregate metadata only. Never selects message, context, draft, reply or secrets. */
@Service
class AiUsageService {
    private final JdbcTemplate db;private final AiProperties config;private final AiExecutionGuard guard;private final AiRuntimePolicy runtime;
    AiUsageService(JdbcTemplate db,AiProperties config,AiExecutionGuard guard,AiRuntimePolicy runtime){this.db=db;this.config=config;this.guard=guard;this.runtime=runtime;}
    record Totals(long requests,long succeeded,long failed,long unknown,long queued,long running,long untracked,long inputTokens,long outputTokens,long tokenKnownRequests){}
    record Day(String date,Totals totals){}
    record Runtime(boolean enabled,int maxConcurrent,int queueCapacity,long queued,long running,AiExecutionGuard.Snapshot circuit){}
    record Entry(String requestId,String member,String email,String quotaDate,String state,String quotaState,String errorCode,Instant createdAt,Instant completedAt,Long durationMs,Long inputTokens,Long outputTokens){}
    record ReasonCount(String reason,long attempts){}
    record AttemptSummary(long attempts,long repairs,long tokenKnownAttempts,long inputTokens,long outputTokens,Long averageQueueWaitMs,long waitKnownJobs){}
    record Report(String from,String to,String timezone,Totals totals,List<Day> days,Runtime runtime,AiQuotaService.Page<Entry> recent,List<ReasonCount> reasons,AttemptSummary attempts){}
    private static final String STATE="COALESCE(q.terminal_state,j.state,CASE WHEN q.state='SUCCEEDED' THEN 'SUCCEEDED' WHEN q.state='RELEASED' THEN 'FAILED' WHEN q.state='UNKNOWN' THEN 'UNKNOWN' ELSE 'UNTRACKED' END)";
    private static final String INPUT="COALESCE(q.input_tokens,j.input_tokens)",OUTPUT="COALESCE(q.output_tokens,j.output_tokens)";
    private static final String FROM=" FROM ai_quota_requests q LEFT JOIN ai_jobs j ON j.reservation_id=q.id JOIN users u ON u.id=q.owner_id WHERE q.quota_day>=? AND q.quota_day<=?";
    private static String aggregate(){
        String s="COUNT(*)";
        for(String state:List.of("SUCCEEDED","FAILED","UNKNOWN","QUEUED","RUNNING","UNTRACKED"))s+=",COALESCE(SUM(CASE WHEN "+STATE+"='"+state+"' THEN 1 ELSE 0 END),0)";
        return s+",COALESCE(SUM("+INPUT+"),0),COALESCE(SUM("+OUTPUT+"),0),COALESCE(SUM(CASE WHEN COALESCE(q.token_usage_complete,TRUE)=TRUE AND "+INPUT+" IS NOT NULL AND "+OUTPUT+" IS NOT NULL THEN 1 ELSE 0 END),0)";
    }
    private Totals totals(java.sql.ResultSet r,int start)throws java.sql.SQLException{return new Totals(r.getLong(start),r.getLong(start+1),r.getLong(start+2),r.getLong(start+3),r.getLong(start+4),r.getLong(start+5),r.getLong(start+6),r.getLong(start+7),r.getLong(start+8),r.getLong(start+9));}
    @Transactional(readOnly=true)
    Report report(LocalDate from,LocalDate to,int page,int size){
        LocalDate today=LocalDate.now(AiQuotaService.ZONE);
        if(from==null)from=today.minusDays(6);if(to==null)to=today;
        if(from.isAfter(to)||from.isBefore(today.minusDays(89))||to.isAfter(today)||Duration.between(from.atStartOfDay(),to.atStartOfDay()).toDays()>30)throw new IllegalArgumentException("请选择最近90天内、不超过31天且不含未来日期的范围。");
        if(page<0||page>100000||size<1||size>50)throw new IllegalArgumentException("分页范围无效。");
        var sum=db.queryForObject("SELECT "+aggregate()+FROM,(r,n)->totals(r,1),from,to);
        var days=db.query("SELECT q.quota_day,"+aggregate()+FROM+" GROUP BY q.quota_day ORDER BY q.quota_day",(r,n)->new Day(r.getDate(1).toLocalDate().toString(),totals(r,2)),from,to);
        var rows=db.query("SELECT q.id,u.display_name,u.email,q.quota_day,"+STATE+",q.state,COALESCE(q.failure_code,j.error_code),q.created_at,COALESCE(q.completed_at,CASE WHEN j.state IN ('SUCCEEDED','FAILED','UNKNOWN') THEN j.updated_at END),q.duration_ms,"+INPUT+","+OUTPUT+FROM+" ORDER BY q.created_at DESC,q.id DESC LIMIT ? OFFSET ?",
            (r,n)->new Entry(r.getString(1),r.getString(2),r.getString(3),r.getDate(4).toLocalDate().toString(),r.getString(5),r.getString(6),safeCode(r.getString(7)),r.getTimestamp(8).toInstant(),r.getTimestamp(9)==null?null:r.getTimestamp(9).toInstant(),(Long)r.getObject(10),(Long)r.getObject(11),(Long)r.getObject(12)),from,to,size,(long)page*size);
        long queued=db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='QUEUED'",Long.class),running=db.queryForObject("SELECT COUNT(*) FROM ai_jobs WHERE state='RUNNING'",Long.class);
        var reasons=db.query("SELECT a.reason,COUNT(*) FROM ai_job_attempts a JOIN ai_jobs j ON j.id=a.job_id JOIN ai_quota_requests q ON q.id=j.reservation_id WHERE q.quota_day>=? AND q.quota_day<=? AND a.reason IS NOT NULL GROUP BY a.reason ORDER BY a.reason",(r,n)->new ReasonCount(safeReason(r.getString(1)),r.getLong(2)),from,to);
        var attemptRows=db.queryForMap("SELECT COUNT(*) AS attempts,COALESCE(SUM(CASE WHEN a.attempt_no=1 THEN 1 ELSE 0 END),0) AS repairs,COALESCE(SUM(CASE WHEN a.input_tokens IS NOT NULL AND a.output_tokens IS NOT NULL THEN 1 ELSE 0 END),0) AS known,COALESCE(SUM(a.input_tokens),0) AS inputs,COALESCE(SUM(a.output_tokens),0) AS outputs FROM ai_job_attempts a JOIN ai_jobs j ON j.id=a.job_id JOIN ai_quota_requests q ON q.id=j.reservation_id WHERE q.quota_day>=? AND q.quota_day<=?",from,to);
        var wait=db.queryForMap("SELECT COUNT(*) AS known,AVG(TIMESTAMPDIFF(SECOND,j.created_at,a.started_at)*1000) AS wait_ms FROM ai_job_attempts a JOIN ai_jobs j ON j.id=a.job_id JOIN ai_quota_requests q ON q.id=j.reservation_id WHERE a.attempt_no=0 AND a.started_at>=j.created_at AND q.quota_day>=? AND q.quota_day<=?",from,to);
        var attempts=new AttemptSummary(((Number)attemptRows.get("attempts")).longValue(),((Number)attemptRows.get("repairs")).longValue(),((Number)attemptRows.get("known")).longValue(),((Number)attemptRows.get("inputs")).longValue(),((Number)attemptRows.get("outputs")).longValue(),wait.get("wait_ms")==null?null:((Number)wait.get("wait_ms")).longValue(),((Number)wait.get("known")).longValue());
        var limits=runtime.current().limits();return new Report(from.toString(),to.toString(),"Asia/Shanghai",sum,days,new Runtime(config.enabled()&&!config.apiKeyFile().isBlank(),limits.maxConcurrent(),limits.queueCapacity(),queued,running,guard.snapshot()),new AiQuotaService.Page<>(rows,page,size,sum.requests()),reasons,attempts);
    }
    private String safeCode(String code){if(code==null)return null;try{return AiFailure.Code.valueOf(code).name();}catch(Exception unknown){return "UNKNOWN_ERROR";}}
    private String safeReason(String reason){try{return AiFailure.Reason.valueOf(reason).name();}catch(Exception unknown){return "UNKNOWN_ERROR";}}
}
