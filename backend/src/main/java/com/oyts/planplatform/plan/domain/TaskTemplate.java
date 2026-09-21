package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_templates")
public class TaskTemplate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 40) private String category;
    @Column(nullable = false, length = 180) private String title;
    @Column(nullable = false, length = 2000) private String description = "";
    @Column(name = "planned_minutes", nullable = false) private int plannedMinutes;
    @Column(name = "acceptance_json", nullable = false, columnDefinition = "text") private String acceptanceJson = "[]";
    @Column(name = "resource_url", nullable = false, length = 1000) private String resourceUrl = "";
    @Column(name = "required_task", nullable = false) private boolean required = true;

    protected TaskTemplate() {}
    public TaskTemplate(UserAccount owner, String name, String category, String title, String description, int plannedMinutes, String acceptanceJson, String resourceUrl, boolean required) {
        this.owner = owner; update(name, category, title, description, plannedMinutes, acceptanceJson, resourceUrl, required);
    }
    public void update(String name, String category, String title, String description, int plannedMinutes, String acceptanceJson, String resourceUrl, boolean required) {
        if (plannedMinutes < 1 || plannedMinutes > 1440) throw new IllegalArgumentException("模板计划时长必须在 1—1440 分钟之间。");
        this.name = name.trim(); this.category = category.trim().toUpperCase(); this.title = title.trim(); this.description = description == null ? "" : description.trim();
        this.plannedMinutes = plannedMinutes; this.acceptanceJson = acceptanceJson == null ? "[]" : acceptanceJson; this.resourceUrl = resourceUrl == null ? "" : resourceUrl.trim(); this.required = required;
    }
    public UserAccount getOwner() { return owner; } public String getName() { return name; } public String getCategory() { return category; }
    public String getTitle() { return title; } public String getDescription() { return description; } public int getPlannedMinutes() { return plannedMinutes; }
    public String getAcceptanceJson() { return acceptanceJson; } public String getResourceUrl() { return resourceUrl; } public boolean isRequired() { return required; }
}
