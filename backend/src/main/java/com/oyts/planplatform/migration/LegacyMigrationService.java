package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.JsonNode;
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
import com.oyts.planplatform.plan.domain.TaskProgress;
import com.oyts.planplatform.plan.domain.TaskTemplate;
import com.oyts.planplatform.plan.domain.TimeEntry;
import com.oyts.planplatform.plan.repository.DailyReflectionRepository;
import com.oyts.planplatform.plan.repository.PlanRepository;
import com.oyts.planplatform.plan.repository.PlanMilestoneRepository;
import com.oyts.planplatform.plan.repository.PlanTagRepository;
import com.oyts.planplatform.plan.repository.PlanTaskRepository;
import com.oyts.planplatform.plan.repository.TaskProgressRepository;
import com.oyts.planplatform.plan.repository.TaskTemplateRepository;
import com.oyts.planplatform.plan.repository.TimeEntryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LegacyMigrationService {
    private static final ZoneId LEGACY_ZONE = ZoneId.of("Asia/Shanghai");
    private final UserAccountRepository users;
    private final LegacyImportRepository imports;
    private final PlanRepository plans;
    private final PlanMilestoneRepository milestones;
    private final PlanTagRepository tags;
    private final PlanTaskRepository tasks;
    private final TaskProgressRepository progress;
    private final TaskTemplateRepository templates;
    private final TimeEntryRepository timeEntries;
    private final DailyReflectionRepository reflections;
    private final MainlineRepository mainlines;
    private final MainlineItemRepository mainlineItems;
    private final ObjectMapper mapper;

    LegacyMigrationService(UserAccountRepository users, LegacyImportRepository imports, PlanRepository plans, PlanMilestoneRepository milestones,
                           PlanTagRepository tags, PlanTaskRepository tasks, TaskProgressRepository progress, TaskTemplateRepository templates,
                           TimeEntryRepository timeEntries, DailyReflectionRepository reflections,
                           MainlineRepository mainlines, MainlineItemRepository mainlineItems, ObjectMapper mapper) {
        this.users = users; this.imports = imports; this.plans = plans; this.milestones = milestones; this.tags = tags; this.tasks = tasks;
        this.progress = progress; this.templates = templates; this.timeEntries = timeEntries; this.reflections = reflections;
        this.mainlines = mainlines; this.mainlineItems = mainlineItems; this.mapper = mapper;
    }

    Preview preview(JsonNode payload) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (!payload.isObject()) errors.add("备份文件必须是 JSON 对象。");
        int schema = payload.path("schemaVersion").asInt(-1);
        if (schema < 1 || schema > 6) errors.add("仅支持旧版 schemaVersion 1—6。");
        if (!payload.path("progress").isObject()) errors.add("缺少 progress 任务进度对象。");
        if (!payload.path("reflections").isObject()) errors.add("缺少 reflections 复盘对象。");
        if (!payload.path("workspace").isObject()) warnings.add("旧备份没有工作区，只能保存原始快照，无法迁移自定义计划。");
        JsonNode catalog = payload.path("migrationCatalog");
        if (!catalog.path("defaultPlan").isObject()) warnings.add("备份不含暑假计划任务目录；其进度会保留在原始快照中，但不能转换为关系表。");
        if (!catalog.path("builtInMainlines").isArray()) warnings.add("备份不含内置主线目录；吉他和项目升级进度只能保留在原始快照中。");

        List<JsonNode> planNodes = planNodes(payload);
        List<JsonNode> mainlineNodes = mainlineNodes(payload);
        int planTasks = planNodes.stream().mapToInt(node -> node.path("tasks").size()).sum();
        int items = mainlineNodes.stream().mapToInt(node -> node.path("items").size()).sum();
        int entryCount = 0;
        for (JsonNode item : payload.path("progress")) entryCount += item.path("timeEntries").size();
        int preservedOnly = payload.path("workspace").path("trash").size() + payload.path("workspace").path("deletionMarkers").size();
        if (payload.path("workspace").path("taskTemplates").size() > 0) warnings.add("旧版任务模板会迁入新版模板库；模板的开始/结束时间需在创建任务时补充。");
        if (payload.path("gates").size() > 0) warnings.add("旧版闸门会迁为里程碑并保留验收勾选；旧备份未携带闸门日期，请在新版调整目标日期。");
        if (preservedOnly > 0) warnings.add("回收站和删除标记共 " + preservedOnly + " 条，仅保存在原始快照中。");
        return new Preview(checksum(payload), text(payload, "planVersion", "unknown"), errors.isEmpty(), planNodes.size(),
            planTasks, mainlineNodes.size(), items, payload.path("reflections").size(), entryCount, preservedOnly,
            List.copyOf(warnings), List.copyOf(errors));
    }

    @Transactional
    ImportReport execute(String userId, JsonNode payload) {
        Preview preview = preview(payload);
        if (!preview.ready()) throw new IllegalArgumentException(String.join("；", preview.errors()));
        LegacyImport existing = imports.findByOwnerIdAndChecksum(userId, preview.checksum()).orElse(null);
        if (existing != null && "COMPLETED".equals(existing.getStatus())) return readReport(existing.getReportJson());
        if (existing == null && imports.existsByOwnerIdAndStatus(userId, "COMPLETED")) {
            throw new ConflictException("该账号已有一批已完成的旧版迁移，请先回滚后再导入其他备份。");
        }
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        LegacyImport batch = existing == null
            ? imports.save(new LegacyImport(owner, preview.sourceVersion(), preview.checksum(), payload.toString()))
            : existing;
        if (existing != null) batch.restart();

        List<String> planIds = new ArrayList<>();
        List<String> mainlineIds = new ArrayList<>();
        List<String> templateIds = new ArrayList<>();
        List<String> milestoneIds = new ArrayList<>();
        int importedTags = 0, importedTasks = 0, importedProgress = 0, importedEntries = 0, importedReflections = 0, importedItems = 0, importedTemplates = 0, importedMilestones = 0;
        JsonNode progressMap = payload.path("progress");

        for (JsonNode source : payload.path("workspace").path("taskTemplates")) {
            String name = clip(text(source, "name", "旧版模板"), 80);
            TaskTemplate template = templates.save(new TaskTemplate(owner, name, "CUSTOM", clip(text(source, "title", name), 180),
                clip(text(source, "description", ""), 2000), Math.max(1, source.path("plannedMinutes").asInt(1)),
                json(source.path("acceptance").isArray() ? source.path("acceptance") : mapper.createArrayNode()), clip(text(source, "resourceUrl", ""), 1000), true));
            templateIds.add(template.getId()); importedTemplates++;
        }

        for (JsonNode source : planNodes(payload)) {
            Plan plan = plans.save(new Plan(owner, clip(text(source, "name", "旧版计划"), 120), "从计划控制台 " + preview.sourceVersion() + " 迁移",
                date(source, "startDate"), date(source, "endDate")));
            plans.flush();
            planIds.add(plan.getId());
            Map<String, PlanTag> tagMap = new HashMap<>();
            for (JsonNode tagNode : source.path("tags")) {
                PlanTag tag = tags.save(new PlanTag(plan, clip(text(tagNode, "name", "未命名标签"), 60),
                    clip(text(tagNode, "color", "#002FA7"), 20), tagNode.path("isOverall").asBoolean(false)));
                tagMap.put(text(tagNode, "id", ""), tag); importedTags++;
            }
            int order = 0;
            for (JsonNode taskNode : source.path("tasks")) {
                String legacyTaskId = text(taskNode, "id", "task-" + order);
                PlanTask task = new PlanTask(plan);
                task.update(tagMap.get(text(taskNode, "tagId", "")), date(taskNode, "date"), time(taskNode, "startTime"), time(taskNode, "endTime"),
                    Math.max(1, taskNode.path("plannedMinutes").asInt(1)), clip(text(taskNode, "category", "custom").toUpperCase(), 40),
                    clip(text(taskNode, "title", "未命名任务"), 180), clip(text(taskNode, "description", ""), 2000),
                    json(taskNode.path("acceptance").isArray() ? taskNode.path("acceptance") : mapper.createArrayNode()),
                    clip(resourceUrl(taskNode), 1000), taskNode.path("required").asBoolean(true), order++);
                tasks.save(task); importedTasks++;
                TaskProgress itemProgress = new TaskProgress(task);
                JsonNode oldProgress = progressMap.path(legacyTaskId);
                applyProgress(itemProgress, oldProgress);
                progress.save(itemProgress); importedProgress++;
                for (JsonNode entry : oldProgress.path("timeEntries")) {
                    timeEntries.save(new TimeEntry(itemProgress, instant(entry.path("startedAt")), instant(entry.path("endedAt")),
                        entry.path("seconds").asLong(0), clip(text(entry, "source", "legacy").toUpperCase(), 20)));
                    importedEntries++;
                }
            }
            boolean defaultPlan = source.equals(payload.path("migrationCatalog").path("defaultPlan"));
            Iterator<Map.Entry<String, JsonNode>> reflectionIterator = payload.path("reflections").properties().iterator();
            String legacyPlanId = text(source, "id", "");
            while (reflectionIterator.hasNext()) {
                Map.Entry<String, JsonNode> row = reflectionIterator.next();
                if ((!defaultPlan && !row.getKey().startsWith(legacyPlanId + ":")) || (defaultPlan && row.getKey().contains(":"))) continue;
                JsonNode value = row.getValue();
                DailyReflection reflection = new DailyReflection(plan, date(value, "date"));
                reflection.update(clip(text(value, "completedSummary", ""), 2000), clip(text(value, "unfinishedSummary", ""), 2000),
                    clip(text(value, "blockers", ""), 2000), clip(text(value, "firstTaskTomorrow", ""), 1000),
                    value.path("energy").isInt() ? value.path("energy").asInt() : null, clip(text(value, "note", ""), 2000));
                reflections.save(reflection); importedReflections++;
            }
            if (defaultPlan) {
                int gateIndex = 0;
                Iterator<Map.Entry<String, JsonNode>> gateIterator = payload.path("gates").properties().iterator();
                while (gateIterator.hasNext()) {
                    Map.Entry<String, JsonNode> gate = gateIterator.next();
                    List<String> conditions = new ArrayList<>();
                    Iterator<String> conditionIterator = gate.getValue().path("checks").fieldNames();
                    while (conditionIterator.hasNext()) conditions.add(conditionIterator.next());
                    PlanMilestone milestone = new PlanMilestone(plan, gate.getKey(), plan.getStartDate().plusDays(Math.min(gateIndex++, java.time.temporal.ChronoUnit.DAYS.between(plan.getStartDate(), plan.getEndDate()))),
                        "从旧版闸门迁移；请按当前计划调整目标日期。", json(conditions));
                    List<Boolean> checks = conditions.stream().map(condition -> gate.getValue().path("checks").path(condition).asBoolean(false)).toList();
                    milestone.record(json(checks), clip(text(gate.getValue(), "note", ""), 2000), nullableInstant(gate.getValue().path("reviewedAt")));
                    milestones.save(milestone); milestoneIds.add(milestone.getId()); importedMilestones++;
                }
            }
        }

        for (JsonNode source : mainlineNodes(payload)) {
            Mainline line = mainlines.save(new Mainline(owner, clip(text(source, "name", "旧版主线"), 120),
                clip(text(source, "description", ""), 1000), clip(text(source, "color", "#002FA7"), 20)));
            mainlines.flush(); mainlineIds.add(line.getId());
            int order = 0;
            for (JsonNode itemNode : source.path("items")) {
                MainlineItem item = new MainlineItem(line);
                item.update(clip(text(itemNode, "title", "未命名步骤"), 180), clip(text(itemNode, "description", ""), 2000),
                    json(itemNode.path("acceptance").isArray() ? itemNode.path("acceptance") : mapper.createArrayNode()),
                    Math.max(1, itemNode.path("plannedMinutes").asInt(1)), order++);
                JsonNode oldProgress = progressMap.path(text(itemNode, "id", ""));
                MainlineItem.Status status = enumValue(MainlineItem.Status.class, text(oldProgress, "status", "pending"), MainlineItem.Status.PENDING);
                item.importState(status, oldProgress.path("accumulatedSeconds").asLong(0), clip(text(oldProgress, "note", ""), 2000),
                    nullableInstant(oldProgress.path("startedAt")), nullableInstant(oldProgress.path("completedAt")));
                mainlineItems.save(item); importedItems++;
            }
        }

        ImportReport report = new ImportReport(batch.getId(), preview.checksum(), preview.sourceVersion(), "COMPLETED",
            importedTags, importedTasks, importedProgress, importedEntries, importedReflections, importedItems, importedTemplates, importedMilestones,
            List.copyOf(planIds), List.copyOf(mainlineIds), List.copyOf(templateIds), List.copyOf(milestoneIds), preview.warnings(), Instant.now(), null);
        batch.complete(json(report));
        return report;
    }

    @Transactional(readOnly = true)
    List<History> history(String userId) {
        return imports.findByOwnerIdOrderByImportedAtDesc(userId).stream().map(row -> new History(row.getId(), row.getSourceVersion(),
            row.getChecksum(), row.getStatus(), row.getImportedAt(), row.getCompletedAt(), row.getRolledBackAt())).toList();
    }

    @Transactional
    ImportReport rollback(String userId, String importId) {
        LegacyImport batch = imports.findByIdAndOwnerId(importId, userId).orElseThrow(() -> new NotFoundException("迁移批次不存在。"));
        if ("ROLLED_BACK".equals(batch.getStatus())) return readReport(batch.getReportJson());
        if (!"COMPLETED".equals(batch.getStatus())) throw new ConflictException("只有已完成的迁移可以回滚。");
        ImportReport previous = readReport(batch.getReportJson());
        safeList(previous.createdTemplateIds()).forEach(id -> templates.findByIdAndOwnerId(id, userId).ifPresent(templates::delete));
        safeList(previous.createdMilestoneIds()).forEach(id -> milestones.findByIdAndPlanOwnerId(id, userId).ifPresent(milestones::delete));
        previous.createdMainlineIds().forEach(id -> mainlines.findByIdAndOwnerId(id, userId).ifPresent(mainlines::delete));
        previous.createdPlanIds().forEach(id -> plans.findByIdAndOwnerId(id, userId).ifPresent(plans::delete));
        ImportReport rolledBack = new ImportReport(previous.importId(), previous.checksum(), previous.sourceVersion(), "ROLLED_BACK",
            previous.tags(), previous.tasks(), previous.progressRecords(), previous.timeEntries(), previous.reflections(), previous.mainlineItems(), previous.templates(), previous.milestones(),
            previous.createdPlanIds(), previous.createdMainlineIds(), safeList(previous.createdTemplateIds()), safeList(previous.createdMilestoneIds()), previous.warnings(), previous.completedAt(), Instant.now());
        batch.rollback(json(rolledBack));
        return rolledBack;
    }

    @Transactional
    Snapshot preserve(String userId, JsonNode payload) {
        Preview preview = preview(payload);
        UserAccount owner = users.findById(userId).orElseThrow(() -> new NotFoundException("用户不存在。"));
        LegacyImport row = imports.findByOwnerIdAndChecksum(userId, preview.checksum())
            .orElseGet(() -> imports.save(new LegacyImport(owner, preview.sourceVersion(), preview.checksum(), payload.toString())));
        return new Snapshot(row.getId(), preview.sourceVersion(), preview.checksum(), "原始 JSON 已完整保存在服务器。");
    }

    private List<JsonNode> planNodes(JsonNode payload) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode defaultPlan = payload.path("migrationCatalog").path("defaultPlan");
        if (defaultPlan.isObject()) result.add(defaultPlan);
        payload.path("workspace").path("customPlans").forEach(result::add);
        return result;
    }

    private List<JsonNode> mainlineNodes(JsonNode payload) {
        List<JsonNode> result = new ArrayList<>();
        payload.path("migrationCatalog").path("builtInMainlines").forEach(result::add);
        payload.path("workspace").path("customMainlines").forEach(result::add);
        return result;
    }

    private void applyProgress(TaskProgress target, JsonNode source) {
        TaskProgress.Status status = enumValue(TaskProgress.Status.class, text(source, "status", "pending"), TaskProgress.Status.PENDING);
        LocalDate deferTo = source.path("deferTo").isTextual() && !source.path("deferTo").asText().isBlank() ? LocalDate.parse(source.path("deferTo").asText()) : null;
        target.importState(status, source.path("accumulatedSeconds").asLong(0), nullableInstant(source.path("startedAt")),
            nullableInstant(source.path("completedAt")), json(source.path("acceptanceChecks").isArray() ? source.path("acceptanceChecks") : mapper.createArrayNode()),
            clip(text(source, "note", ""), 2000), deferTo, clip(text(source, "skipReason", ""), 1000));
    }

    private String resourceUrl(JsonNode task) {
        JsonNode resources = task.path("resources");
        if (resources.isArray() && !resources.isEmpty()) return text(resources.get(0), "url", "");
        return text(task, "sourceRef", "");
    }

    private String checksum(JsonNode payload) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算备份校验值。", exception); }
    }
    private String text(JsonNode node, String field, String fallback) { JsonNode value = node.path(field); return value.isTextual() ? value.asText() : fallback; }
    private String clip(String value, int max) { return value == null ? "" : value.strip().substring(0, Math.min(value.strip().length(), max)); }
    private LocalDate date(JsonNode node, String field) { return LocalDate.parse(text(node, field, "")); }
    private LocalTime time(JsonNode node, String field) { String value = text(node, field, ""); return value.isBlank() ? null : LocalTime.parse(value); }
    private Instant nullableInstant(JsonNode node) { return node.isTextual() && !node.asText().isBlank() ? instant(node) : null; }
    private Instant instant(JsonNode node) {
        String value = node.asText();
        try { return Instant.parse(value); } catch (Exception ignored) {}
        try { return OffsetDateTime.parse(value).toInstant(); } catch (Exception ignored) {}
        return LocalDateTime.parse(value).atZone(LEGACY_ZONE).toInstant();
    }
    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try { return Enum.valueOf(type, value.toUpperCase()); } catch (Exception ignored) { return fallback; }
    }
    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("迁移报告无法序列化。", e); } }
    private ImportReport readReport(String value) { try { return mapper.readValue(value, ImportReport.class); } catch (Exception e) { throw new IllegalStateException("迁移报告已损坏。", e); } }

    record Preview(String checksum, String sourceVersion, boolean ready, int plans, int tasks, int mainlines,
                   int mainlineItems, int reflections, int timeEntries, int preservedOnly,
                   List<String> warnings, List<String> errors) {}
    record ImportReport(String importId, String checksum, String sourceVersion, String status, int tags, int tasks,
                        int progressRecords, int timeEntries, int reflections, int mainlineItems, int templates, int milestones,
                        List<String> createdPlanIds, List<String> createdMainlineIds, List<String> createdTemplateIds, List<String> createdMilestoneIds, List<String> warnings,
                        Instant completedAt, Instant rolledBackAt) {}
    record History(String importId, String sourceVersion, String checksum, String status, Instant createdAt,
                   Instant completedAt, Instant rolledBackAt) {}
    record Snapshot(String snapshotId, String sourceVersion, String checksum, String message) {}
}
