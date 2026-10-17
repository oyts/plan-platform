package com.oyts.planplatform.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.auth.UserAccountRepository;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.mainline.MainlineItemRepository;
import com.oyts.planplatform.mainline.MainlineRepository;
import com.oyts.planplatform.mainline.domain.Mainline;
import com.oyts.planplatform.mainline.domain.MainlineItem;
import com.oyts.planplatform.plan.domain.DailyReflection;
import com.oyts.planplatform.plan.domain.Plan;
import com.oyts.planplatform.plan.domain.PlanMilestone;
import com.oyts.planplatform.plan.domain.PlanTag;
import com.oyts.planplatform.plan.domain.PlanTask;
import com.oyts.planplatform.plan.domain.TaskTemplate;
import com.oyts.planplatform.plan.domain.TaskProgress;
import com.oyts.planplatform.plan.domain.TimeEntry;
import com.oyts.planplatform.plan.repository.DailyReflectionRepository;
import com.oyts.planplatform.plan.repository.PlanRepository;
import com.oyts.planplatform.plan.repository.PlanMilestoneRepository;
import com.oyts.planplatform.plan.repository.PlanTagRepository;
import com.oyts.planplatform.plan.repository.PlanTaskRepository;
import com.oyts.planplatform.plan.repository.TaskTemplateRepository;
import com.oyts.planplatform.plan.repository.TaskProgressRepository;
import com.oyts.planplatform.plan.repository.TimeEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final TypeReference<List<Boolean>> BOOLEAN_LIST = new TypeReference<>() {};
    private final UserAccountRepository users;
    private final PlanRepository plans;
    private final PlanMilestoneRepository milestones;
    private final PlanTagRepository tags;
    private final PlanTaskRepository tasks;
    private final TaskTemplateRepository templates;
    private final TaskProgressRepository progress;
    private final TimeEntryRepository timeEntries;
    private final DailyReflectionRepository reflections;
    private final MainlineRepository mainlines;
    private final MainlineItemRepository mainlineItems;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public PlanService(UserAccountRepository users, PlanRepository plans, PlanMilestoneRepository milestones, PlanTagRepository tags, PlanTaskRepository tasks, TaskTemplateRepository templates,
                       TaskProgressRepository progress, TimeEntryRepository timeEntries,
                       DailyReflectionRepository reflections, MainlineRepository mainlines, MainlineItemRepository mainlineItems,
                       ObjectMapper objectMapper, EntityManager entityManager) {
        this.users = users; this.plans = plans; this.milestones = milestones; this.tags = tags; this.tasks = tasks; this.templates = templates; this.progress = progress;
        this.timeEntries = timeEntries; this.reflections = reflections; this.mainlines = mainlines; this.mainlineItems = mainlineItems; this.objectMapper = objectMapper; this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.PlanResponse> listPlans(String userId) {
        return plans.findByOwnerIdAndDeletedAtIsNullAndArchivedFalseOrderByStartDateDesc(userId).stream().map(this::toPlan).toList();
    }

    @Transactional
    public PlanDtos.PlanResponse createPlan(String userId, PlanDtos.PlanRequest request) {
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        Plan plan = new Plan(owner, request.name(), request.description(), request.startDate(), request.endDate());
        if (request.restDays() != null) plan.setRestDays(request.restDays());
        plans.save(plan);
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
        // Older clients omit this field; do not erase an existing configuration.
        if (request.restDays() != null) plan.setRestDays(request.restDays());
        flushAndRefresh(plan);
        return toPlan(plan);
    }

    @Transactional
    public void archivePlan(String userId, String planId) { ownedPlan(userId, planId).trash(); }

    @Transactional(readOnly = true)
    public List<PlanDtos.TagResponse> listTags(String userId, String planId) {
        ownedPlan(userId, planId);
        return tags.findByPlanIdAndDeletedAtIsNullOrderByOverallDescCreatedAtAsc(planId).stream().map(this::toTag).toList();
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
        if (tasks.countByTagIdAndDeletedAtIsNull(tagId) > 0) throw new ConflictException("该标签仍被任务使用，请先修改任务标签。");
        tag.trash();
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.TaskResponse> listTasks(String userId, String planId) {
        ownedPlan(userId, planId);
        return tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId).stream().map(this::toTask).toList();
    }

    /**
     * The full task ledger is useful for exports, but the schedule UI must not load an entire long-term plan.
     * Keep both endpoints: this query is bounded by a date window and uses a database page.
     */
    @Transactional(readOnly = true)
    public PlanDtos.PageResponse<PlanDtos.TaskResponse> taskPage(String userId, String planId, LocalDate from, LocalDate to, int page, int size) {
        Plan plan = ownedPlan(userId, planId);
        validateRange(from, to);
        LocalDate boundedFrom = from.isBefore(plan.getStartDate()) ? plan.getStartDate() : from;
        LocalDate boundedTo = to.isAfter(plan.getEndDate()) ? plan.getEndDate() : to;
        if (boundedFrom.isAfter(boundedTo)) return new PlanDtos.PageResponse<>(List.of(), normalizePage(page), normalizeSize(size), 0, 0);
        Page<PlanTask> result = tasks.findByPlanIdAndDeletedAtIsNullAndDateBetweenOrderByDateAscSortOrderAsc(
            planId, boundedFrom, boundedTo, PageRequest.of(normalizePage(page), normalizeSize(size), Sort.unsorted()));
        return new PlanDtos.PageResponse<>(result.getContent().stream().map(this::toTask).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public PlanDtos.TaskResponse createTask(String userId, String planId, PlanDtos.TaskRequest request) {
        Plan plan = ownedPlan(userId, planId);
        validateTaskDate(plan, request.date());
        PlanTag tag = request.tagId() == null ? null : ownedTag(userId, request.tagId());
        if (tag != null && !tag.getPlan().getId().equals(planId)) throw new IllegalArgumentException("标签不属于当前计划。");
        return createTask(plan, tag, request, null, null);
    }

    @Transactional
    public List<PlanDtos.TaskResponse> createRecurringTasks(String userId, String planId, PlanDtos.RecurringTaskRequest request) {
        Plan plan = ownedPlan(userId, planId);
        PlanDtos.TaskRequest taskRequest = request.task();
        validateTaskDate(plan, taskRequest.date());
        if (request.until().isBefore(taskRequest.date())) throw new IllegalArgumentException("重复结束日期不能早于首次日期。");
        PlanTag tag = taskRequest.tagId() == null ? null : ownedTag(userId, taskRequest.tagId());
        if (tag != null && !tag.getPlan().getId().equals(planId)) throw new IllegalArgumentException("标签不属于当前计划。");
        String recurrenceId = UUID.randomUUID().toString();
        List<PlanDtos.TaskResponse> created = new ArrayList<>();
        for (LocalDate date = taskRequest.date(); !date.isAfter(request.until()) && !date.isAfter(plan.getEndDate()); date = date.plusDays(1)) {
            if (!matchesRecurrence(taskRequest.date(), date, request.mode())) continue;
            if (created.size() >= 366) throw new IllegalArgumentException("一次最多创建 366 个重复任务。");
            PlanDtos.TaskRequest copy = new PlanDtos.TaskRequest(taskRequest.tagId(), date, taskRequest.startTime(), taskRequest.endTime(), taskRequest.plannedMinutes(),
                taskRequest.category(), taskRequest.title(), taskRequest.description(), taskRequest.acceptance(), taskRequest.resourceUrl(), taskRequest.required(), taskRequest.sortOrder(), null);
            created.add(createTask(plan, tag, copy, recurrenceId, null));
        }
        if (created.isEmpty()) throw new IllegalArgumentException("重复规则没有在当前计划范围内生成任务。");
        return created;
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.RecurrenceBatchResponse> recurrenceBatches(String userId, String planId) {
        ownedPlan(userId, planId);
        Map<String, List<PlanTask>> batches = new LinkedHashMap<>();
        tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId).stream().filter(task -> task.getRecurrenceId() != null)
            .forEach(task -> batches.computeIfAbsent(task.getRecurrenceId(), ignored -> new ArrayList<>()).add(task));
        return batches.entrySet().stream().map(entry -> {
            List<PlanTask> rows = entry.getValue(); PlanTask first = rows.getFirst();
            long completed = rows.stream().filter(row -> progressFor(row.getId()).getStatus() == TaskProgress.Status.COMPLETED).count();
            long open = rows.stream().filter(row -> { TaskProgress.Status state = progressFor(row.getId()).getStatus(); return state != TaskProgress.Status.COMPLETED && state != TaskProgress.Status.SKIPPED; }).count();
            return new PlanDtos.RecurrenceBatchResponse(entry.getKey(), first.getTitle(), first.getCategory(), first.getDate(), rows.getLast().getDate(), rows.size(), completed, open);
        }).toList();
    }

    /** Repeated tasks are materialized up-front. Stopping a batch preserves history and marks only future unfinished rows as skipped. */
    @Transactional
    public PlanDtos.StopRecurrenceResponse stopRecurrence(String userId, String planId, String recurrenceId, PlanDtos.StopRecurrenceRequest request) {
        ownedPlan(userId, planId);
        List<PlanTask> rows = tasks.findByPlanIdAndRecurrenceIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId, recurrenceId);
        if (rows.isEmpty()) throw new NotFoundException("重复任务批次不存在。");
        long stopped = 0;
        for (PlanTask task : rows) {
            if (task.getDate().isBefore(request.from())) continue;
            TaskProgress item = progressFor(task.getId());
            if (item.getStatus() == TaskProgress.Status.COMPLETED || item.getStatus() == TaskProgress.Status.SKIPPED) continue;
            if (item.getStartedAt() != null) {
                Instant ended = Instant.now(); long seconds = Math.max(0, java.time.Duration.between(item.getStartedAt(), ended).getSeconds());
                if (seconds > 0) timeEntries.save(new TimeEntry(item, item.getStartedAt(), ended, seconds, "TIMER"));
            }
            item.update(TaskProgress.Status.SKIPPED, item.getNote(), item.getAcceptanceJson(), null,
                blank(request.reason()) ? "重复任务批次已停止" : request.reason());
            stopped++;
        }
        return new PlanDtos.StopRecurrenceResponse(recurrenceId, stopped);
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
    public void deleteTask(String userId, String taskId) { ownedTask(userId, taskId).trash(); }

    @Transactional(readOnly = true)
    public List<PlanDtos.MilestoneResponse> listMilestones(String userId, String planId) {
        ownedPlan(userId, planId);
        return milestones.findByPlanIdAndDeletedAtIsNullOrderByDateAscCreatedAtAsc(planId).stream().map(this::toMilestone).toList();
    }

    @Transactional
    public PlanDtos.MilestoneResponse createMilestone(String userId, String planId, PlanDtos.MilestoneRequest request) {
        Plan plan = ownedPlan(userId, planId);
        validateTaskDate(plan, request.date());
        return toMilestone(milestones.save(new PlanMilestone(plan, request.name(), request.date(), request.description(), json(request.acceptance()))));
    }

    @Transactional
    public PlanDtos.MilestoneResponse updateMilestone(String userId, String milestoneId, PlanDtos.MilestoneRequest request) {
        PlanMilestone milestone = ownedMilestone(userId, milestoneId);
        checkVersion(milestone.getVersion(), request.version());
        validateTaskDate(milestone.getPlan(), request.date());
        milestone.update(request.name(), request.date(), request.description(), json(request.acceptance()));
        flushAndRefresh(milestone);
        return toMilestone(milestone);
    }

    @Transactional
    public PlanDtos.MilestoneResponse updateMilestoneProgress(String userId, String milestoneId, PlanDtos.MilestoneProgressRequest request) {
        PlanMilestone milestone = ownedMilestone(userId, milestoneId);
        checkVersion(milestone.getVersion(), request.version());
        milestone.record(json(request.checks()), request.note(), Instant.now());
        flushAndRefresh(milestone);
        return toMilestone(milestone);
    }

    @Transactional
    public void deleteMilestone(String userId, String milestoneId) { ownedMilestone(userId, milestoneId).trash(); }

    @Transactional(readOnly = true)
    public List<PlanDtos.TemplateResponse> listTemplates(String userId) { return templates.findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId).stream().map(this::toTemplate).toList(); }

    @Transactional
    public PlanDtos.TemplateResponse createTemplate(String userId, PlanDtos.TemplateRequest request) {
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        if (templates.existsByOwnerIdAndDeletedAtIsNullAndNameIgnoreCase(userId, request.name().trim())) throw new ConflictException("已存在同名任务模板。");
        return toTemplate(templates.save(new TaskTemplate(owner, request.name(), request.category(), request.title(), request.description(), request.plannedMinutes(), json(request.acceptance()), request.resourceUrl(), request.required())));
    }

    @Transactional
    public PlanDtos.TemplateResponse updateTemplate(String userId, String templateId, PlanDtos.TemplateRequest request) {
        TaskTemplate template = ownedTemplate(userId, templateId);
        checkVersion(template.getVersion(), request.version());
        if (!template.getName().equalsIgnoreCase(request.name().trim()) && templates.existsByOwnerIdAndDeletedAtIsNullAndNameIgnoreCase(userId, request.name().trim())) throw new ConflictException("已存在同名任务模板。");
        template.update(request.name(), request.category(), request.title(), request.description(), request.plannedMinutes(), json(request.acceptance()), request.resourceUrl(), request.required());
        flushAndRefresh(template);
        return toTemplate(template);
    }

    @Transactional
    public void deleteTemplate(String userId, String templateId) { ownedTemplate(userId, templateId).trash(); }

    @Transactional
    public PlanDtos.TaskResponse instantiateTemplate(String userId, String templateId, PlanDtos.InstantiateTemplateRequest request) {
        TaskTemplate template = ownedTemplate(userId, templateId);
        Plan plan = ownedPlan(userId, request.planId());
        validateTaskDate(plan, request.date());
        PlanTag tag = request.tagId() == null ? null : ownedTag(userId, request.tagId());
        if (tag != null && !tag.getPlan().getId().equals(plan.getId())) throw new IllegalArgumentException("标签不属于当前计划。");
        PlanDtos.TaskRequest taskRequest = new PlanDtos.TaskRequest(request.tagId(), request.date(), request.startTime(), request.endTime(), template.getPlannedMinutes(),
            template.getCategory(), template.getTitle(), template.getDescription(), read(template.getAcceptanceJson(), STRING_LIST), template.getResourceUrl(), template.isRequired(),
            request.sortOrder() == null ? 0 : request.sortOrder(), null);
        return createTask(plan, tag, taskRequest, null, template.getId());
    }

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

    /** Move an unfinished task onto a concrete catch-up date. Works for ordinary and rest days alike. */
    @Transactional
    public PlanDtos.ProgressResponse defer(String userId, String taskId, PlanDtos.DeferRequest request) {
        PlanTask task = ownedTask(userId, taskId);
        if (request.deferTo().isBefore(task.getDate())) throw new IllegalArgumentException("补做日期不能早于原计划日期。");
        TaskProgress current = progressFor(taskId);
        checkVersion(current.getVersion(), request.version());
        if (current.getStatus() == TaskProgress.Status.COMPLETED || current.getStatus() == TaskProgress.Status.SKIPPED) {
            throw new ConflictException("已完成或已跳过的任务不能安排补做。");
        }
        current.update(TaskProgress.Status.DEFERRED,
            request.note() == null ? current.getNote() : request.note(), current.getAcceptanceJson(), request.deferTo(), "");
        flushAndRefresh(current);
        return toProgress(current);
    }

    /** Returns only tasks explicitly scheduled for catch-up in the requested date rail. */
    @Transactional(readOnly = true)
    public List<PlanDtos.TaskResponse> carryover(String userId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return progress.findByTaskPlanOwnerId(userId).stream()
            .filter(item -> item.getTask().getDeletedAt() == null)
            .filter(item -> item.getStatus() == TaskProgress.Status.DEFERRED && item.getDeferTo() != null)
            .filter(item -> !item.getDeferTo().isBefore(from) && !item.getDeferTo().isAfter(to))
            .sorted(java.util.Comparator.comparing(TaskProgress::getDeferTo)
                .thenComparing(item -> item.getTask().getStartTime(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(item -> item.getTask().getSortOrder()))
            .map(item -> toTask(item.getTask())).toList();
    }

    @Transactional(readOnly = true)
    public PlanDtos.TodayResponse today(String userId, LocalDate date) {
        List<PlanDtos.TaskResponse> rows = tasks.findByPlanOwnerIdAndDeletedAtIsNullAndDateOrderByStartTimeAsc(userId, date).stream().map(this::toTask).toList();
        return new PlanDtos.TodayResponse(date, rows, rows.size(), rows.stream().filter(row -> row.progress().status().equals("COMPLETED")).count(),
            rows.stream().mapToLong(PlanDtos.TaskResponse::plannedMinutes).sum(), rows.stream().mapToLong(row -> row.progress().accumulatedSeconds()).sum());
    }

    @Transactional
    public PlanDtos.ReflectionResponse saveReflection(String userId, String planId, LocalDate date, PlanDtos.ReflectionRequest request) {
        Plan plan = ownedPlan(userId, planId);
        validateTaskDate(plan, date);
        DailyReflection reflection = reflections.findByPlanIdAndDate(planId, date).orElseGet(() -> new DailyReflection(plan, date));
        checkVersion(reflection.getVersion(), request.version());
        reflection.update(request.completedSummary(), request.unfinishedSummary(), request.blockers(), request.firstTaskTomorrow(), request.energy(), request.note());
        reflections.save(reflection);
        flushAndRefresh(reflection);
        return toReflection(reflection);
    }

    @Transactional(readOnly = true)
    public PlanDtos.PageResponse<PlanDtos.ReflectionResponse> reflectionPage(String userId, String planId, LocalDate from, LocalDate to, int page, int size) {
        ownedPlan(userId, planId);
        validateRange(from, to);
        Page<DailyReflection> result = reflections.findByPlanIdAndDateBetweenOrderByDateDesc(planId, from, to,
            PageRequest.of(normalizePage(page), normalizeSize(size)));
        return new PlanDtos.PageResponse<>(result.getContent().stream().map(this::toReflection).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public String reflectionExport(String userId, String planId, LocalDate from, LocalDate to, String format) {
        Plan plan = ownedPlan(userId, planId);
        validateRange(from, to);
        List<PlanDtos.ReflectionResponse> rows = reflections.findByPlanIdAndDateBetweenOrderByDateDesc(planId, from, to).stream().map(this::toReflection).toList();
        if ("MARKDOWN".equalsIgnoreCase(format)) {
            StringBuilder markdown = new StringBuilder("# ").append(plan.getName()).append("：复盘记录\n\n");
            rows.forEach(row -> markdown.append("## ").append(row.date()).append("\n\n")
                .append("- 完成：").append(markdownValue(row.completedSummary())).append("\n")
                .append("- 未完成：").append(markdownValue(row.unfinishedSummary())).append("\n")
                .append("- 阻塞：").append(markdownValue(row.blockers())).append("\n")
                .append("- 明日第一件事：").append(markdownValue(row.firstTaskTomorrow())).append("\n")
                .append("- 精力：").append(row.energy() == null ? "未评分" : row.energy() + "/5").append("\n")
                .append("- 备注：").append(markdownValue(row.note())).append("\n\n"));
            return markdown.toString();
        }
        StringBuilder csv = new StringBuilder("日期,完成,未完成,阻塞,明日第一件事,精力,备注\n");
        rows.forEach(row -> csv.append(csvCell(row.date().toString())).append(',').append(csvCell(row.completedSummary())).append(',')
            .append(csvCell(row.unfinishedSummary())).append(',').append(csvCell(row.blockers())).append(',').append(csvCell(row.firstTaskTomorrow())).append(',')
            .append(csvCell(row.energy() == null ? "" : row.energy().toString())).append(',').append(csvCell(row.note())).append('\n'));
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public PlanDtos.PageResponse<PlanDtos.TimeEntryResponse> timeLedger(String userId, LocalDate from, LocalDate to, int page, int size,
                                                                         String planId, String tagId, String taskId, String source) {
        validateRange(from, to);
        Instant rangeStart = from.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        Page<TimeEntry> result = timeEntries.findAll(timeSpecification(userId, rangeStart, rangeEnd, planId, tagId, taskId, source),
            PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.DESC, "startedAt")));
        return new PlanDtos.PageResponse<>(result.getContent().stream().map(this::toTimeEntry).toList(), result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public String timeLedgerCsv(String userId, LocalDate from, LocalDate to, String planId, String tagId, String taskId, String source) {
        validateRange(from, to);
        Instant rangeStart = from.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        StringBuilder csv = new StringBuilder("开始时间,结束时间,计划,任务,标签,秒数,来源\n");
        timeEntries.findAll(timeSpecification(userId, rangeStart, rangeEnd, planId, tagId, taskId, source), Sort.by(Sort.Direction.DESC, "startedAt")).forEach(entry -> {
            PlanDtos.TimeEntryResponse row = toTimeEntry(entry);
            csv.append(csvCell(row.startedAt().toString())).append(',').append(csvCell(row.endedAt().toString())).append(',')
                .append(csvCell(row.planId())).append(',').append(csvCell(row.taskTitle())).append(',').append(csvCell(row.tagName()))
                .append(',').append(row.seconds()).append(',').append(csvCell(row.source())).append('\n');
        });
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public List<PlanDtos.TrashResponse> trash(String userId) {
        List<PlanDtos.TrashResponse> rows = new ArrayList<>();
        tasks.findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(task -> rows.add(new PlanDtos.TrashResponse("TASK", task.getId(), task.getTitle(), task.getPlan().getName() + " · " + task.getDate(), task.getDeletedAt())));
        milestones.findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("MILESTONE", item.getId(), item.getName(), item.getPlan().getName() + " · " + item.getDate(), item.getDeletedAt())));
        templates.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("TEMPLATE", item.getId(), item.getName(), item.getTitle(), item.getDeletedAt())));
        plans.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("PLAN", item.getId(), item.getName(), item.getStartDate() + " — " + item.getEndDate(), item.getDeletedAt())));
        tags.findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("TAG", item.getId(), item.getName(), item.getPlan().getName(), item.getDeletedAt())));
        mainlines.findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("MAINLINE", item.getId(), item.getName(), "主线 · " + item.getDescription(), item.getDeletedAt())));
        mainlineItems.findByMainlineOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(userId).forEach(item -> rows.add(new PlanDtos.TrashResponse("MAINLINE_ITEM", item.getId(), item.getTitle(), item.getMainline().getName() + " · 第 " + (item.getSortOrder() + 1) + " 步", item.getDeletedAt())));
        rows.sort(java.util.Comparator.comparing(PlanDtos.TrashResponse::deletedAt).reversed());
        return rows;
    }

    @Transactional
    public void restoreTrash(String userId, String type, String id) {
        switch (type.toUpperCase()) {
            case "TASK" -> { PlanTask item = tasks.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站任务不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "MILESTONE" -> { PlanMilestone item = milestones.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站里程碑不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "TEMPLATE" -> { TaskTemplate item = templates.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站模板不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "PLAN" -> { Plan item = plans.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站计划不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "TAG" -> { PlanTag item = tags.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站标签不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "MAINLINE" -> { Mainline item = mainlines.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站主线不存在。")); requireTrashed(item.getDeletedAt()); item.restore(); }
            case "MAINLINE_ITEM" -> { MainlineItem item = mainlineItems.findByIdAndMainlineOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站主线步骤不存在。")); requireTrashed(item.getDeletedAt()); if (item.getMainline().getDeletedAt() != null) throw new ConflictException("请先恢复所属主线。"); item.restore(); }
            default -> throw new IllegalArgumentException("不支持的回收站类型。");
        }
    }

    @Transactional
    public void purgeTrash(String userId, String type, String id) {
        switch (type.toUpperCase()) {
            case "TASK" -> { PlanTask item = tasks.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站任务不存在。")); requireTrashed(item.getDeletedAt()); tasks.delete(item); }
            case "MILESTONE" -> { PlanMilestone item = milestones.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站里程碑不存在。")); requireTrashed(item.getDeletedAt()); milestones.delete(item); }
            case "TEMPLATE" -> { TaskTemplate item = templates.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站模板不存在。")); requireTrashed(item.getDeletedAt()); templates.delete(item); }
            case "PLAN" -> { Plan item = plans.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站计划不存在。")); requireTrashed(item.getDeletedAt()); plans.delete(item); }
            case "TAG" -> { PlanTag item = tags.findByIdAndPlanOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站标签不存在。")); requireTrashed(item.getDeletedAt()); tags.delete(item); }
            case "MAINLINE" -> { Mainline item = mainlines.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站主线不存在。")); requireTrashed(item.getDeletedAt()); mainlines.delete(item); }
            case "MAINLINE_ITEM" -> { MainlineItem item = mainlineItems.findByIdAndMainlineOwnerId(id, userId).orElseThrow(() -> new NotFoundException("回收站主线步骤不存在。")); requireTrashed(item.getDeletedAt()); mainlineItems.delete(item); }
            default -> throw new IllegalArgumentException("不支持的回收站类型。");
        }
    }

    @Transactional(readOnly = true)
    public PlanDtos.StatsResponse stats(String userId, String planId, LocalDate from, LocalDate to) {
        ownedPlan(userId, planId);
        validateRange(from, to);
        List<PlanDtos.TaskResponse> rows = tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId).stream()
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

    @Transactional(readOnly = true)
    public String taskExport(String userId, String planId, LocalDate from, LocalDate to) {
        ownedPlan(userId, planId); validateRange(from, to);
        StringBuilder csv = new StringBuilder("日期,开始,结束,任务,分类,标签,计划分钟,状态,实际秒数,重复批次\n");
        tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId).stream()
            .filter(item -> !item.getDate().isBefore(from) && !item.getDate().isAfter(to)).forEach(item -> {
                PlanDtos.TaskResponse row = toTask(item);
                csv.append(csvCell(row.date().toString())).append(',').append(csvCell(row.startTime() == null ? "" : row.startTime().toString())).append(',')
                    .append(csvCell(row.endTime() == null ? "" : row.endTime().toString())).append(',').append(csvCell(row.title())).append(',')
                    .append(csvCell(row.category())).append(',').append(csvCell(row.tagName())).append(',').append(row.plannedMinutes()).append(',')
                    .append(csvCell(row.progress().status())).append(',').append(row.progress().accumulatedSeconds()).append(',').append(csvCell(row.recurrenceId())).append('\n');
            });
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public String statsExport(String userId, String planId, LocalDate from, LocalDate to) {
        PlanDtos.StatsResponse summary = stats(userId, planId, from, to);
        StringBuilder csv = new StringBuilder("开始日期,结束日期,任务数,已完成,计划分钟,实际秒数\n");
        csv.append(summary.from()).append(',').append(summary.to()).append(',').append(summary.taskCount()).append(',').append(summary.completedCount()).append(',').append(summary.plannedMinutes()).append(',').append(summary.actualSeconds()).append("\n\n标签,任务数,已完成,计划分钟,实际秒数\n");
        summary.tags().forEach(row -> csv.append(csvCell(row.tagName())).append(',').append(row.taskCount()).append(',').append(row.completedCount()).append(',').append(row.plannedMinutes()).append(',').append(row.actualSeconds()).append('\n'));
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public PlanDtos.PageResponse<PlanDtos.DailyStat> dailyStats(String userId, String planId, LocalDate from, LocalDate to, int page, int size) {
        ownedPlan(userId, planId);
        validateRange(from, to);
        Map<LocalDate, List<PlanDtos.TaskResponse>> byDate = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) byDate.put(date, new ArrayList<>());
        tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(planId).stream()
            .filter(task -> !task.getDate().isBefore(from) && !task.getDate().isAfter(to))
            .map(this::toTask).forEach(task -> byDate.get(task.date()).add(task));
        List<PlanDtos.DailyStat> rows = byDate.entrySet().stream().map(entry -> dailyStat(entry.getKey(), entry.getValue())).toList();
        int normalizedSize = normalizeSize(size);
        int normalizedPage = normalizePage(page);
        int start = Math.min(normalizedPage * normalizedSize, rows.size());
        int end = Math.min(start + normalizedSize, rows.size());
        return new PlanDtos.PageResponse<>(rows.subList(start, end), normalizedPage, normalizedSize, rows.size(), (int) Math.ceil((double) rows.size() / normalizedSize));
    }

    private Plan ownedPlan(String userId, String planId) { return plans.findByIdAndOwnerIdAndDeletedAtIsNull(planId, userId).orElseThrow(() -> new NotFoundException("计划不存在。")); }
    private PlanTag ownedTag(String userId, String tagId) { return tags.findByIdAndPlanOwnerIdAndDeletedAtIsNull(tagId, userId).orElseThrow(() -> new NotFoundException("标签不存在。")); }
    private PlanTask ownedTask(String userId, String taskId) { return tasks.findByIdAndPlanOwnerIdAndDeletedAtIsNull(taskId, userId).orElseThrow(() -> new NotFoundException("任务不存在。")); }
    private PlanMilestone ownedMilestone(String userId, String milestoneId) { return milestones.findByIdAndPlanOwnerIdAndDeletedAtIsNull(milestoneId, userId).orElseThrow(() -> new NotFoundException("里程碑不存在。")); }
    private TaskTemplate ownedTemplate(String userId, String templateId) { return templates.findByIdAndOwnerIdAndDeletedAtIsNull(templateId, userId).orElseThrow(() -> new NotFoundException("任务模板不存在。")); }
    private TaskProgress progressFor(String taskId) { return progress.findByTaskId(taskId).orElseThrow(() -> new NotFoundException("任务进度不存在。")); }

    private PlanDtos.TaskResponse createTask(Plan plan, PlanTag tag, PlanDtos.TaskRequest request, String recurrenceId, String templateId) {
        PlanTask task = new PlanTask(plan);
        applyTask(task, tag, request);
        task.assignRecurrence(recurrenceId);
        task.assignTemplate(templateId);
        tasks.save(task);
        progress.save(new TaskProgress(task));
        return toTask(task);
    }
    private boolean matchesRecurrence(LocalDate firstDate, LocalDate candidate, PlanDtos.RecurrenceMode mode) {
        return switch (mode) {
            case DAILY -> true;
            case WEEKDAYS -> candidate.getDayOfWeek().getValue() <= 5;
            case WEEKLY -> java.time.temporal.ChronoUnit.DAYS.between(firstDate, candidate) % 7 == 0;
        };
    }

    private void applyTask(PlanTask task, PlanTag tag, PlanDtos.TaskRequest request) {
        task.update(tag, request.date(), request.startTime(), request.endTime(), request.plannedMinutes(), request.category().trim().toUpperCase(),
            request.title(), request.description(), json(request.acceptance()), request.resourceUrl(), request.required(), request.sortOrder());
    }
    private void validateTaskDate(Plan plan, LocalDate date) {
        if (date.isBefore(plan.getStartDate()) || date.isAfter(plan.getEndDate())) throw new IllegalArgumentException("任务日期必须位于计划日期范围内。");
    }
    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new IllegalArgumentException("开始日期不能晚于结束日期。");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366) throw new IllegalArgumentException("一次最多查询 367 天的数据。");
    }
    private int normalizePage(int page) {
        if (page < 0) throw new IllegalArgumentException("页码不能小于 0。");
        return page;
    }
    private int normalizeSize(int size) {
        if (size < 1 || size > 100) throw new IllegalArgumentException("每页数量必须在 1 到 100 之间。");
        return size;
    }
    private PlanDtos.DailyStat dailyStat(LocalDate date, List<PlanDtos.TaskResponse> rows) {
        return new PlanDtos.DailyStat(date.toString(), rows.size(),
            rows.stream().filter(row -> row.progress().status().equals("COMPLETED")).count(),
            rows.stream().filter(row -> row.progress().status().equals("PARTIAL")).count(),
            rows.stream().filter(row -> row.progress().status().equals("SKIPPED")).count(),
            rows.stream().filter(row -> row.progress().status().equals("DEFERRED")).count(),
            rows.stream().mapToLong(PlanDtos.TaskResponse::plannedMinutes).sum(),
            rows.stream().mapToLong(row -> row.progress().accumulatedSeconds()).sum());
    }
    private void checkVersion(long current, Long requested) {
        if (requested != null && requested != current) throw new ConflictException("数据已经被其他设备修改，请刷新后重试。");
    }
    private void requireTrashed(Instant deletedAt) {
        if (deletedAt == null) throw new ConflictException("该记录不在回收站中。");
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
        List<PlanTask> planTasks = tasks.findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(plan.getId());
        long completed = planTasks.stream().filter(task -> progress.findByTaskId(task.getId()).map(item -> item.getStatus() == TaskProgress.Status.COMPLETED).orElse(false)).count();
        return new PlanDtos.PlanResponse(plan.getId(), plan.getName(), plan.getDescription(), plan.getStartDate(), plan.getEndDate(), plan.isArchived(), plan.getVersion(), planTasks.size(), completed, plan.getUpdatedAt(), plan.getRestDays());
    }
    private PlanDtos.TagResponse toTag(PlanTag tag) { return new PlanDtos.TagResponse(tag.getId(), tag.getName(), tag.getColor(), tag.isOverall(), tag.getVersion()); }
    private PlanDtos.TaskResponse toTask(PlanTask task) {
        PlanTag tag = task.getTag();
        return new PlanDtos.TaskResponse(task.getId(), task.getPlan().getId(), tag == null ? null : tag.getId(), tag == null ? null : tag.getName(), tag == null ? null : tag.getColor(),
            task.getDate(), task.getStartTime(), task.getEndTime(), task.getPlannedMinutes(), task.getCategory(), task.getTitle(), task.getDescription(),
            read(task.getAcceptanceJson(), STRING_LIST), task.getResourceUrl(), task.isRequired(), task.getSortOrder(), task.getRecurrenceId(), task.getTemplateId(), task.getVersion(), toProgress(progressFor(task.getId())));
    }
    private PlanDtos.ProgressResponse toProgress(TaskProgress item) {
        return new PlanDtos.ProgressResponse(item.getStatus().name(), item.getAccumulatedSeconds(), item.getStartedAt(), item.getCompletedAt(),
            read(item.getAcceptanceJson(), BOOLEAN_LIST), item.getNote(), item.getDeferTo(), item.getSkipReason(), item.getVersion());
    }
    private PlanDtos.ReflectionResponse toReflection(DailyReflection item) {
        return new PlanDtos.ReflectionResponse(item.getId(), item.getDate(), item.getCompletedSummary(), item.getUnfinishedSummary(), item.getBlockers(), item.getFirstTaskTomorrow(), item.getEnergy(), item.getNote(), item.getVersion());
    }
    private PlanDtos.MilestoneResponse toMilestone(PlanMilestone item) {
        return new PlanDtos.MilestoneResponse(item.getId(), item.getPlan().getId(), item.getName(), item.getDate(), item.getDescription(),
            read(item.getAcceptanceJson(), STRING_LIST), read(item.getChecksJson(), BOOLEAN_LIST), item.getNote(), item.getReviewedAt(), item.getVersion());
    }
    private PlanDtos.TemplateResponse toTemplate(TaskTemplate item) {
        return new PlanDtos.TemplateResponse(item.getId(), item.getName(), item.getCategory(), item.getTitle(), item.getDescription(), item.getPlannedMinutes(),
            read(item.getAcceptanceJson(), STRING_LIST), item.getResourceUrl(), item.isRequired(), item.getVersion());
    }
    private PlanDtos.TimeEntryResponse toTimeEntry(TimeEntry entry) {
        PlanTask task = entry.getProgress().getTask();
        PlanTag tag = task.getTag();
        return new PlanDtos.TimeEntryResponse(entry.getId(), task.getPlan().getId(), task.getId(), task.getTitle(), tag == null ? "未分类" : tag.getName(),
            tag == null ? null : tag.getColor(), entry.getStartedAt(), entry.getEndedAt(), entry.getSeconds(), entry.getSource());
    }
    private String csvCell(String value) {
        return com.oyts.planplatform.common.CsvCell.encode(value);
    }
    private String markdownValue(String value) { return blank(value) ? "未填写" : value.replace("\n", "<br>"); }
    private org.springframework.data.jpa.domain.Specification<TimeEntry> timeSpecification(String userId, Instant from, Instant to,
                                                                                              String planId, String tagId, String taskId, String source) {
        return (root, query, builder) -> {
            var progressJoin = root.join("progress");
            var taskJoin = progressJoin.join("task");
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(taskJoin.get("plan").get("owner").get("id"), userId));
            predicates.add(builder.greaterThanOrEqualTo(root.get("startedAt"), from));
            predicates.add(builder.lessThan(root.get("startedAt"), to));
            if (!blank(planId)) predicates.add(builder.equal(taskJoin.get("plan").get("id"), planId));
            if (!blank(tagId)) predicates.add(builder.equal(taskJoin.get("tag").get("id"), tagId));
            if (!blank(taskId)) predicates.add(builder.equal(taskJoin.get("id"), taskId));
            if (!blank(source)) predicates.add(builder.equal(root.get("source"), source.trim().toUpperCase()));
            return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
