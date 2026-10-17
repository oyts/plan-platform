package com.oyts.planplatform.daily;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.*;

@Entity @Table(name = "reminder_preferences")
public class ReminderPreference extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private boolean includePlans = true;
    @Column(nullable = false) private boolean includeTodos = true;
    @Column(nullable = false) private boolean showOverdue = true;
    @Column(nullable = false) private int lookAheadDays = 3;
    protected ReminderPreference() {}
    ReminderPreference(UserAccount owner) { this.owner = owner; }
    void update(DailyDtos.ReminderRequest r) {
        enabled = r.enabled(); includePlans = r.includePlans(); includeTodos = r.includeTodos();
        showOverdue = r.showOverdue(); lookAheadDays = r.lookAheadDays();
    }
    DailyDtos.Reminders response() { return new DailyDtos.Reminders(enabled, includePlans, includeTodos, showOverdue, lookAheadDays, getVersion()); }
}
