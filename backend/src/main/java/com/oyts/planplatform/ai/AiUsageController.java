package com.oyts.planplatform.ai;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ai/usage")
@PreAuthorize("hasRole('ADMIN')")
class AiUsageController {
    private final AiUsageService service;AiUsageController(AiUsageService service){this.service=service;}
    @GetMapping ResponseEntity<?> get(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return AiQuotaController.privateReply(service.report(from,to,page,size));}
}
