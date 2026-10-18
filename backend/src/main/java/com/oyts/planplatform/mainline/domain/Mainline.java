package com.oyts.planplatform.mainline.domain;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mainlines")
public class Mainline extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 1000) private String description = "";
    @Column(nullable = false, length = 20) private String color;
    @Column(nullable = false) private boolean archived;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Column(name = "execution_mode", nullable = false, length = 20) private String executionMode = "SEQUENTIAL";
    @Column(nullable=false) private boolean paused;
    protected Mainline() {}
    public Mainline(UserAccount owner, String name, String description, String color) { this.owner = owner; update(name, description, color); }
    public void update(String name, String description, String color) { this.name = name.trim(); this.description = description == null ? "" : description.trim(); this.color = color.trim(); }
    public void archive() { archived = true; }
    public void trash() { deletedAt = Instant.now(); }
    public void restore() { deletedAt = null; archived = false; }
    public String getName() { return name; } public String getDescription() { return description; }
    public String getColor() { return color; } public boolean isArchived() { return archived; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getExecutionMode() { return executionMode; }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean value) { paused=value; }
    public void setExecutionMode(String mode) {
        if (!"SEQUENTIAL".equals(mode) && !"FLEXIBLE".equals(mode)) throw new IllegalArgumentException("请选择顺序推进或自由推进。");
        executionMode = mode;
    }
}
