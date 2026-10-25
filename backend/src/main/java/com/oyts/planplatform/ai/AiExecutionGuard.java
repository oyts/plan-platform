package com.oyts.planplatform.ai;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Small bounded pool, overall timeout and circuit breaker. No automatic paid retries. */
public final class AiExecutionGuard implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong blockedUntil = new AtomicLong();
    public AiExecutionGuard(int concurrent, int queueCapacity) {
        if (concurrent < 1 || concurrent > 4 || queueCapacity < 0 || queueCapacity > 20) throw new IllegalArgumentException("AI 并发限制无效。");
        BlockingQueue<Runnable> queue = queueCapacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueCapacity);
        executor = new ThreadPoolExecutor(concurrent, concurrent, 0, TimeUnit.SECONDS, queue,
            runnable -> { var thread = new Thread(runnable, "plan-ai-worker"); thread.setDaemon(true); return thread; }, new ThreadPoolExecutor.AbortPolicy());
    }
    public AiProvider.Result call(AiProvider provider, AiProvider.Request request, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(120)) > 0) throw new IllegalArgumentException("AI 超时范围无效。");
        long deadline = blockedUntil.get();
        if (deadline != 0 && deadline - System.nanoTime() > 0) throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE, false);
        Future<AiProvider.Result> future;
        try { future = executor.submit(() -> provider.generate(request)); }
        catch (RejectedExecutionException busy) { throw new AiFailure(AiFailure.Code.BUSY); }
        try {
            var result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (result == null || result.text() == null || result.text().isBlank() || result.text().length() > 128000 || result.inputTokens() < 0 || result.outputTokens() < 0 || result.inputTokens()>1000000 || result.outputTokens()>8192) {
                failed(); throw new AiFailure(AiFailure.Code.INVALID_OUTPUT,false,AiFailure.Reason.OUTPUT_INVALID,
                    result!=null && result.inputTokens()>=0 && result.outputTokens()>=0 && result.inputTokens()<=1000000 && result.outputTokens()<=8192?result:null);
            }
            failures.set(0); return result;
        } catch (TimeoutException failure) { future.cancel(true); failed(); throw new AiFailure(AiFailure.Code.TIMEOUT); }
        catch (InterruptedException failure) { future.cancel(true); Thread.currentThread().interrupt(); throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE); }
        catch (ExecutionException failure) { failed(); if (failure.getCause() instanceof AiFailure safe) throw safe; throw new AiFailure(AiFailure.Code.UPSTREAM_UNAVAILABLE); }
        finally { executor.purge(); }
    }
    private void failed() {
        if (failures.incrementAndGet() >= 3) blockedUntil.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
    }
    /** Shrink only prevents new execution; already running calls are never interrupted. */
    public synchronized void configure(int concurrent){
        if(concurrent<1||concurrent>AiRuntimePolicy.HARD_CONCURRENT)throw new IllegalArgumentException("并发超出硬边界。");
        if(concurrent>executor.getMaximumPoolSize()){executor.setMaximumPoolSize(concurrent);executor.setCorePoolSize(concurrent);}
        else{executor.setCorePoolSize(concurrent);executor.setMaximumPoolSize(concurrent);}
    }
    public record Snapshot(int active,int queued,boolean circuitOpen,long retryAfterSeconds) {}
    public Snapshot snapshot() {
        long remaining=blockedUntil.get()-System.nanoTime();
        boolean open=blockedUntil.get()!=0 && remaining>0;
        return new Snapshot(executor.getActiveCount(),executor.getQueue().size(),open,open?Math.max(1,TimeUnit.NANOSECONDS.toSeconds(remaining)+1):0);
    }
    @Override public void close() { executor.shutdownNow(); }
}
