package com.oyts.planplatform.ai;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AiJobRunner {
    private final AiConversationService sessions; private final AiProvider provider; private final AiExecutionGuard guard; private final AiProperties config;
    private final boolean enabled,repairEnabled; private final AiAttempts attempts; private final ExecutorService workers; private final AtomicInteger active=new AtomicInteger();
    AiJobRunner(AiConversationService sessions,AiProvider provider,AiExecutionGuard guard,AiProperties config,AiAttempts attempts,
                @Value("${app.ai.worker-enabled:true}") boolean enabled,@Value("${app.ai.repair-enabled:false}") boolean repairEnabled) {
        this.sessions=sessions;this.provider=provider;this.guard=guard;this.config=config;this.attempts=attempts;this.enabled=enabled;this.repairEnabled=repairEnabled;
        workers=Executors.newFixedThreadPool(config.maxConcurrent(),r->{var t=new Thread(r,"plan-ai-job");t.setDaemon(true);return t;});
    }
    @Scheduled(fixedDelay=1000,initialDelay=2000)
    public void dispatch() {
        if(!enabled || !config.enabled()) return;
        while(active.get()<config.maxConcurrent()) {
            var work=sessions.claim();if(work==null)return;active.incrementAndGet();
            try{workers.submit(()->{try{run(work);}finally{active.decrementAndGet();}});}
            catch(RejectedExecutionException closing){active.decrementAndGet();sessions.fail(work,new AiFailure(AiFailure.Code.BUSY,false));return;}
        }
    }
    void run(AiConversationService.Work work) {
        if(!sessions.runnable(work)){sessions.fail(work,new AiFailure(AiFailure.Code.DISABLED,false));return;}
        if(!attempts.begin(work.id(),0))return;
        long deadline=System.nanoTime()+Duration.ofSeconds(config.timeoutSeconds()).toNanos();
        String context=work.context();int outputLimit=config.maxOutputTokens();
        for(int index=0;index<=1;index++){
            AiProvider.Result result=null;
            try {
                if(!sessions.runnable(work))throw new AiFailure(AiFailure.Code.DISABLED,false);
                attempts.inputLength(work.id(),index,context.length());
                if(index==0 && (long)context.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+AiPrompt.SYSTEM.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>64000)
                    throw new AiFailure(AiFailure.Code.INVALID_CONFIG,false);
                long millis=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
                if(millis<1)throw new AiFailure(AiFailure.Code.TIMEOUT,true);
                result=guard.call(provider,new AiProvider.Request(context,outputLimit),Duration.ofMillis(millis));
                if(result.outputTokens()>outputLimit || result.inputTokens()>64000)throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.BUDGET_EXCEEDED,result);
                attempts.finish(work.id(),index,"REPLIED",null,result);
                String normalized=AiOutputText.unwrap(result.text());
                sessions.succeed(work,new AiProvider.Result(normalized,result.inputTokens(),result.outputTokens()));
                attempts.finish(work.id(),index,"ACCEPTED",null,result);
                return;
            } catch(AiFailure safe){
                if(result==null)result=safe.candidate();
                sessions.retainReadableReply(work,result);
                attempts.finish(work.id(),index,safe.uncertain()?"UNKNOWN":safe.code()==AiFailure.Code.INVALID_OUTPUT?"REJECTED":"FAILED",safe,result);
                if(index==0 && repairEnabled && !safe.uncertain() && safe.code()==AiFailure.Code.INVALID_OUTPUT
                    && safe.reason()!=AiFailure.Reason.MODE_MISMATCH && safe.reason()!=AiFailure.Reason.BUDGET_EXCEEDED && result!=null){
                    int remaining=(int)Math.max(0,Math.min(config.maxOutputTokens(),8192L-result.outputTokens()));
                    String repair=repairContext(work.context(),result,safe);
                    // Input byte bound is conservative, includes fixed system prompt, never truncates user data.
                    long estimated=repair==null?Long.MAX_VALUE:(long)repair.getBytes(java.nio.charset.StandardCharsets.UTF_8).length+AiPrompt.SYSTEM.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    if(remaining>=128 && repair!=null && repair.length()<=config.maxInputChars()
                        && result.inputTokens()<=64000-estimated && deadline-System.nanoTime()>Duration.ofMillis(250).toNanos()
                        && attempts.begin(work.id(),1)){
                        context=repair;outputLimit=remaining;continue;
                    }
                }
                sessions.fail(work,safe);return;
            } catch(Exception persistence){
                var unknown=new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,true);
                attempts.finish(work.id(),index,"UNKNOWN",unknown,result);
                sessions.fail(work,unknown);return;
            }
        }
    }
    private String repairContext(String original,AiProvider.Result result,AiFailure failure){
        if(result.text()==null||result.text().length()>128000)return null;
        try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
            "intent","OUTPUT_REPAIR","originalContext",original,"rejectedOutput",result.text(),"reason",failure.reason().name()));}
        catch(Exception ignored){return null;}
    }
    @Scheduled(fixedDelay=60000,initialDelay=5000)
    public void maintain() { if(enabled) sessions.maintain(); }
    @PreDestroy void close() { workers.shutdownNow(); }
}
