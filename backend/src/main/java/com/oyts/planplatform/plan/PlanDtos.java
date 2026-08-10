package com.oyts.planplatform.plan;

import com.oyts.planplatform.plan.domain.TaskProgress;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class PlanDtos {
    private PlanDtos() {}

    public record PlanRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 60) String overallTagName,
        @Size(max = 20) String overallTagColor,
        Long version
    ) {}
    public record PlanResponse(String id, String name, String description, LocalDate startDate, LocalDate endDate,
                               boolean archived, long version, long taskCount, long completedCount, Instant updatedAt) {}

    public record TagRequest(@NotBlank @Size(max = 60) String name, @NotBlank @Size(max = 20) String color, Long version) {}
    public record TagResponse(String id, String name, String color, boolean overall, long version) {}

    public record TaskRequest(
        String tagId,
        @NotNull LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        @Min(1) @Max(1440) int plannedMinutes,
        @NotBlank @Size(max = 40) String category,
        @NotBlank @Size(max = 180) String title,
        @Size(max = 2000) String description,
        List<@NotBlank @Size(max = 500) String> acceptance,
        @Size(max = 1000) String resourceUrl,
        boolean required,
        int sortOrder,
        Long version
    ) {}
    public record ProgressResponse(String status, long accumulatedSeconds, Instant startedAt, Instant completedAt,
                                   List<Boolean> acceptanceChecks, String note, LocalDate deferTo, String skipReason, long version) {}
    public record TaskResponse(String id, String planId, String tagId, String tagName, String tagColor, LocalDate date,
                               LocalTime startTime, LocalTime endTime, int plannedMinutes, String category, String title,
                               String description, List<String> acceptance, String resourceUrl, boolean required,
                               int sortOrder, long version, ProgressResponse progress) {}

    public record ProgressRequest(@NotNull TaskProgress.Status status, @Size(max = 2000) String note,
                                  List<Boolean> acceptanceChecks, LocalDate deferTo, @Size(max = 1000) String skipReason, Long version) {}
    public record CompleteRequest(@Size(max = 2000) String note, List<Boolean> acceptanceChecks, Long version) {}
    public record ManualTimeRequest(long seconds, Long version) {}

    public record ReflectionRequest(@Size(max = 2000) String completedSummary, @Size(max = 2000) String unfinishedSummary,
                                    @Size(max = 2000) String blockers, @Size(max = 1000) String firstTaskTomorrow,
                                    @Min(1) @Max(5) Integer energy, @Size(max = 2000) String note, Long version) {}
    public record ReflectionResponse(String id, LocalDate date, String completedSummary, String unfinishedSummary,
                                     String blockers, String firstTaskTomorrow, Integer energy, String note, long version) {}

    public record TodayResponse(LocalDate date, List<TaskResponse> tasks, long total, long completed,
                                long plannedMinutes, long actualSeconds) {}
    public record StatsResponse(LocalDate from, LocalDate to, long taskCount, long completedCount,
                                long plannedMinutes, long actualSeconds, List<TagStat> tags) {}
    public record TagStat(String tagId, String tagName, String color, long taskCount, long completedCount,
                          long plannedMinutes, long actualSeconds) {}
}
