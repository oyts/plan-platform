package com.oyts.planplatform.mainline.domain;

import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "mainline_stages")
public class MainlineStage extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "mainline_id") private Mainline mainline;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 1000) private String description = "";
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    protected MainlineStage() {}
    public MainlineStage(Mainline line, String name, String description, int order) { mainline = line; update(name, description, order); }
    public void update(String name, String description, int order) { this.name = name.trim(); this.description = description == null ? "" : description.trim(); sortOrder = order; }
    public Mainline getMainline() { return mainline; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
