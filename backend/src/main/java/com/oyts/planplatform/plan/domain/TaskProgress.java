package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "task_progress")
public class TaskProgress extends BaseEntity {
    public enum Status { PENDING, RUNNING, PAUSED, COMPLETED, PARTIAL, SKIPPED, DEFERRED }

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private PlanTask task;
    @Column(nullable = false, length = 30)
    private String status = Status.PENDING.name();
    @Column(name = "accumulated_seconds", nullable = false)
    private long accumulatedSeconds;
    private Instant startedAt;
    private Instant completedAt;
    @Column(name = "acceptance_json", nullable = false, columnDefinition = "text")
    private String acceptanceJson = "[]";
    @Column(nullable = false, length = 2000)
    private String note = "";
    private LocalDate deferTo;
    @Column(name = "skip_reason", nullable = false, length = 1000)
    private String skipReason = "";

    protected TaskProgress() {}
    public TaskProgress(PlanTask task) { this.task = task; }

    public void start(Instant now) { status = Status.RUNNING.name(); startedAt = now; }
    public long pause(Instant now) {
        long added = startedAt == null ? 0 : Math.max(0, java.time.Duration.between(startedAt, now).getSeconds());
        accumulatedSeconds += added;
        status = Status.PAUSED.name();
        startedAt = null;
        return added;
    }
    public void complete(String note, String acceptanceJson, Instant now) {
        if (Status.RUNNING.name().equals(status)) pause(now);
        status = Status.COMPLETED.name();
        this.note = note == null ? "" : note.trim();
        this.acceptanceJson = acceptanceJson;
        this.completedAt = now;
        this.deferTo = null;
    }
    public void update(Status status, String note, String acceptanceJson, LocalDate deferTo, String skipReason) {
        this.status = status.name();
        this.note = note == null ? "" : note.trim();
        this.acceptanceJson = acceptanceJson;
        this.deferTo = deferTo;
        this.skipReason = skipReason == null ? "" : skipReason.trim();
        if (status != Status.RUNNING) startedAt = null;
        if (status != Status.COMPLETED) completedAt = null;
    }
    public void addSeconds(long seconds) { accumulatedSeconds = Math.max(0, accumulatedSeconds + seconds); }

    public PlanTask getTask() { return task; }
    public Status getStatus() { return Status.valueOf(status); }
    public long getAccumulatedSeconds() { return accumulatedSeconds; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getAcceptanceJson() { return acceptanceJson; }
    public String getNote() { return note; }
    public LocalDate getDeferTo() { return deferTo; }
    public String getSkipReason() { return skipReason; }
}
