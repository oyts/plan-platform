package com.oyts.planplatform.ai;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** No prompt/response content; durable attempt claim prevents paid replay after restart. */
@Service
class AiAttempts {
    private final JdbcTemplate db;
    AiAttempts(JdbcTemplate db){this.db=db;}
    @Transactional
    public boolean begin(String job,int attempt){
        if(attempt<0||attempt>1)throw new IllegalArgumentException("Invalid attempt");
        String state=db.queryForObject("SELECT state FROM ai_jobs WHERE id=? FOR UPDATE",String.class,job);
        if(!"RUNNING".equals(state))return false;
        if(db.queryForObject("SELECT COUNT(*) FROM ai_job_attempts WHERE job_id=? AND attempt_no=?",Integer.class,job,attempt)>0)return false;
        if(attempt==1 && db.queryForObject("SELECT COUNT(*) FROM ai_job_attempts WHERE job_id=? AND attempt_no=0 AND state='REJECTED' AND input_tokens IS NOT NULL AND output_tokens IS NOT NULL",Integer.class,job)!=1)return false;
        db.update("INSERT INTO ai_job_attempts(job_id,attempt_no,state,started_at) VALUES (?,?,'RUNNING',?)",job,attempt,Timestamp.from(Instant.now()));
        return true;
    }
    @Transactional
    public void inputLength(String job,int attempt,int length){
        db.update("UPDATE ai_job_attempts SET input_chars=? WHERE job_id=? AND attempt_no=? AND state='RUNNING'",length,job,attempt);
    }
    @Transactional
    public void finish(String job,int attempt,String state,AiFailure failure,AiProvider.Result result){
        if(!java.util.Set.of("REPLIED","ACCEPTED","REJECTED","FAILED","UNKNOWN").contains(state))throw new IllegalArgumentException("Invalid state");
        db.update("UPDATE ai_job_attempts SET state=?,reason=?,input_tokens=?,output_tokens=?,output_chars=?,completed_at=? WHERE job_id=? AND attempt_no=? AND state IN ('RUNNING','REPLIED')",
            state,failure==null?null:failure.reason().name(),result==null?null:result.inputTokens(),result==null?null:result.outputTokens(),result==null||result.text()==null?null:result.text().length(),Timestamp.from(Instant.now()),job,attempt);
        // A late known response may arrive after maintenance marked the parent UNKNOWN.
        // Refresh only usage metadata, never outcome/quota state or business data.
        var totals=db.queryForMap("SELECT COUNT(*) AS n,COUNT(input_tokens) AS known,COALESCE(SUM(input_tokens),0) AS ins,COALESCE(SUM(output_tokens),0) AS outs FROM ai_job_attempts WHERE job_id=?",job);
        long known=((Number)totals.get("known")).longValue();
        db.update("UPDATE ai_quota_requests SET input_tokens=?,output_tokens=?,token_usage_complete=? WHERE id=(SELECT reservation_id FROM ai_jobs WHERE id=?) AND terminal_state IS NOT NULL",
            known==0?null:((Number)totals.get("ins")).longValue(),known==0?null:((Number)totals.get("outs")).longValue(),known==((Number)totals.get("n")).longValue(),job);
    }
}
