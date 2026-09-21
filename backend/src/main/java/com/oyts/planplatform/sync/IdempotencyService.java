package com.oyts.planplatform.sync;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository records; private final UserAccountRepository users;
    IdempotencyService(IdempotencyRecordRepository records, UserAccountRepository users) { this.records = records; this.users = users; }
    @Transactional
    public IdempotencyDecision reserve(String userId, String key, String method, String path, String fingerprint) {
        records.deleteByExpiresAtBefore(Instant.now());
        IdempotencyRecord existing = records.findByOwnerIdAndOperationKey(userId, key).orElse(null);
        if (existing != null) return decision(existing, method, path, fingerprint);
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        IdempotencyRecord created = records.saveAndFlush(new IdempotencyRecord(owner, key, method, path, fingerprint, Instant.now().plus(24, ChronoUnit.HOURS)));
        return IdempotencyDecision.process(created.getId());
    }
    @Transactional(readOnly = true)
    public IdempotencyDecision lookup(String userId, String key, String method, String path, String fingerprint) {
        return records.findByOwnerIdAndOperationKey(userId, key).map(record -> decision(record, method, path, fingerprint)).orElseThrow(() -> new ConflictException("操作键正在被其他请求占用，请稍后重试。"));
    }
    @Transactional public void complete(String id, int status, String contentType, String body) { records.findById(id).ifPresent(record -> record.complete(status, contentType, body)); }
    @Transactional public void release(String id) { records.findById(id).ifPresent(records::delete); }
    private IdempotencyDecision decision(IdempotencyRecord record, String method, String path, String fingerprint) {
        if (!record.getRequestMethod().equals(method) || !record.getRequestPath().equals(path) || !record.getRequestFingerprint().equals(fingerprint)) throw new ConflictException("同一操作键不能用于不同请求内容。" );
        if (!record.isCompleted()) throw new ConflictException("该操作仍在处理中，请稍后重试。" );
        return IdempotencyDecision.replay(record.getResponseStatus(), record.getResponseContentType(), record.getResponseBody());
    }
    public record IdempotencyDecision(boolean replay, String recordId, int status, String contentType, String body) {
        static IdempotencyDecision process(String id) { return new IdempotencyDecision(false, id, 0, null, null); }
        static IdempotencyDecision replay(int status, String contentType, String body) { return new IdempotencyDecision(true, null, status, contentType, body); }
    }
}
