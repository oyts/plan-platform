package com.oyts.planplatform.mainline;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
final class MainlineDtos {
    private MainlineDtos() {}
    record MainlineRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description, @NotBlank @Size(max = 20) String color, Long version) {}
    record ItemRequest(@NotBlank @Size(max = 180) String title, @Size(max = 2000) String description, List<@NotBlank @Size(max = 500) String> acceptance, @Min(1) int plannedMinutes, @Min(0) int sortOrder, Long version) {}
    record CompleteRequest(@Size(max = 2000) String note, Long version) {}
    record ItemResponse(String id, String title, String description, List<String> acceptance, int plannedMinutes, int sortOrder, String status, boolean unlocked, long accumulatedSeconds, String note, Instant startedAt, Instant completedAt, long version) {}
    record MainlineResponse(String id, String name, String description, String color, long version, long completed, long total, List<ItemResponse> items) {}
}
