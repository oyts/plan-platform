package com.oyts.planplatform.ai;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
class AiController {
    private final AiProperties config;
    private final AiQuotaService quotas;
    AiController(AiProperties config, AiQuotaService quotas) { this.config = config; this.quotas = quotas; }
    @GetMapping("/capabilities") ResponseEntity<?> capabilities() {
        // Never infer availability from the presence of a key or expose its path/content.
        return AiQuotaController.privateReply(Map.of("schemaVersion", 1, "available", false, "provider", "deepseek",
            "model", config.model(), "status", config.enabled() ? "INTEGRATION_PENDING" : "DISABLED",
            "message", "AI 基础已准备，真实对话尚未开放。", "transport", "ASYNC_POLLING", "timezone", "Asia/Shanghai"));
    }
    @GetMapping("/quota") ResponseEntity<?> quota(@AuthenticationPrincipal Jwt user) { return AiQuotaController.privateReply(quotas.usage(user.getSubject())); }
    @PostMapping("/jobs") ResponseEntity<?> unavailable() {
        return ResponseEntity.status(503).header("Cache-Control", "private, no-store")
            .body(Map.of("code", "AI_NOT_AVAILABLE", "message", "AI 对话尚未开放，请继续手动创建；现有计划不受影响。"));
    }
}
