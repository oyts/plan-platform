package com.oyts.planplatform.common;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Release identity. Database readiness is separately available at actuator/health/readiness. */
@RestController
class ReleaseController {
    @Value("${app.version}") private String version;
    @GetMapping("/api/health") Map<String, Object> release() {
        return Map.of("ok", true, "service", "plan-platform-api", "version", version, "apiVersion", 1,
            "capabilities",Map.of("structureTemplates",true));
    }
}
