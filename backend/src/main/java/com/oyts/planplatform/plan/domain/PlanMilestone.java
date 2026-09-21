package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "plan_milestones")
public class PlanMilestone extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id") private Plan plan;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "milestone_date", nullable = false) private LocalDate date;
    @Column(nullable = false, length = 1000) private String description = "";
    @Column(name = "acceptance_json", nullable = false, columnDefinition = "text") private String acceptanceJson = "[]";
    @Column(name = "checks_json", nullable = false, columnDefinition = "text") private String checksJson = "[]";
    @Column(nullable = false, length = 2000) private String note = "";
    private Instant reviewedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    protected PlanMilestone() {}
    public PlanMilestone(Plan plan, String name, LocalDate date, String description, String acceptanceJson) {
        this.plan = plan; update(name, date, description, acceptanceJson);
    }
    public void update(String name, LocalDate date, String description, String acceptanceJson) {
        this.name = name.trim(); this.date = date; this.description = description == null ? "" : description.trim();
        this.acceptanceJson = acceptanceJson == null ? "[]" : acceptanceJson;
    }
    public void record(String checksJson, String note, Instant reviewedAt) {
        this.checksJson = checksJson == null ? "[]" : checksJson; this.note = note == null ? "" : note.trim(); this.reviewedAt = reviewedAt;
    }
    public Plan getPlan() { return plan; } public String getName() { return name; } public LocalDate getDate() { return date; }
    public String getDescription() { return description; } public String getAcceptanceJson() { return acceptanceJson; }
    public String getChecksJson() { return checksJson; } public String getNote() { return note; } public Instant getReviewedAt() { return reviewedAt; }
    public void trash() { deletedAt = Instant.now(); } public void restore() { deletedAt = null; } public Instant getDeletedAt() { return deletedAt; }
}
