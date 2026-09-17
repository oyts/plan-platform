package com.oyts.planplatform.auth;

import com.oyts.planplatform.common.TooManyRequestsException;
import com.oyts.planplatform.config.SecurityProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
class LoginAttemptService {
    private final ConcurrentHashMap<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final SecurityProperties properties;

    LoginAttemptService(SecurityProperties properties) { this.properties = properties; }

    void check(String email, String clientAddress) {
        checkBucket(accountKey(email, clientAddress), properties.maxLoginAttempts());
        checkBucket(addressKey(clientAddress), properties.maxLoginAttempts() * 5);
    }

    void failed(String email, String clientAddress) {
        record(accountKey(email, clientAddress));
        record(addressKey(clientAddress));
    }

    void succeeded(String email, String clientAddress) { failures.remove(accountKey(email, clientAddress)); }

    private void checkBucket(String key, int limit) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() >= limit) throw new TooManyRequestsException("登录失败次数过多，请稍后再试。");
        }
    }

    private void record(String key) {
        Deque<Instant> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) { prune(attempts); attempts.addLast(Instant.now()); }
        if (failures.size() > 10_000) failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) { prune(entry.getValue()); return entry.getValue().isEmpty(); }
        });
    }

    private void prune(Deque<Instant> attempts) {
        Instant threshold = Instant.now().minusSeconds(properties.loginWindowSeconds());
        while (!attempts.isEmpty() && attempts.getFirst().isBefore(threshold)) attempts.removeFirst();
    }

    private String accountKey(String email, String clientAddress) { return "account|" + email.strip().toLowerCase(Locale.ROOT) + '|' + address(clientAddress); }
    private String addressKey(String clientAddress) { return "address|" + address(clientAddress); }
    private String address(String clientAddress) { return clientAddress == null ? "unknown" : clientAddress; }
}
