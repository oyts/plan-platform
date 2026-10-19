package com.oyts.planplatform.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/ai/saved-drafts")
class AiWorkbenchController {
    private final AiWorkbenchService service;
    AiWorkbenchController(AiWorkbenchService service){this.service=service;}
    record Save(@NotBlank @Size(max=120) String name,@NotBlank @Size(max=245760) String contentJson,@NotNull @Min(-1) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Pick(@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String id,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Clear(@NotNull @Size(min=1,max=30) List<@Valid Pick> selected) {}
    record Resume(@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version,@NotNull Boolean consent) {}
    @GetMapping ResponseEntity<?> list(@AuthenticationPrincipal Jwt user){return AiQuotaController.privateReply(service.list(user.getSubject()));}
    @GetMapping("/{id}") ResponseEntity<?> read(@AuthenticationPrincipal Jwt user,@PathVariable String id){return AiQuotaController.privateReply(service.read(user.getSubject(),id));}
    @PutMapping("/{id:[a-f0-9-]{36}}") ResponseEntity<?> save(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Save body){return AiQuotaController.privateReply(service.save(user.getSubject(),id,body.name(),body.version(),body.contentJson()));}
    @PostMapping("/clear") ResponseEntity<?> clear(@AuthenticationPrincipal Jwt user,@Valid @RequestBody Clear body){return AiQuotaController.privateReply(service.clear(user.getSubject(),body.selected().stream().map(p->new AiWorkbenchService.Selection(p.id(),p.version())).toList()));}
    @PostMapping("/{id}/resume") ResponseEntity<?> resume(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Resume body){return AiQuotaController.privateReply(service.resume(user.getSubject(),id,body.version(),body.consent()));}
}
