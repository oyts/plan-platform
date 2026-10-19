package com.oyts.planplatform.reminders;

import com.oyts.planplatform.common.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

/** Bounded unauthenticated admission; never store raw keys or network addresses. */
@Component
public class ReminderPollLimiter {
    private final Map<String,Deque<Long>> buckets=new HashMap<>();
    synchronized void acquire(String address,String key){
        long now=System.currentTimeMillis();buckets.values().forEach(q->{while(!q.isEmpty()&&q.getFirst()<now-60000)q.removeFirst();});buckets.entrySet().removeIf(e->e.getValue().isEmpty());
        String a=hash("address:"+Objects.toString(address,"unknown")),k=hash("key:"+Objects.toString(key,""));
        if(buckets.size()>4092)throw limited();
        var source=buckets.computeIfAbsent(a,x->new ArrayDeque<>());var device=buckets.computeIfAbsent(k,x->new ArrayDeque<>());
        if(source.size()>=120||device.size()>=12)throw limited();source.addLast(now);device.addLast(now);
    }
    private String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException();}}
    private TooManyRequestsException limited(){return new TooManyRequestsException("提醒核对过于频繁，请稍后再试。");}
}
