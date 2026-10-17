package com.oyts.planplatform.daily;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;

public final class DailyDtos {
    private DailyDtos() {}
    public record Counts(long today, long overdue, long upcoming) {}
    public record Summary(LocalDate date, LocalDate through, Counts plan, Counts todo, long activeRoutes) {}
    public record Item(String kind, String id, String parentId, String title, String parentName, LocalDate dueDate, String status) {}
    public record Route(String id, String name, long total, long completed) {}
    public record Reminders(boolean enabled, boolean includePlans, boolean includeTodos, boolean showOverdue, int lookAheadDays, Long version) {}
    public record ReminderRequest(@NotNull Boolean enabled, @NotNull Boolean includePlans, @NotNull Boolean includeTodos,
        @NotNull Boolean showOverdue, @NotNull @Min(1) @Max(14) Integer lookAheadDays, @PositiveOrZero Long version) {}
    public record Review(LocalDate weekStart, String wins, String blockers, String nextFocus, Long version, Instant updatedAt) {}
    public record ReviewRequest(@Size(max = 4000) String wins, @Size(max = 4000) String blockers,
        @Size(max = 4000) String nextFocus, @PositiveOrZero Long version) {}
    public record Week(LocalDate start, LocalDate end, Instant calculatedAt, long planScheduled, long planCompleted,
        long todoScheduled, long todoCompleted, long routeCompleted, long recordedPlanSeconds,
        long openPlansThroughEnd, long openTodosThroughEnd, long legacyCompletedTodosUntimed) {}
}
