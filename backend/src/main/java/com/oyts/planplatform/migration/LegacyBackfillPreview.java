package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oyts.planplatform.common.ConflictException;
import com.oyts.planplatform.common.NotFoundException;
import com.oyts.planplatform.mainline.MainlineRepository;
import com.oyts.planplatform.plan.repository.PlanRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only preparation, deliberately separate from the future explicit write transaction. */
@Service
class LegacyBackfillPreview {
    private final LegacyImportRepository imports;
    private final PlanRepository plans;
    private final MainlineRepository mainlines;
    private final ObjectMapper mapper;

    LegacyBackfillPreview(LegacyImportRepository imports, PlanRepository plans,
                          MainlineRepository mainlines, ObjectMapper mapper) {
        this.imports = imports; this.plans = plans; this.mainlines = mainlines; this.mapper = mapper;
    }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    Preview preview(String owner, String importId) {
        LegacyImport batch = imports.findByIdAndOwnerId(importId, owner)
            .orElseThrow(() -> new NotFoundException("迁移批次不存在。"));
        if (!"COMPLETED".equals(batch.getStatus())) throw conflict("仅已完成批次可预检补迁。");
        JsonNode source = read(batch.getPayload()), report = read(batch.getReportJson());
        if (!digest(source.toString()).equals(batch.getChecksum())
            || !batch.getChecksum().equals(report.path("checksum").asText())
            || !importId.equals(report.path("importId").asText())
            || !"COMPLETED".equals(report.path("status").asText())) throw conflict("原始快照与迁移报告不一致。");
        JsonNode versionNode = report.path("recyclingVersion");
        if (!versionNode.isMissingNode() && (!versionNode.isIntegralNumber() || !versionNode.canConvertToInt()))
            throw conflict("迁移算法版本格式无效。");
        int version = versionNode.asInt(0);
        if (version < 0 || version > 1) throw conflict("未知迁移算法版本，请先核对记录。");
        LegacyRecycling.Converted converted;
        try { converted = LegacyRecycling.convert(source); }
        catch (IllegalArgumentException e) { throw conflict("原回收站快照无效，请保留记录并核对。"); }
        if (version == 1) return result(batch, true, converted.markersOnly(), List.of());

        // The old writer's ordering was default/built-in first, then workspace parents.
        // Never guess mappings from today's titles or scan another user's objects.
        Map<String, String> planIds = mapping(source, true, report.path("createdPlanIds"));
        Map<String, String> lineIds = mapping(source, false, report.path("createdMainlineIds"));
        List<Entry> entries = new ArrayList<>();
        var keys = converted.deleted().keySet().stream()
            .sorted(Comparator.comparing(LegacyRecycling.Key::type)
                .thenComparing(LegacyRecycling.Key::parent).thenComparing(LegacyRecycling.Key::id)).toList();
        for (var key : keys) {
            boolean parent = key.type().equals("plan") || key.type().equals("mainline");
            String targetId = null; Long parentVersion = null;
            String action = "CREATE_DELETED_PARENT";
            if (!parent) {
                boolean tag = key.type().equals("tag");
                String parentType = tag ? "plan" : "mainline";
                if (converted.deletion(parentType, "", key.parent()) != null) action = "IN_PARENT_SNAPSHOT";
                else {
                    targetId = (tag ? planIds : lineIds).get(key.parent());
                    if (targetId == null) throw conflict("原父对象映射缺失，不能猜测关联。");
                    if (tag) {
                        var found = plans.findByIdAndOwnerId(targetId, owner)
                            .orElseThrow(() -> conflict("原计划已不存在，保留快照等待核对。"));
                        if (found.getDeletedAt() != null) throw conflict("原计划已在回收站，先处理父对象状态。");
                        parentVersion = found.getVersion();
                    } else {
                        var found = mainlines.findByIdAndOwnerId(targetId, owner)
                            .orElseThrow(() -> conflict("原路线已不存在，保留快照等待核对。"));
                        if (found.getDeletedAt() != null) throw conflict("原路线已在回收站，先处理父对象状态。");
                        parentVersion = found.getVersion();
                    }
                    action = "ATTACH_DELETED_CHILD";
                }
            }
            entries.add(new Entry(key.type(), key.id(), key.parent(), converted.deleted().get(key), action, targetId, parentVersion));
        }
        return result(batch, false, converted.markersOnly(), List.copyOf(entries));
    }

    private Preview result(LegacyImport batch, boolean included, int markerCount, List<Entry> entries) {
        String fingerprint;
        try { fingerprint = digest(mapper.writeValueAsString(List.of(batch.getId(), batch.getChecksum(), included, markerCount, entries))); }
        catch (Exception e) { throw new IllegalStateException("补迁预检无法序列化。", e); }
        return new Preview(batch.getId(), batch.getChecksum(), fingerprint, included, false, markerCount, entries,
            "只读预检，不会写入。永久删除标记仅保留，不删除当前内容；正式补迁仍需版本复核、显式确认及持久防重回执。");
    }

    private Map<String, String> mapping(JsonNode source, boolean plan, JsonNode ids) {
        List<JsonNode> parents = new ArrayList<>();
        JsonNode catalog = source.path("migrationCatalog"), workspace = source.path("workspace");
        if (plan) { if (catalog.path("defaultPlan").isObject()) parents.add(catalog.path("defaultPlan")); }
        else catalog.path("builtInMainlines").forEach(parents::add);
        workspace.path(plan ? "customPlans" : "customMainlines").forEach(parents::add);
        if (!ids.isArray() || ids.size() != parents.size()) throw conflict("原父对象数量与报告不一致。");
        Map<String, String> result = new HashMap<>(); Set<String> targets = new HashSet<>();
        for (int i = 0; i < parents.size(); i++) {
            String old = parents.get(i).path("id").asText(""); JsonNode target = ids.get(i);
            if (old.isBlank() || !target.isTextual() || target.asText().isBlank()
                || result.putIfAbsent(old, target.asText()) != null || !targets.add(target.asText()))
                throw conflict("原父对象映射重复或缺失。");
        }
        return result;
    }
    private JsonNode read(String value) {
        try { JsonNode node = mapper.readTree(value); if (node == null || !node.isObject()) throw conflict("快照或报告格式损坏。"); return node; }
        catch (java.io.IOException | IllegalArgumentException e) { throw conflict("快照或报告格式损坏。"); }
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static ConflictException conflict(String message) { return new ConflictException("旧批次补迁预检：" + message); }
    record Entry(String type, String legacyId, String legacyParentId, Instant deletedAt,
                 String action, String targetParentId, Long parentVersion) {}
    record Preview(String importId, String checksum, String fingerprint, boolean alreadyIncluded,
                   boolean writeSupported, int retainedPermanentMarkers, List<Entry> entries, String notice) {}
}
