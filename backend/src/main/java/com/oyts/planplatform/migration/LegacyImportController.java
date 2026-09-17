package com.oyts.planplatform.migration;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/migrations")
class LegacyImportController {
    private final LegacyMigrationService service;
    LegacyImportController(LegacyMigrationService service) { this.service = service; }

    @PostMapping("/legacy/preview")
    LegacyMigrationService.Preview preview(@RequestBody JsonNode payload) { return service.preview(payload); }

    @PostMapping("/legacy/import")
    @ResponseStatus(HttpStatus.CREATED)
    LegacyMigrationService.ImportReport execute(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode payload) {
        return service.execute(jwt.getSubject(), payload);
    }

    @GetMapping("/legacy")
    List<LegacyMigrationService.History> history(@AuthenticationPrincipal Jwt jwt) { return service.history(jwt.getSubject()); }

    @PostMapping("/legacy/{importId}/rollback")
    LegacyMigrationService.ImportReport rollback(@AuthenticationPrincipal Jwt jwt, @PathVariable String importId) {
        return service.rollback(jwt.getSubject(), importId);
    }

    @PostMapping("/legacy-snapshot")
    @ResponseStatus(HttpStatus.CREATED)
    LegacyMigrationService.Snapshot preserve(@AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode payload) {
        return service.preserve(jwt.getSubject(), payload);
    }
}
