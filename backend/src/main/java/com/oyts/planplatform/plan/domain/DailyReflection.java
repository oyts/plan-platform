package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "daily_reflections")
public class DailyReflection extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "plan_id") private Plan plan;
    @Column(name = "reflection_date", nullable = false) private LocalDate date;
    @Column(nullable = false, length = 2000) private String completedSummary = "";
    @Column(nullable = false, length = 2000) private String unfinishedSummary = "";
    @Column(nullable = false, length = 2000) private String blockers = "";
    @Column(nullable = false, length = 1000) private String firstTaskTomorrow = "";
    private Integer energy;
    @Column(nullable = false, length = 2000) private String note = "";

    protected DailyReflection() {}
    public DailyReflection(Plan plan, LocalDate date) { this.plan = plan; this.date = date; }
    public void update(String completed, String unfinished, String blockers, String firstTask, Integer energy, String note) {
        if (energy != null && (energy < 1 || energy > 5)) throw new IllegalArgumentException("精力评分必须在 1—5 之间。");
        this.completedSummary = value(completed); this.unfinishedSummary = value(unfinished); this.blockers = value(blockers);
        this.firstTaskTomorrow = value(firstTask); this.energy = energy; this.note = value(note);
    }
    private String value(String input) { return input == null ? "" : input.trim(); }
    public LocalDate getDate() { return date; }
    public String getCompletedSummary() { return completedSummary; }
    public String getUnfinishedSummary() { return unfinishedSummary; }
    public String getBlockers() { return blockers; }
    public String getFirstTaskTomorrow() { return firstTaskTomorrow; }
    public Integer getEnergy() { return energy; }
    public String getNote() { return note; }
}
