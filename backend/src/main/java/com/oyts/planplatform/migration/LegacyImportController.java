package com.oyts.planplatform.migration;
import com.fasterxml.jackson.databind.JsonNode;
import com.oyts.planplatform.auth.*;
import com.oyts.planplatform.common.NotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/migrations")
class LegacyImportController {
    private final UserAccountRepository users; private final LegacyImportRepository imports;
    LegacyImportController(UserAccountRepository users, LegacyImportRepository imports) { this.users = users; this.imports = imports; }
    @PostMapping("/legacy-snapshot") @ResponseStatus(HttpStatus.CREATED) @Transactional
    ImportResponse preserve(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode payload) {
        if (!payload.isObject() || !payload.has("schemaVersion")) throw new IllegalArgumentException("请选择旧版计划控制台导出的 JSON 文件。");
        UserAccount owner = users.findById(jwt.getSubject()).orElseThrow(() -> new NotFoundException("用户不存在。"));
        String version = payload.path("planVersion").asText("unknown");
        LegacyImport saved = imports.save(new LegacyImport(owner, version, payload.toString()));
        int customPlans = payload.path("workspace").path("customPlans").isArray() ? payload.path("workspace").path("customPlans").size() : 0;
        int progressRecords = payload.path("progress").isObject() ? payload.path("progress").size() : 0;
        return new ImportResponse(saved.getId(), version, customPlans, progressRecords,
            List.of("原始 JSON 已完整保存在服务器。", "下一阶段将把内置暑假计划和自定义主线映射为关系型数据。"));
    }
    record ImportResponse(String snapshotId, String sourceVersion, int customPlans, int progressRecords, List<String> messages) {}
}
