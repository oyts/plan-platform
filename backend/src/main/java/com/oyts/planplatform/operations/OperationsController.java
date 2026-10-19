package com.oyts.planplatform.operations;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/operations") @PreAuthorize("hasRole('ADMIN')")
class OperationsController {
    private final OperationsService service;private final OperationsMailDelivery mail;OperationsController(OperationsService service,OperationsMailDelivery mail){this.service=service;this.mail=mail;}
    @GetMapping("/mail-status") Object mailStatus(){return mail.status();}
    @GetMapping("/status") Object status(){return service.status();}
    @GetMapping("/alerts") Object alerts(){return service.alerts();}
    @GetMapping("/policy-audit") Object policyAudit(@RequestParam(defaultValue="0")int page){return service.policyAudit(page);}
    @PostMapping("/evaluate") Object evaluate(){return service.evaluate();}
    @PutMapping("/policy") Object policy(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody OperationsService.Policy request){return service.updatePolicy(jwt.getSubject(),request);}
}
