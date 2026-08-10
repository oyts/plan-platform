package com.oyts.planplatform.mainline.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "mainline_items")
public class MainlineItem extends BaseEntity {
    public enum Status { PENDING, RUNNING, PAUSED, COMPLETED }
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mainline_id") private Mainline mainline;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, length = 2000) private String description = "";
    @Column(name = "acceptance_json", nullable = false, columnDefinition = "text") private String acceptanceJson = "[]";
    @Column(name = "planned_minutes", nullable = false) private int plannedMinutes;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false, length = 30) private String status = Status.PENDING.name();
    @Column(name = "accumulated_seconds", nullable = false) private long accumulatedSeconds;
    @Column(nullable = false, length = 2000) private String note = "";
    private Instant startedAt;
    private Instant completedAt;
    protected MainlineItem() {}
    public MainlineItem(Mainline mainline) { this.mainline = mainline; }
    public void update(String title, String description, String acceptanceJson, int plannedMinutes, int sortOrder) {
        if (plannedMinutes <= 0) throw new IllegalArgumentException("计划时长必须大于 0。");
        this.title = title.trim(); this.description = description == null ? "" : description.trim(); this.acceptanceJson = acceptanceJson;
        this.plannedMinutes = plannedMinutes; this.sortOrder = sortOrder;
    }
    public void start() { status = Status.RUNNING.name(); startedAt = Instant.now(); }
    public void pause() { if (startedAt != null) accumulatedSeconds += Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds()); startedAt = null; status = Status.PAUSED.name(); }
    public void complete(String note) { if (startedAt != null) pause(); status = Status.COMPLETED.name(); completedAt = Instant.now(); this.note = note == null ? "" : note.trim(); }
    public Mainline getMainline() { return mainline; } public String getTitle() { return title; }
    public String getDescription() { return description; } public String getAcceptanceJson() { return acceptanceJson; }
    public int getPlannedMinutes() { return plannedMinutes; } public int getSortOrder() { return sortOrder; }
    public Status getStatus() { return Status.valueOf(status); } public long getAccumulatedSeconds() { return accumulatedSeconds; }
    public String getNote() { return note; } public Instant getStartedAt() { return startedAt; } public Instant getCompletedAt() { return completedAt; }
}
