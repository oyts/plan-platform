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
