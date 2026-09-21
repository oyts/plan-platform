package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "plan_tasks")
public class PlanTask extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private PlanTag tag;

    @Column(name = "task_date", nullable = false)
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    @Column(nullable = false)
    private int plannedMinutes;
    @Column(nullable = false, length = 40)
    private String category;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description = "";
    @Column(name = "acceptance_json", nullable = false, columnDefinition = "text")
    private String acceptanceJson = "[]";
    @Column(name = "resource_url", nullable = false, length = 1000)
    private String resourceUrl = "";
    @Column(name = "required_task", nullable = false)
    private boolean required = true;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "recurrence_id", length = 36)
    private String recurrenceId;
    @Column(name = "template_id", length = 36)
    private String templateId;
    @Column(name = "deleted_at") private Instant deletedAt;

    @OneToOne(mappedBy = "task", fetch = FetchType.LAZY)
    private TaskProgress progress;

    protected PlanTask() {}

    public PlanTask(Plan plan) { this.plan = plan; }

    public void update(PlanTag tag, LocalDate date, LocalTime startTime, LocalTime endTime, int plannedMinutes,
                       String category, String title, String description, String acceptanceJson,
                       String resourceUrl, boolean required, int sortOrder) {
        if (plannedMinutes <= 0) throw new IllegalArgumentException("计划时长必须大于 0。");
        this.tag = tag;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.plannedMinutes = plannedMinutes;
        this.category = category;
        this.title = title.trim();
        this.description = description == null ? "" : description.trim();
        this.acceptanceJson = acceptanceJson;
        this.resourceUrl = resourceUrl == null ? "" : resourceUrl.trim();
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public Plan getPlan() { return plan; }
    public PlanTag getTag() { return tag; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getPlannedMinutes() { return plannedMinutes; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAcceptanceJson() { return acceptanceJson; }
    public String getResourceUrl() { return resourceUrl; }
    public boolean isRequired() { return required; }
    public int getSortOrder() { return sortOrder; }
    public void assignRecurrence(String recurrenceId) { this.recurrenceId = recurrenceId; }
    public void assignTemplate(String templateId) { this.templateId = templateId; }
    public String getRecurrenceId() { return recurrenceId; }
    public String getTemplateId() { return templateId; }
    public void trash() { deletedAt = Instant.now(); }
    public void restore() { deletedAt = null; }
    public Instant getDeletedAt() { return deletedAt; }
}
