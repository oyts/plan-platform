package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_tags")
public class PlanTag extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, length = 20)
    private String color;

    @Column(name = "overall_tag", nullable = false)
    private boolean overall;

    protected PlanTag() {}

    public PlanTag(Plan plan, String name, String color, boolean overall) {
        this.plan = plan;
        update(name, color);
        this.overall = overall;
    }

    public void update(String name, String color) {
        this.name = name.trim();
        this.color = color.trim();
    }

    public Plan getPlan() { return plan; }
    public String getName() { return name; }
    public String getColor() { return color; }
    public boolean isOverall() { return overall; }
}
