package com.oyts.planplatform.reflection;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class ReflectionDtos {
 private ReflectionDtos(){}
 public enum Kind{DAY,WEEK}
 public record Notes(LocalDate date,String wins,String blockers,String nextFocus,String energy,String burden,boolean reviewed,Long version,Instant updatedAt){}
 public record Save(@Size(max=4000) String wins,@Size(max=4000) String blockers,@Size(max=4000) String nextFocus,
  @NotNull @Pattern(regexp="UNSPECIFIED|LOW|STEADY|HIGH") String energy,
  @NotNull @Pattern(regexp="UNSPECIFIED|LIGHT|MANAGEABLE|HEAVY") String burden,@NotNull Boolean reviewed,@PositiveOrZero Long version){}
 public record Mode(long arranged,long arrangedCompleted,long open,long skipped,long actuallyCompleted,long completedWithoutTime){}
 public record Facts(Kind kind,LocalDate start,LocalDate end,Instant calculatedAt,String timezone,Mode plan,Mode todo,long routeCompleted,long recordedPlanSeconds,boolean hasApplicableItems,boolean allApplicableComplete){}
 public record Item(String kind,String id,String parentId,String title,LocalDate arrangedDate,String status,Instant completedAt){}
 public record Preferences(@NotNull Boolean completionGuide,@NotNull Boolean dailyEnabled,
  @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String dailyTime,
  @NotNull Boolean weeklyEnabled,@Min(1) @Max(7) int weekDay,
  @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String weeklyTime,
  @NotNull Boolean trendEnabled,Long version){public static Preferences defaults(){return new Preferences(true,false,"22:30",false,7,"22:30",true,null);}}
 public record Guide(String kind,LocalDate period,String message,String path){}
 public record Trend(boolean eligible,String reason,Instant nextEligibleAt,boolean increaseAllowed,List<Facts> weeks,String lastDecision){}
 public record TrendDecision(@NotNull @Pattern(regexp="OPENED|DECLINED|LATER") String decision){}
 public record PreviewRequest(@NotNull Kind kind,@NotNull LocalDate start,@NotNull Boolean includeNotes,
  @Pattern(regexp="PLAN|ROUTE|TODO") String relatedMode,@Pattern(regexp="[a-f0-9-]{36}") String relatedId){}
 public record Preview(Kind kind,LocalDate start,String fingerprint,String contentJson){}
 public record Analyse(@NotNull Kind kind,@NotNull LocalDate start,@NotNull Boolean includeNotes,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String fingerprint,@NotNull Boolean consent,
  @Pattern(regexp="PLAN|ROUTE|TODO") String relatedMode,@Pattern(regexp="[a-f0-9-]{36}") String relatedId){}
}
