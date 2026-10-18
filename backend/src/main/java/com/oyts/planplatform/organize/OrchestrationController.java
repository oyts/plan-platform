package com.oyts.planplatform.organize;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/organize")
class OrchestrationController {
    private final OrchestrationService service;
    OrchestrationController(OrchestrationService service){this.service=service;}
    @GetMapping("/routes") Object routes(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="ACTIVE") String state){return service.routes(jwt.getSubject(),page,size,search,state);}
    @PostMapping("/copy/preview") Object copyPreview(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OrchestrationService.Copy request){return service.copyPreview(jwt.getSubject(),request);}
    @PostMapping("/copy/apply") Object copy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OrchestrationService.CopyApply request){return service.copy(jwt.getSubject(),request);}
    @PostMapping("/batch/preview") Object batchPreview(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OrchestrationService.Batch request){return service.batchPreview(jwt.getSubject(),request);}
    @PostMapping("/batch/apply") Object batch(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OrchestrationService.BatchApply request){return service.batch(jwt.getSubject(),request);}
}
