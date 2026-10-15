package com.oyts.planplatform.ai;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMIN')")
class AiQuotaController {
    private final AiQuotaService quotas;
    AiQuotaController(AiQuotaService quotas) { this.quotas = quotas; }
    record PolicyChange(@NotNull @Min(0) @Max(10000) @JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer dailyLimit,
                        @NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record UserChange(@Min(0) @Max(10000) @JsonDeserialize(using=AiJsonIntegers.IntValue.class) Integer dailyLimit,
                      @NotNull Boolean paused, @NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    @GetMapping("/settings") ResponseEntity<?> settings() { return privateReply(quotas.policy()); }
    @PutMapping("/settings") ResponseEntity<?> settings(@AuthenticationPrincipal Jwt user, @Valid @RequestBody PolicyChange request) {
        return privateReply(quotas.changePolicy(user.getSubject(), request.dailyLimit(), request.version()));
    }
    @GetMapping("/users") ResponseEntity<?> users(@RequestParam(defaultValue="") String query, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return privateReply(quotas.members(query, page, size));
    }
    @PutMapping("/users/{id}") ResponseEntity<?> user(@AuthenticationPrincipal Jwt actor, @PathVariable String id, @Valid @RequestBody UserChange request) {
        return privateReply(quotas.changeUser(actor.getSubject(), id, request.dailyLimit(), request.paused(), request.version()));
    }
    @GetMapping("/audit") ResponseEntity<?> audit(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return privateReply(quotas.history(page, size));
    }
    static ResponseEntity<?> privateReply(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body); }
}
