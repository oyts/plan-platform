package com.oyts.planplatform.mainline;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
final class MainlineDtos {
    private MainlineDtos() {}
    record MainlineRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description, @NotBlank @Size(max = 20) String color, Long version, String executionMode) {}
    record RoutingRequest(String stageId, @NotBlank String dependencyMode, @Size(max = 100) List<@NotBlank @Size(max = 36) String> prerequisiteIds) {}
    record ItemRequest(@NotBlank @Size(max = 180) String title, @Size(max = 2000) String description, List<@NotBlank @Size(max = 500) String> acceptance, @Min(1) int plannedMinutes, @Min(0) int sortOrder, Long version, @jakarta.validation.Valid RoutingRequest routing) {}
    record StageRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description, @Min(0) int sortOrder, Long version) {}
    record StageResponse(String id, String name, String description, int sortOrder, long version) {}
    record CompleteRequest(@Size(max = 2000) String note, Long version) {}
    record PauseRequest(@NotNull Long version, boolean paused) {}
    record ItemResponse(String id, String title, String description, List<String> acceptance, int plannedMinutes, int sortOrder, String status, boolean unlocked, long accumulatedSeconds, String note, Instant startedAt, Instant completedAt, long version, String stageId, String dependencyMode, List<String> prerequisiteIds, List<String> blockers) {}
    record MainlineResponse(String id, String name, String description, String color, long version, long completed, long total, List<ItemResponse> items, String executionMode, List<StageResponse> stages, int nextSortOrder, boolean paused) {}
}
