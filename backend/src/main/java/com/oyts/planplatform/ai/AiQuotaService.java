package com.oyts.planplatform.ai;

import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.common.TooManyRequestsException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quota admission is short and transactional. Never hold this lock during model inference. */
@Service
public class AiQuotaService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate db;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public AiQuotaService(JdbcTemplate db) { this(db, Clock.systemUTC()); }
    AiQuotaService(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }
    public record Policy(int dailyLimit, long version) {}
    public record Usage(String date, String resetsAt, int dailyLimit, Integer overrideLimit, boolean paused,
                        int used, int pending, int remaining, long version) {}
    public record Member(String id, String displayName, String email, String role, Usage quota) {}
    public record Page<T>(List<T> items, int page, int size, long total) {}
    public record Audit(String actor, String target, String changeType, Integer beforeLimit, Integer afterLimit,
                        boolean beforePaused, boolean afterPaused, Instant createdAt) {}
    public record Reservation(String id, String state, String quotaDay, boolean acquired) {}
    private record Override(Integer limit, boolean paused, long version) {}

    @Transactional(readOnly = true)
    public Policy policy() { return policy(false); }
    private Policy policy(boolean lock) {
        return db.queryForObject("SELECT daily_limit, version FROM ai_quota_policy WHERE id=1" + (lock ? " FOR UPDATE" : ""),
            (rs, n) -> new Policy(rs.getInt(1), rs.getLong(2)));
    }
    private Override override(String owner) {
        var rows = db.query("SELECT daily_limit, paused, version FROM ai_user_quota WHERE owner_id=?",
            (rs, n) -> new Override((Integer) rs.getObject(1), rs.getBoolean(2), rs.getLong(3)), owner);
        return rows.isEmpty() ? new Override(null, false, 0) : rows.getFirst();
    }
    private void requireUser(String id) {
        if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND enabled=true", Integer.class, id) != 1)
            throw new NotFoundException("成员不存在。");
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZONE)); }
    private Usage usage(String owner, Policy policy, LocalDate day) {
        var rule = override(owner);
        var counts = db.queryForObject("SELECT COALESCE(SUM(CASE WHEN state='SUCCEEDED' THEN 1 ELSE 0 END),0), "
                + "COALESCE(SUM(CASE WHEN state IN ('RESERVED','UNKNOWN') THEN 1 ELSE 0 END),0) "
                + "FROM ai_quota_requests WHERE owner_id=? AND quota_day=?",
            (rs, n) -> new int[]{rs.getInt(1), rs.getInt(2)}, owner, day);
        int limit = rule.limit == null ? policy.dailyLimit : rule.limit;
        return new Usage(day.toString(), day.plusDays(1).atStartOfDay(ZONE).toInstant().toString(), limit, rule.limit,
            rule.paused, counts[0], counts[1], rule.paused ? 0 : Math.max(0, limit - counts[0] - counts[1]), rule.version);
    }
    @Transactional(readOnly = true)
    public Usage usage(String owner) { requireUser(owner); return usage(owner, policy(false), today()); }
    @Transactional(readOnly = true)
    public Page<Member> members(String query, int page, int size) {
        paging(page, size);
        if (query == null || query.length() > 100) throw new IllegalArgumentException("搜索内容最多 100 字。");
        String search = "%" + query.trim().toLowerCase(java.util.Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String where = " FROM users WHERE enabled=true AND (LOWER(email) LIKE ? ESCAPE '!' OR LOWER(display_name) LIKE ? ESCAPE '!')";
        long total = db.queryForObject("SELECT COUNT(*)" + where, Long.class, search, search);
        var current = policy(false); var day = today();
        var rows = db.query("SELECT id, display_name, email, role" + where + " ORDER BY created_at, id LIMIT ? OFFSET ?",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)}, search, search, size, page * size);
        return new Page<>(rows.stream().map(row -> new Member(row[0], row[1], row[2], row[3], usage(row[0], current, day))).toList(), page, size, total);
    }
    @Transactional
    public Policy changePolicy(String actor, int limit, long version) {
        limit(limit); var before = policy(true); requireUser(actor);
        if (before.version != version) throw new ConflictException("默认额度已被修改，请刷新后重试。");
        db.update("UPDATE ai_quota_policy SET daily_limit=?,version=version+1,updated_at=? WHERE id=1", limit, timestamp());
        audit(actor, null, "DEFAULT", before.dailyLimit, limit, false, false);
        return new Policy(limit, version + 1);
    }
    @Transactional
    public Usage changeUser(String actor, String owner, Integer limit, boolean paused, long version) {
        if (limit != null) limit(limit);
        var current = policy(true); requireUser(actor); requireUser(owner);
        var before = override(owner);
        if (before.version != version) throw new ConflictException("成员额度已被修改，请刷新后重试。");
        if (db.queryForObject("SELECT COUNT(*) FROM ai_user_quota WHERE owner_id=?", Integer.class, owner) == 0)
            db.update("INSERT INTO ai_user_quota(owner_id,daily_limit,paused,version) VALUES (?,?,?,1)", owner, limit, paused);
        else db.update("UPDATE ai_user_quota SET daily_limit=?,paused=?,version=version+1 WHERE owner_id=?", limit, paused, owner);
        audit(actor, owner, "USER", before.limit, limit, before.paused, paused);
        return usage(owner, current, today());
    }
    @Transactional(readOnly = true)
    public Page<Audit> history(int page, int size) {
        paging(page, size);
        long total = db.queryForObject("SELECT COUNT(*) FROM ai_quota_audit", Long.class);
        var rows = db.query("SELECT a.display_name, t.email, q.change_type,q.before_limit,q.after_limit,q.before_paused,q.after_paused,q.created_at "
            + "FROM ai_quota_audit q JOIN users a ON a.id=q.actor_id LEFT JOIN users t ON t.id=q.target_id ORDER BY q.created_at DESC,q.id LIMIT ? OFFSET ?",
            (rs, n) -> new Audit(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), (Integer) rs.getObject(5), rs.getBoolean(6), rs.getBoolean(7), rs.getTimestamp(8).toInstant()), size, page * size);
        return new Page<>(rows, page, size, total);
    }
    /** Internal only: clients cannot reserve or refund quota through a public endpoint. */
    @Transactional
    public Reservation reserve(String owner, String operationKey, String fingerprint) {
        if (operationKey == null || !operationKey.matches("[A-Za-z0-9._:-]{8,100}") || fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("AI 请求标识无效。");
        var current = policy(true); requireUser(owner);
        var prior = db.query("SELECT id,state,quota_day,fingerprint FROM ai_quota_requests WHERE owner_id=? AND operation_key=?",
            (rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getDate(3).toLocalDate().toString(), rs.getString(4)}, owner, operationKey);
        if (!prior.isEmpty()) {
            var row = prior.getFirst();
            if (!row[3].equals(fingerprint)) throw new ConflictException("同一 AI 请求标识不能用于不同内容。");
            return new Reservation(row[0], row[1], row[2], false);
        }
        var day = today(); var quota = usage(owner, current, day);
        if (quota.paused || quota.remaining == 0) throw new TooManyRequestsException("今日 AI 对话额度不足或已暂停，请联系管理员。");
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO ai_quota_requests(id,owner_id,operation_key,fingerprint,quota_day,state,created_at,updated_at) VALUES (?,?,?,?,?,'RESERVED',?,?)",
            id, owner, operationKey, fingerprint, day, timestamp(), timestamp());
        return new Reservation(id, "RESERVED", day.toString(), true);
    }
    /** Unknown upstream results stay charged as pending; never auto-refund ambiguous paid calls. */
    @Transactional
    public void settle(String owner, String id, String state) {
        if (!List.of("SUCCEEDED", "RELEASED", "UNKNOWN").contains(state)) throw new IllegalArgumentException("无效的 AI 结算状态。");
        policy(true); requireUser(owner);
        var prior = db.query("SELECT state FROM ai_quota_requests WHERE id=? AND owner_id=?", (rs, n) -> rs.getString(1), id, owner);
        if (prior.isEmpty()) throw new NotFoundException("AI 请求不存在。");
        String from = prior.getFirst();
        if (from.equals(state)) return;
        if (!from.equals("RESERVED") && !from.equals("UNKNOWN")) throw new ConflictException("此 AI 请求已结算，不能重复扣次或退回。");
        db.update("UPDATE ai_quota_requests SET state=?,updated_at=? WHERE id=? AND owner_id=?", state, timestamp(), id, owner);
    }
    private Timestamp timestamp() { return Timestamp.from(clock.instant()); }
    private void audit(String actor, String target, String type, Integer before, Integer after, boolean beforePaused, boolean afterPaused) {
        db.update("INSERT INTO ai_quota_audit(id,actor_id,target_id,change_type,before_limit,after_limit,before_paused,after_paused,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(), actor, target, type, before, after, beforePaused, afterPaused, timestamp());
    }
    private static void limit(int value) { if (value < 0 || value > 10000) throw new IllegalArgumentException("每日额度必须为 0 至 10000 的整数。"); }
    private static void paging(int page, int size) { if (page < 0 || page > 100000 || size < 1 || size > 50) throw new IllegalArgumentException("分页范围无效。"); }
}
