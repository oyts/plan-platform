package com.oyts.planplatform.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

/** Versioned, untrusted model output. Parsing never writes business data. */
public final class AiDrafts {
    private AiDrafts() {}
    public enum Mode { PLAN, ROUTE, TODO }
    public enum ChangeType { ADD, MODIFY }
    public record Stage(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String localId,
                        @NotBlank @Size(max=120) String title) {}
    public record Item(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String localId,
                       @NotBlank @Size(max=120) String title, @NotNull @Size(max=2000) String note,
                       LocalDate dueDate, @Min(0) @Max(1440) int plannedMinutes, String stageId,
                       @NotNull @Size(max=20) List<@NotBlank String> prerequisites) {}
    public record Draft(@Min(1) @Max(1) int schemaVersion, @NotNull Mode mode, @Min(0) long revision,
                        @NotBlank @Size(max=120) String title, LocalDate startDate, LocalDate endDate,
                        @NotNull @Size(max=7) List<@NotNull @Min(1) @Max(7) Integer> restDays,
                        @NotNull @Size(max=30) List<@NotNull @Valid Stage> stages,
                        @NotNull @Size(min=1,max=100) List<@NotNull @Valid Item> items) {}
    public record Change(@NotNull ChangeType type, @NotBlank @Size(max=100) String target,
                         @NotNull @Valid Item item) {}
    public record Patch(@Min(1) @Max(1) int schemaVersion, @Min(0) long draftRevision,
                        @NotNull @Size(min=1,max=100) List<@NotNull @Valid Change> changes,
                        @NotNull @Size(max=100) Map<@NotBlank String,@NotNull @Min(0) Long> baseVersions) {}
    public static Draft parseDraft(String json, ObjectMapper mapper, Validator validator) {
        Draft draft = parse(json, Draft.class, mapper, validator);
        Set<String> stageIds = new HashSet<>();
        for (Stage stage : draft.stages) if (!stageIds.add(stage.localId)) invalid();
        if (new HashSet<>(draft.restDays).size() != draft.restDays.size()) invalid();
        switch (draft.mode) {
            case PLAN -> {
                if (draft.startDate == null || draft.endDate == null || draft.endDate.isBefore(draft.startDate) || !draft.stages.isEmpty()) invalid();
            }
            case ROUTE -> { if (draft.startDate != null || draft.endDate != null || !draft.restDays.isEmpty()) invalid(); }
            case TODO -> { if (draft.startDate != null || draft.endDate != null || !draft.restDays.isEmpty() || !draft.stages.isEmpty()) invalid(); }
        }
        Map<String,Item> items = new HashMap<>();
        for (Item item : draft.items) {
            if (items.put(item.localId,item) != null) invalid();
            if (draft.mode != Mode.ROUTE && (item.stageId != null || !item.prerequisites.isEmpty())) invalid();
            if (draft.mode == Mode.ROUTE && (item.dueDate != null || item.stageId != null && !stageIds.contains(item.stageId))) invalid();
            if (draft.mode == Mode.TODO && item.dueDate == null) invalid();
            if (draft.mode == Mode.PLAN && (item.dueDate == null || item.dueDate.isBefore(draft.startDate) || item.dueDate.isAfter(draft.endDate))) invalid();
            if (new HashSet<>(item.prerequisites).size() != item.prerequisites.size()) invalid();
        }
        Set<String> visiting = new HashSet<>(), done = new HashSet<>();
        for (String id : items.keySet()) visit(id, items, visiting, done);
        return draft;
    }
    public static Patch parsePatch(String json, ObjectMapper mapper, Validator validator) {
        Patch patch = parse(json, Patch.class, mapper, validator);
        Set<String> targets = new HashSet<>();
        for (Change change : patch.changes) {
            if (!targets.add(change.target) || change.type == ChangeType.MODIFY && !patch.baseVersions.containsKey(change.target)) invalid();
        }
        return patch;
    }
    private static void visit(String id, Map<String,Item> items, Set<String> visiting, Set<String> done) {
        if (done.contains(id)) return;
        if (!items.containsKey(id) || !visiting.add(id)) invalid();
        for (String dependency : items.get(id).prerequisites) visit(dependency,items,visiting,done);
        visiting.remove(id); done.add(id);
    }
    private static <T> T parse(String json, Class<T> type, ObjectMapper source, Validator validator) {
        if (json == null || json.length() > 128000) invalid();
        try {
            ObjectMapper mapper = source.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            mapper.coercionConfigFor(com.fasterxml.jackson.databind.type.LogicalType.Integer)
                .setCoercion(com.fasterxml.jackson.databind.cfg.CoercionInputShape.String, com.fasterxml.jackson.databind.cfg.CoercionAction.Fail);
            T result = mapper.readValue(json,type);
            if (result == null || !validator.validate(result).isEmpty()) invalid();
            return result;
        } catch (Exception failure) { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
    }
    private static void invalid() { throw new AiFailure(AiFailure.Code.INVALID_OUTPUT); }
}
