package com.oyts.planplatform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Component;

/** Single-instance bounded admission. Unknown and known recovery emails use the same buckets. */
@Component
class MailRequestLimiter {
    private final Clock clock;
    private final Map<String,Deque<Long>> attempts = new HashMap<>();
    MailRequestLimiter() { this(Clock.systemUTC()); }
    MailRequestLimiter(Clock clock) { this.clock=clock; }
    synchronized int retryAfterSeconds(String purpose,String identity,String address) {
        long now=clock.millis(),wait=0;
        var person=attempts.get(key(purpose+"|user|"+identity.trim().toLowerCase(Locale.ROOT)));
        var source=attempts.get(key(purpose+"|address|"+Objects.toString(address,"unknown")));
        if(person!=null){var active=person.stream().filter(t->t>now-900_000).toList();if(!active.isEmpty())wait=Math.max(wait,active.getLast()+60_000-now);if(active.size()>=3)wait=Math.max(wait,active.getFirst()+900_000-now);}
        if(source!=null){var active=source.stream().filter(t->t>now-900_000).toList();if(active.size()>=10)wait=Math.max(wait,active.getFirst()+900_000-now);}
        return (int)Math.max(0,(wait+999)/1000);
    }
    synchronized boolean acquire(String purpose,String identity,String address) {
        long now=clock.millis(),window=900_000;
        attempts.values().forEach(q->{while(!q.isEmpty()&&q.getFirst()<=now-window)q.removeFirst();});
        attempts.entrySet().removeIf(e->e.getValue().isEmpty());
        String person=key(purpose+"|user|"+identity.trim().toLowerCase(Locale.ROOT));
        String source=key(purpose+"|address|"+Objects.toString(address,"unknown"));
        if(attempts.size()>4092)return false;
        var user=attempts.computeIfAbsent(person,k->new ArrayDeque<>());
        var ip=attempts.computeIfAbsent(source,k->new ArrayDeque<>());
        if(user.size()>=3 || (!user.isEmpty()&&now-user.getLast()<60_000) || ip.size()>=10)return false;
        user.addLast(now);ip.addLast(now);return true;
    }
    private static String key(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable");}
    }
}
