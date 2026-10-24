package com.oyts.planplatform.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/sessions/{id}/collaboration")
class AiCollaborationController {
    private final AiCollaborationService service;
    AiCollaborationController(AiCollaborationService service){this.service=service;}
    record Export(@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version,@NotBlank @Size(max=2000) String instructions,@NotNull Boolean consent) {}
    record Import(@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version,@NotBlank @Size(max=160000) String source,@Size(max=64) String fingerprint,Boolean confirmed) {}
    @PostMapping("/export") ResponseEntity<?> export(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Export request){return AiQuotaController.privateReply(service.export(user.getSubject(),id,request.version(),request.instructions(),request.consent()));}
    @PostMapping("/preview") ResponseEntity<?> preview(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Import request){return AiQuotaController.privateReply(service.preview(user.getSubject(),id,request.version(),request.source()));}
    @PostMapping("/accept") ResponseEntity<?> accept(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Import request){return AiQuotaController.privateReply(service.accept(user.getSubject(),id,request.version(),request.source(),request.fingerprint(),Boolean.TRUE.equals(request.confirmed())));}
    @ExceptionHandler(AiFailure.class) ResponseEntity<?> invalid(AiFailure ignored){return ResponseEntity.unprocessableEntity().header("Cache-Control","private, no-store").body(java.util.Map.of("message","协作草稿未通过完整校验，请检查日期、阶段、依赖和保护项。原输入保留。","code","INVALID_OUTPUT"));}
}
