package com.oyts.planplatform.plan;

import com.oyts.planplatform.plan.domain.TaskProgress;
import jakarta.validation.Valid;
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
                               int sortOrder, String recurrenceId, String templateId, long version, ProgressResponse progress) {}

    /** A stable, zero-based page contract for long-running plans. */
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}

    public record ProgressRequest(@NotNull TaskProgress.Status status, @Size(max = 2000) String note,
                                  List<Boolean> acceptanceChecks, LocalDate deferTo, @Size(max = 1000) String skipReason, Long version) {}
    public record CompleteRequest(@Size(max = 2000) String note, List<Boolean> acceptanceChecks, Long version) {}
    public record ManualTimeRequest(long seconds, Long version) {}
    public record DeferRequest(@NotNull LocalDate deferTo, @Size(max = 2000) String note, Long version) {}
    public enum RecurrenceMode { DAILY, WEEKDAYS, WEEKLY }
    public record RecurringTaskRequest(@NotNull @Valid TaskRequest task, @NotNull RecurrenceMode mode, @NotNull LocalDate until) {}

    public record MilestoneRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate date,
                                   @Size(max = 1000) String description, List<@NotBlank @Size(max = 500) String> acceptance, Long version) {}
    public record MilestoneProgressRequest(List<Boolean> checks, @Size(max = 2000) String note, Long version) {}
    public record MilestoneResponse(String id, String planId, String name, LocalDate date, String description,
                                    List<String> acceptance, List<Boolean> checks, String note, Instant reviewedAt, long version) {}

    public record TemplateRequest(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 40) String category,
                                  @NotBlank @Size(max = 180) String title, @Size(max = 2000) String description,
                                  @Min(1) @Max(1440) int plannedMinutes, List<@NotBlank @Size(max = 500) String> acceptance,
                                  @Size(max = 1000) String resourceUrl, boolean required, Long version) {}
    public record TemplateResponse(String id, String name, String category, String title, String description, int plannedMinutes,
                                   List<String> acceptance, String resourceUrl, boolean required, long version) {}
    public record InstantiateTemplateRequest(@NotBlank String planId, String tagId, @NotNull LocalDate date, LocalTime startTime,
                                             LocalTime endTime, Integer sortOrder) {}

    public record ReflectionRequest(@Size(max = 2000) String completedSummary, @Size(max = 2000) String unfinishedSummary,
                                    @Size(max = 2000) String blockers, @Size(max = 1000) String firstTaskTomorrow,
                                    @Min(1) @Max(5) Integer energy, @Size(max = 2000) String note, Long version) {}
    public record ReflectionResponse(String id, LocalDate date, String completedSummary, String unfinishedSummary,
                                     String blockers, String firstTaskTomorrow, Integer energy, String note, long version) {}
    public record TimeEntryResponse(String id, String planId, String taskId, String taskTitle, String tagName, String tagColor,
                                    Instant startedAt, Instant endedAt, long seconds, String source) {}
    public record TrashResponse(String type, String id, String title, String detail, Instant deletedAt) {}

    public record TodayResponse(LocalDate date, List<TaskResponse> tasks, long total, long completed,
                                long plannedMinutes, long actualSeconds) {}
    public record StatsResponse(LocalDate from, LocalDate to, long taskCount, long completedCount,
                                long plannedMinutes, long actualSeconds, List<TagStat> tags) {}
    public record TagStat(String tagId, String tagName, String color, long taskCount, long completedCount,
                          long plannedMinutes, long actualSeconds) {}
    public record DailyStat(String date, long taskCount, long completedCount, long partialCount, long skippedCount,
                            long deferredCount, long plannedMinutes, long actualSeconds) {}
}
