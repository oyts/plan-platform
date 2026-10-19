package com.oyts.planplatform.reminders;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class ReminderDtos {
    private ReminderDtos() {}
    public record Preferences(@NotNull Boolean advanceEnabled, @Min(1) @Max(120) int advanceMinutes,
        @NotNull Boolean startEnabled, @NotNull Boolean todoEnabled,
        @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String todoTime,
        @NotBlank @Size(max=60) String zone,
        @NotNull Boolean quietEnabled,
        @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String quietFrom,
        @NotBlank @Pattern(regexp="(?:[01]\\d|2[0-3]):[0-5]\\d") String quietUntil,
        @NotNull Boolean hideDetails, @Size(max=36) String primaryDeviceId, Long version) {
        public static Preferences defaults() { return new Preferences(false,5,false,false,"22:30","Asia/Shanghai",false,"23:00","07:00",true,null,null); }
    }
    public record Register(@Pattern(regexp="WINDOWS|ANDROID") @NotNull String platform, @NotBlank @Size(max=40) String label) {}
    public record Registration(String id, String token, Instant expiresAt) {}
    public record Device(String id, String platform, String label, boolean enabled, Instant expiresAt, Instant lastSeenAt) {}
    public record Notice(String key, String title, String body, String path, long expiresAt) {}
    public record Poll(long serverTime, long nextWakeAt, List<Notice> notices, List<String> activeKeys, boolean sourceTruncated) {}
}
