package com.oyts.planplatform.todo;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;

public final class TodoDtos {
    public record Create(@NotBlank @Size(max = 180) String title, @Size(max = 2000) String note,
                         @NotNull LocalDate dueDate) {}
    public record Update(@NotBlank @Size(max = 180) String title, @Size(max = 2000) String note,
                         @NotNull LocalDate dueDate, @NotNull Boolean done, @NotNull Boolean deleted,
                         @NotNull @PositiveOrZero Long version) {}
    public record Response(String id, String title, String note, LocalDate dueDate, boolean done,
                           boolean deleted, long version, Instant updatedAt) {}
    private TodoDtos() {}
}
