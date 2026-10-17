package com.oyts.planplatform.daily;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity @Table(name = "weekly_reviews", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "week_start"}))
public class WeeklyReview extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(nullable = false) private LocalDate weekStart;
    @Column(nullable = false, length = 4000) private String wins = "";
    @Column(nullable = false, length = 4000) private String blockers = "";
    @Column(nullable = false, length = 4000) private String nextFocus = "";
    protected WeeklyReview() {}
    WeeklyReview(UserAccount owner, LocalDate weekStart) { this.owner = owner; this.weekStart = weekStart; }
    void update(DailyDtos.ReviewRequest r) { wins = clean(r.wins()); blockers = clean(r.blockers()); nextFocus = clean(r.nextFocus()); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    DailyDtos.Review response() { return new DailyDtos.Review(weekStart, wins, blockers, nextFocus, getVersion(), getUpdatedAt()); }
}
