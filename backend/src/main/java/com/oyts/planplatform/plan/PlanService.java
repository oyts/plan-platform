package com.oyts.planplatform.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.plan.domain.DailyReflection;
import com.oyts.planplatform.plan.domain.Plan;
import com.oyts.planplatform.plan.domain.PlanTag;
import com.oyts.planplatform.plan.domain.PlanTask;
import com.oyts.planplatform.plan.domain.TaskProgress;
import com.oyts.planplatform.plan.domain.TimeEntry;
import com.oyts.planplatform.plan.repository.DailyReflectionRepository;
import com.oyts.planplatform.plan.repository.PlanRepository;
import com.oyts.planplatform.plan.repository.PlanTagRepository;
import com.oyts.planplatform.plan.repository.PlanTaskRepository;
import com.oyts.planplatform.plan.repository.TaskProgressRepository;
import com.oyts.planplatform.plan.repository.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Boolean>> BOOLEAN_LIST = new TypeReference<>() {};
    private final UserAccountRepository users;
    private final PlanRepository plans;
    private final PlanTagRepository tags;
    private final PlanTaskRepository tasks;
    private final TaskProgressRepository progress;
    private final TimeEntryRepository timeEntries;
    private final DailyReflectionRepository reflections;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public PlanService(UserAccountRepository users, PlanRepository plans, PlanTagRepository tags, PlanTaskRepository tasks,
                       TaskProgressRepository progress, TimeEntryRepository timeEntries,
                       DailyReflectionRepository reflections, ObjectMapper objectMapper, EntityManager entityManager) {
        this.users = users; this.plans = plans; this.tags = tags; this.tasks = tasks; this.progress = progress;
        this.timeEntries = timeEntries; this.reflections = reflections; this.objectMapper = objectMapper; this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.PlanResponse> listPlans(String userId) {
        return plans.findByOwnerIdAndArchivedFalseOrderByStartDateDesc(userId).stream().map(this::toPlan).toList();
    }

    @Transactional
    public PlanDtos.PlanResponse createPlan(String userId, PlanDtos.PlanRequest request) {
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        Plan plan = plans.save(new Plan(owner, request.name(), request.description(), request.startDate(), request.endDate()));
        String overallName = blank(request.overallTagName()) ? "总计" : request.overallTagName();
        String color = blank(request.overallTagColor()) ? "#002FA7" : request.overallTagColor();
        tags.save(new PlanTag(plan, overallName, color, true));
        return toPlan(plan);
    }

    @Transactional
    public PlanDtos.PlanResponse updatePlan(String userId, String planId, PlanDtos.PlanRequest request) {
        Plan plan = ownedPlan(userId, planId);
        checkVersion(plan.getVersion(), request.version());
        plan.update(request.name(), request.description(), request.startDate(), request.endDate());
        flushAndRefresh(plan);
        return toPlan(plan);
    }

    @Transactional
    public void archivePlan(String userId, String planId) { ownedPlan(userId, planId).archive(); }

    @Transactional(readOnly = true)
    public List<PlanDtos.TagResponse> listTags(String userId, String planId) {
        ownedPlan(userId, planId);
        return tags.findByPlanIdOrderByOverallDescCreatedAtAsc(planId).stream().map(this::toTag).toList();
    }

    @Transactional
    public PlanDtos.TagResponse createTag(String userId, String planId, PlanDtos.TagRequest request) {
        Plan plan = ownedPlan(userId, planId);
        return toTag(tags.save(new PlanTag(plan, request.name(), request.color(), false)));
    }

    @Transactional
    public PlanDtos.TagResponse updateTag(String userId, String tagId, PlanDtos.TagRequest request) {
        PlanTag tag = ownedTag(userId, tagId);
        checkVersion(tag.getVersion(), request.version());
        tag.update(request.name(), request.color());
        flushAndRefresh(tag);
        return toTag(tag);
    }

    @Transactional
    public void deleteTag(String userId, String tagId) {
        PlanTag tag = ownedTag(userId, tagId);
        if (tag.isOverall()) throw new IllegalArgumentException("总标签不能删除。");
        if (tasks.countByTagId(tagId) > 0) throw new ConflictException("该标签仍被任务使用，请先修改任务标签。");
        tags.delete(tag);
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.TaskResponse> listTasks(String userId, String planId) {
        ownedPlan(userId, planId);
        return tasks.findByPlanIdOrderByDateAscSortOrderAsc(planId).stream().map(this::toTask).toList();
    }

    @Transactional
    public PlanDtos.TaskResponse createTask(String userId, String planId, PlanDtos.TaskRequest request) {
        Plan plan = ownedPlan(userId, planId);
        validateTaskDate(plan, request.date());
        PlanTag tag = request.tagId() == null ? null : ownedTag(userId, request.tagId());
        if (tag != null && !tag.getPlan().getId().equals(planId)) throw new IllegalArgumentException("标签不属于当前计划。");
        PlanTask task = new PlanTask(plan);
        applyTask(task, tag, request);
        tasks.save(task);
        progress.save(new TaskProgress(task));
        return toTask(task);
    }

    @Transactional
    public PlanDtos.TaskResponse updateTask(String userId, String taskId, PlanDtos.TaskRequest request) {
        PlanTask task = ownedTask(userId, taskId);
        checkVersion(task.getVersion(), request.version());
        validateTaskDate(task.getPlan(), request.date());
        PlanTag tag = request.tagId() == null ? null : ownedTag(userId, request.tagId());
        if (tag != null && !tag.getPlan().getId().equals(task.getPlan().getId())) throw new IllegalArgumentException("标签不属于当前计划。");
        applyTask(task, tag, request);
        flushAndRefresh(task);
        return toTask(task);
    }

    @Transactional
    public void deleteTask(String userId, String taskId) { tasks.delete(ownedTask(userId, taskId)); }

    @Transactional
    public PlanDtos.ProgressResponse updateProgress(String userId, String taskId, PlanDtos.ProgressRequest request) {
        ownedTask(userId, taskId);
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), request.version());
        current.update(request.status(), request.note(), json(request.acceptanceChecks()), request.deferTo(), request.skipReason());
        flushAndRefresh(current);
        return toProgress(current);
    }

    @Transactional
    public PlanDtos.ProgressResponse start(String userId, String taskId, Long version) {
        ownedTask(userId, taskId);
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), version);
        current.start(Instant.now());
        flushAndRefresh(current);
        return toProgress(current);
    }

    @Transactional
    public PlanDtos.ProgressResponse pause(String userId, String taskId, Long version) {
        ownedTask(userId, taskId);
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), version);
        Instant started = current.getStartedAt();
        Instant ended = Instant.now();
        long seconds = current.pause(ended);
        if (started != null && seconds > 0) timeEntries.save(new TimeEntry(current, started, ended, seconds, "TIMER"));
        flushAndRefresh(current);
        return toProgress(current);
    }

    @Transactional
    public PlanDtos.ProgressResponse complete(String userId, String taskId, PlanDtos.CompleteRequest request) {
        ownedTask(userId, taskId);
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), request.version());
        Instant started = current.getStartedAt();
        Instant ended = Instant.now();
        if (started != null) {
            long seconds = Math.max(0, java.time.Duration.between(started, ended).getSeconds());
            if (seconds > 0) timeEntries.save(new TimeEntry(current, started, ended, seconds, "TIMER"));
        }
        current.complete(request.note(), json(request.acceptanceChecks()), ended);
        flushAndRefresh(current);
        return toProgress(current);
    }

    @Transactional
    public PlanDtos.ProgressResponse addManualTime(String userId, String taskId, PlanDtos.ManualTimeRequest request) {
        ownedTask(userId, taskId);
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), request.version());
        current.addSeconds(request.seconds());
        Instant now = Instant.now();
        if (request.seconds() != 0) timeEntries.save(new TimeEntry(current, now, now, request.seconds(), "MANUAL"));
        flushAndRefresh(current);
        return toProgress(current);
    }

    @Transactional(readOnly = true)
    public PlanDtos.TodayResponse today(String userId, LocalDate date) {
        List<PlanDtos.TaskResponse> rows = tasks.findByPlanOwnerIdAndDateOrderByStartTimeAsc(userId, date).stream().map(this::toTask).toList();
        return new PlanDtos.TodayResponse(date, rows, rows.size(), rows.stream().filter(row -> row.progress().status().equals("COMPLETED")).count(),
            rows.stream().mapToLong(PlanDtos.TaskResponse::plannedMinutes).sum(), rows.stream().mapToLong(row -> row.progress().accumulatedSeconds()).sum());
    }

    @Transactional
    public PlanDtos.ReflectionResponse saveReflection(String userId, String planId, LocalDate date, PlanDtos.ReflectionRequest request) {
        Plan plan = ownedPlan(userId, planId);
        DailyReflection reflection = reflections.findByPlanIdAndDate(planId, date).orElseGet(() -> new DailyReflection(plan, date));
        checkVersion(reflection.getVersion(), request.version());
        reflection.update(request.completedSummary(), request.unfinishedSummary(), request.blockers(), request.firstTaskTomorrow(), request.energy(), request.note());
        reflections.save(reflection);
        flushAndRefresh(reflection);
        return toReflection(reflection);
    }

    @Transactional(readOnly = true)
    public PlanDtos.StatsResponse stats(String userId, String planId, LocalDate from, LocalDate to) {
        ownedPlan(userId, planId);
        List<PlanDtos.TaskResponse> rows = tasks.findByPlanIdOrderByDateAscSortOrderAsc(planId).stream()
            .filter(task -> !task.getDate().isBefore(from) && !task.getDate().isAfter(to)).map(this::toTask).toList();
        Map<String, List<PlanDtos.TaskResponse>> byTag = new LinkedHashMap<>();
        rows.forEach(row -> byTag.computeIfAbsent(row.tagId() == null ? "untagged" : row.tagId(), ignored -> new ArrayList<>()).add(row));
        List<PlanDtos.TagStat> tagStats = byTag.entrySet().stream().map(entry -> {
            List<PlanDtos.TaskResponse> values = entry.getValue();
            PlanDtos.TaskResponse first = values.getFirst();
            return new PlanDtos.TagStat(entry.getKey(), first.tagName() == null ? "未分类" : first.tagName(), first.tagColor(), values.size(),
                values.stream().filter(row -> row.progress().status().equals("COMPLETED")).count(),
                values.stream().mapToLong(PlanDtos.TaskResponse::plannedMinutes).sum(),
                values.stream().mapToLong(row -> row.progress().accumulatedSeconds()).sum());
        }).toList();
        return new PlanDtos.StatsResponse(from, to, rows.size(), rows.stream().filter(row -> row.progress().status().equals("COMPLETED")).count(),
            rows.stream().mapToLong(PlanDtos.TaskResponse::plannedMinutes).sum(), rows.stream().mapToLong(row -> row.progress().accumulatedSeconds()).sum(), tagStats);
    }

    private Plan ownedPlan(String userId, String planId) { return plans.findByIdAndOwnerId(planId, userId).orElseThrow(() -> new NotFoundException("计划不存在。")); }
    private PlanTag ownedTag(String userId, String tagId) { return tags.findByIdAndPlanOwnerId(tagId, userId).orElseThrow(() -> new NotFoundException("标签不存在。")); }
    private PlanTask ownedTask(String userId, String taskId) { return tasks.findByIdAndPlanOwnerId(taskId, userId).orElseThrow(() -> new NotFoundException("任务不存在。")); }
    private TaskProgress progressFor(String taskId) { return progress.findByTaskId(taskId).orElseThrow(() -> new NotFoundException("任务进度不存在。")); }

    private void applyTask(PlanTask task, PlanTag tag, PlanDtos.TaskRequest request) {
        task.update(tag, request.date(), request.startTime(), request.endTime(), request.plannedMinutes(), request.category().trim().toUpperCase(),
            request.title(), request.description(), json(request.acceptance()), request.resourceUrl(), request.required(), request.sortOrder());
    }
    private void validateTaskDate(Plan plan, LocalDate date) {
        if (date.isBefore(plan.getStartDate()) || date.isAfter(plan.getEndDate())) throw new IllegalArgumentException("任务日期必须位于计划日期范围内。");
    }
    private void checkVersion(long current, Long requested) {
        if (requested != null && requested != current) throw new ConflictException("数据已经被其他设备修改，请刷新后重试。");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void flushAndRefresh(Object entity) { entityManager.flush(); entityManager.refresh(entity); }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("列表数据无法序列化。", exception); }
    }
    private <T> T read(String value, TypeReference<T> type) {
        try { return objectMapper.readValue(value, type); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("数据库中的 JSON 数据无效。", exception); }
    }

    private PlanDtos.PlanResponse toPlan(Plan plan) {
        List<PlanTask> planTasks = tasks.findByPlanIdOrderByDateAscSortOrderAsc(plan.getId());
        long completed = planTasks.stream().filter(task -> progress.findByTaskId(task.getId()).map(item -> item.getStatus() == TaskProgress.Status.COMPLETED).orElse(false)).count();
        return new PlanDtos.PlanResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getStartDate(), plan.getEndDate(), plan.isArchived(), plan.getVersion(), planTasks.size(), completed, plan.getUpdatedAt());
    }
    private PlanDtos.TagResponse toTag(PlanTag tag) { return new PlanDtos.TagResponse(tag.getId(), tag.getName(), tag.getColor(), tag.isOverall(), tag.getVersion()); }
    private PlanDtos.TaskResponse toTask(PlanTask task) {
        PlanTag tag = task.getTag();
        return new PlanDtos.TaskResponse(task.getId(), task.getPlan().getId(), tag == null ? null : tag.getId(), tag == null ? null : tag.getName(), tag == null ? null : tag.getColor(),
            task.getDate(), task.getStartTime(), task.getEndTime(), task.getPlannedMinutes(), task.getCategory(), task.getTitle(), task.getDescription(),
            read(task.getAcceptanceJson(), STRING_LIST), task.getResourceUrl(), task.isRequired(), task.getSortOrder(), task.getVersion(), toProgress(progressFor(task.getId())));
    }
    private PlanDtos.ProgressResponse toProgress(TaskProgress item) {
        return new PlanDtos.ProgressResponse(item.getStatus().name(), item.getAccumulatedSeconds(), item.getStartedAt(), item.getCompletedAt(),
            read(item.getAcceptanceJson(), BOOLEAN_LIST), item.getNote(), item.getDeferTo(), item.getSkipReason(), item.getVersion());
    }
    private PlanDtos.ReflectionResponse toReflection(DailyReflection item) {
        return new PlanDtos.ReflectionResponse(item.getId(), item.getDate(), item.getCompletedSummary(), item.getUnfinishedSummary(), item.getBlockers(), item.getFirstTaskTomorrow(), item.getEnergy(), item.getNote(), item.getVersion());
    }
}
