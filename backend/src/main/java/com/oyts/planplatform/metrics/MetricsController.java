package com.oyts.planplatform.metrics;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import org.springframework.http.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
class MetricsController {
    private final WebMetricsService web;private final GrowthMetrics growth;private final RuntimeTelemetry runtime;
    MetricsController(WebMetricsService web,GrowthMetrics growth,RuntimeTelemetry runtime){this.web=web;this.growth=growth;this.runtime=runtime;}
    private static <T> ResponseEntity<T> privateReply(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(body);}
    @GetMapping("/api/public/analytics/config") ResponseEntity<WebMetricsService.Config> config(){return privateReply(web.config());}
    @PostMapping("/api/public/analytics/ticket") ResponseEntity<WebMetricsService.Ticket> ticket(HttpServletRequest req,@RequestBody JsonNode body){return privateReply(web.ticket(req,body));}
    @PostMapping("/api/public/analytics/event") ResponseEntity<Void> event(HttpServletRequest req,@RequestBody JsonNode body){web.event(req,body);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @GetMapping("/api/admin/metrics/website") @PreAuthorize("hasRole('ADMIN')") ResponseEntity<WebMetricsService.Report> website(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return privateReply(web.report(from,to));}
    @GetMapping("/api/admin/metrics/growth") @PreAuthorize("hasRole('ADMIN')") ResponseEntity<GrowthMetrics.Report> growth(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to){return privateReply(growth.report(from,to));}
    @GetMapping("/api/admin/metrics/runtime") @PreAuthorize("hasRole('ADMIN')") ResponseEntity<RuntimeTelemetry.Report> runtime(){return privateReply(runtime.report());}
}
