package com.oyts.planplatform.feedback;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class FeedbackDtos {
    private FeedbackDtos() {}
    public record Create(@NotBlank @Pattern(regexp="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") String operationKey,
        @NotBlank @Pattern(regexp="BUG|CONNECTION|USABILITY|SUGGESTION|OTHER") String category,
        @NotBlank @Size(max=120) String title, @NotBlank @Size(max=4000) String steps,
        @NotBlank @Size(max=2000) String expectedResult, @NotBlank @Size(max=2000) String actualResult,
        Map<String,Object> diagnostic) {}
    public record Reply(@NotBlank @Size(max=4000) String body,@NotNull Long version) {}
    public record Handle(@NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|NEEDS_INFO|RESOLVED|CLOSED") String status,
        @Size(max=4000) String body,@NotNull Long version) {}
    public record Summary(String id,String title,String category,String status,long version,Instant createdAt,Instant updatedAt) {}
    public record Message(String id,String authorKind,String body,Instant createdAt) {}
    public record Detail(Summary ticket,String steps,String expectedResult,String actualResult,
        Map<String,Object> diagnostic,List<Message> messages) {}
}
