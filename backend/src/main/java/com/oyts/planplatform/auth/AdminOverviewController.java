package com.oyts.planplatform.auth;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;

/** Navigation/metadata only: no private business tables or model text. */
@RestController @RequestMapping("/api/admin") @PreAuthorize("hasRole('ADMIN')")
class AdminOverviewController {
    private final JdbcTemplate db;AdminOverviewController(JdbcTemplate db){this.db=db;}
    record Access(boolean allowed) {}
    record Overview(Instant observedAt,long members,long enabledMembers,long activeInvitations,long queuedAi,long runningAi,long openFeedback) {}
    @GetMapping("/access") ResponseEntity<Access> access(){return reply(new Access(true));}
    @GetMapping("/overview") ResponseEntity<Overview> overview(){return reply(new Overview(
        Instant.now(),count("SELECT COUNT(*) FROM users"),
        count("SELECT COUNT(*) FROM users WHERE enabled=TRUE"),
        count("SELECT COUNT(*) FROM invitations WHERE revoked_at IS NULL AND accepted_at IS NULL AND expires_at>CURRENT_TIMESTAMP"),
        count("SELECT COUNT(*) FROM ai_jobs WHERE state='QUEUED'"),
        count("SELECT COUNT(*) FROM ai_jobs WHERE state='RUNNING'"),
        count("SELECT COUNT(*) FROM feedback_tickets WHERE status IN ('OPEN','IN_PROGRESS')")));}
    private long count(String sql){return db.queryForObject(sql,Long.class);}
    private <T> ResponseEntity<T> reply(T value){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);}
}
