package com.oyts.planplatform.plan.domain;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "plans")
public class Plan extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description = "";

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean archived;

    protected Plan() {}

    public Plan(UserAccount owner, String name, String description, LocalDate startDate, LocalDate endDate) {
        this.owner = owner;
        update(name, description, startDate, endDate);
    }

    public void update(String name, String description, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("计划结束日期不能早于开始日期。");
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void archive() { archived = true; }
    public UserAccount getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isArchived() { return archived; }
}
