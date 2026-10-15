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
    private final boolean enabled; private final ExecutorService workers; private final AtomicInteger active=new AtomicInteger();
    AiJobRunner(AiConversationService sessions,AiProvider provider,AiExecutionGuard guard,AiProperties config,@Value("${app.ai.worker-enabled:true}") boolean enabled) {
        this.sessions=sessions;this.provider=provider;this.guard=guard;this.config=config;this.enabled=enabled;
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
        try { var result=guard.call(provider,new AiProvider.Request(work.context(),config.maxOutputTokens()),Duration.ofSeconds(config.timeoutSeconds()));sessions.succeed(work,result); }
        catch(AiFailure safe){sessions.fail(work,safe);}
        catch(Exception persistence){sessions.fail(work,new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE,true));}
    }
    @Scheduled(fixedDelay=60000,initialDelay=5000)
    public void maintain() { if(enabled) sessions.maintain(); }
    @PreDestroy void close() { workers.shutdownNow(); }
}
