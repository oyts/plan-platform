package com.oyts.planplatform.plan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "time_entries")
public class TimeEntry {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "progress_id") private TaskProgress progress;
    @Column(nullable = false) private Instant startedAt;
    @Column(nullable = false) private Instant endedAt;
    @Column(nullable = false) private long seconds;
    @Column(nullable = false, length = 20) private String source;
    @Column(nullable = false, updatable = false) private Instant createdAt;

    protected TimeEntry() {}
    public TimeEntry(TaskProgress progress, Instant startedAt, Instant endedAt, long seconds, String source) {
        this.progress = progress; this.startedAt = startedAt; this.endedAt = endedAt; this.seconds = seconds; this.source = source;
    }
    @PrePersist void prePersist() { id = UUID.randomUUID().toString(); createdAt = Instant.now(); }
    public String getId() { return id; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public long getSeconds() { return seconds; }
    public String getSource() { return source; }
}
