package com.oyts.planplatform.ai;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
class AiController {
    private final AiProperties config;
    private final AiQuotaService quotas;
    private final AiConversationService sessions;
    AiController(AiProperties config, AiQuotaService quotas,AiConversationService sessions) { this.config = config; this.quotas = quotas; this.sessions=sessions; }
    record Create(AiDrafts.Mode mode,@NotNull Boolean consent) {}
    record Adjust(@NotNull AiDrafts.Mode mode,@NotBlank @Pattern(regexp="[a-f0-9-]{36}") String targetId,@NotNull Boolean consent) {}
    record Choose(@NotNull AiDrafts.Mode mode,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Draft(@NotBlank @Size(max=128000) String draftJson,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Apply(@NotNull Boolean confirmed,@NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    record Message(@NotBlank @Size(max=8000) String message,@NotBlank @Pattern(regexp="[A-Za-z0-9._:-]{8,100}") String operationKey,
                   @NotNull @Min(0) @JsonDeserialize(using=AiJsonIntegers.LongValue.class) Long version) {}
    @GetMapping("/capabilities") ResponseEntity<?> capabilities() {
        // Never infer availability from the presence of a key or expose its path/content.
        boolean available=config.enabled() && !config.apiKeyFile().isBlank();
        return AiQuotaController.privateReply(Map.of("schemaVersion", 1, "available", available, "provider", "deepseek",
            "model", config.model(), "status", available?"READY":config.enabled()?"NOT_CONFIGURED":"DISABLED",
            "message", available?"AI 按需调用；确认草稿后才生成真实内容。":"AI 暂未开放，可继续手动创建或导入草稿。", "transport", "ASYNC_POLLING", "timezone", "Asia/Shanghai",
            "maxInputChars",Math.min(config.maxInputChars(),8000),"adjustmentEnabled",true));
    }
    @GetMapping("/quota") ResponseEntity<?> quota(@AuthenticationPrincipal Jwt user) { return AiQuotaController.privateReply(quotas.usage(user.getSubject())); }
    @PostMapping("/jobs") ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).header("Cache-Control", "private, no-store")
            .body(Map.of("code", "AI_NOT_AVAILABLE", "message", "AI 对话尚未开放，请继续手动创建；现有计划不受影响。"));
    }
    @GetMapping("/sessions") ResponseEntity<?> list(@AuthenticationPrincipal Jwt user) { return AiQuotaController.privateReply(sessions.list(user.getSubject())); }
    @PostMapping("/sessions") ResponseEntity<?> create(@AuthenticationPrincipal Jwt user,@Valid @RequestBody Create request) { return AiQuotaController.privateReply(sessions.create(user.getSubject(),request.mode(),request.consent())); }
    @PostMapping("/adjustments") ResponseEntity<?> adjust(@AuthenticationPrincipal Jwt user,@Valid @RequestBody Adjust request) { return AiQuotaController.privateReply(sessions.createAdjustment(user.getSubject(),request.mode(),request.targetId(),request.consent())); }
    @GetMapping("/sessions/{id}") ResponseEntity<?> read(@AuthenticationPrincipal Jwt user,@PathVariable String id) { return AiQuotaController.privateReply(sessions.read(user.getSubject(),id)); }
    @PutMapping("/sessions/{id}/mode") ResponseEntity<?> choose(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Choose request) { return AiQuotaController.privateReply(sessions.choose(user.getSubject(),id,request.version(),request.mode())); }
    @PutMapping("/sessions/{id}/draft") ResponseEntity<?> draft(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Draft request) { return AiQuotaController.privateReply(sessions.saveDraft(user.getSubject(),id,request.version(),request.draftJson())); }
    @PostMapping("/sessions/{id}/apply") ResponseEntity<?> apply(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Apply request) { return AiQuotaController.privateReply(sessions.apply(user.getSubject(),id,request.version(),request.confirmed())); }
    @PostMapping("/sessions/{id}/jobs") ResponseEntity<?> enqueue(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody Message request) {
        return ResponseEntity.accepted().header("Cache-Control","private, no-store").body(sessions.enqueue(user.getSubject(),id,request.version(),request.operationKey(),request.message()));
    }
    @DeleteMapping("/sessions/{id}") ResponseEntity<?> delete(@AuthenticationPrincipal Jwt user,@PathVariable String id,@RequestParam long version) { sessions.delete(user.getSubject(),id,version);return ResponseEntity.noContent().header("Cache-Control","private, no-store").build(); }
    @ExceptionHandler(AiFailure.class) ResponseEntity<?> failure(AiFailure failure) { return ResponseEntity.status(failure.code()==AiFailure.Code.INVALID_OUTPUT?422:503).header("Cache-Control","private, no-store").body(Map.of("code",failure.code().name(),"message",failure.code()==AiFailure.Code.INVALID_OUTPUT?"草稿格式或内容未通过校验，请检查日期、步骤与依赖后重试。":failure.getMessage())); }
}
