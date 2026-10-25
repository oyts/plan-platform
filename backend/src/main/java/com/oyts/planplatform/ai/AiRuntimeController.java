package com.oyts.planplatform.ai;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController @RequestMapping("/api/admin/ai/runtime") @PreAuthorize("hasRole('ADMIN')")
class AiRuntimeController {
    private final AiRuntimePolicy policy;AiRuntimeController(AiRuntimePolicy policy){this.policy=policy;}
    record Change(@NotNull AiRuntimePolicy.Limits limits,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Restore(@NotNull Boolean confirmed,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    private <T> ResponseEntity<T> reply(T value){return ResponseEntity.ok().header("Cache-Control","private, no-store").body(value);}
    @GetMapping ResponseEntity<AiRuntimePolicy.Policy> current(){return reply(policy.current());}
    @PutMapping ResponseEntity<AiRuntimePolicy.Policy> change(@AuthenticationPrincipal Jwt actor,@Valid @RequestBody Change r){return reply(policy.change(actor.getSubject(),r.limits(),r.version()));}
    @GetMapping("/audit") ResponseEntity<AiQuotaService.Page<AiRuntimePolicy.Audit>> history(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return reply(policy.history(page,size));}
    @PostMapping("/restore/{id}") ResponseEntity<AiRuntimePolicy.Policy> restore(@AuthenticationPrincipal Jwt actor,@PathVariable String id,@Valid @RequestBody Restore r){return reply(policy.restore(actor.getSubject(),id,r.version(),r.confirmed()));}
}
