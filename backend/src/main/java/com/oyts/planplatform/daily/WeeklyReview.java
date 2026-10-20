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
    @Column(nullable=false,length=16) private String energy="UNSPECIFIED";
    @Column(nullable=false,length=16) private String burden="UNSPECIFIED";
    @Column(nullable=false) private boolean reviewed;
    @Column(nullable=false) private boolean openToIncrease;
    protected WeeklyReview() {}
    WeeklyReview(UserAccount owner, LocalDate weekStart) { this.owner = owner; this.weekStart = weekStart; }
    void update(DailyDtos.ReviewRequest r) { wins = clean(r.wins()); blockers = clean(r.blockers()); nextFocus = clean(r.nextFocus());
        if(r.energy()!=null)energy=r.energy();if(r.burden()!=null)burden=r.burden();reviewed=r.reviewed()==null||r.reviewed();if(r.openToIncrease()!=null)openToIncrease=r.openToIncrease(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    DailyDtos.Review response() { return new DailyDtos.Review(weekStart, wins, blockers, nextFocus, getVersion(), getUpdatedAt(),energy,burden,reviewed,openToIncrease); }
}
