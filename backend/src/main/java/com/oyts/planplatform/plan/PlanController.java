package com.oyts.planplatform.plan;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlanController {
    private final PlanService service;
    public PlanController(PlanService service) { this.service = service; }

    @GetMapping("/plans")
    List<PlanDtos.PlanResponse> plans(@AuthenticationPrincipal Jwt jwt) { return service.listPlans(jwt.getSubject()); }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.PlanResponse createPlan(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanDtos.PlanRequest request) {
        return service.createPlan(jwt.getSubject(), request);
    }

    @PutMapping("/plans/{planId}")
    PlanDtos.PlanResponse updatePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @Valid @RequestBody PlanDtos.PlanRequest request) {
        return service.updatePlan(jwt.getSubject(), planId, request);
    }

    @DeleteMapping("/plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archivePlan(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) { service.archivePlan(jwt.getSubject(), planId); }

    @GetMapping("/plans/{planId}/tags")
    List<PlanDtos.TagResponse> tags(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) { return service.listTags(jwt.getSubject(), planId); }

    @PostMapping("/plans/{planId}/tags")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.TagResponse createTag(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @Valid @RequestBody PlanDtos.TagRequest request) {
        return service.createTag(jwt.getSubject(), planId, request);
    }

    @PutMapping("/tags/{tagId}")
    PlanDtos.TagResponse updateTag(@AuthenticationPrincipal Jwt jwt, @PathVariable String tagId, @Valid @RequestBody PlanDtos.TagRequest request) {
        return service.updateTag(jwt.getSubject(), tagId, request);
    }

    @DeleteMapping("/tags/{tagId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTag(@AuthenticationPrincipal Jwt jwt, @PathVariable String tagId) { service.deleteTag(jwt.getSubject(), tagId); }

    @GetMapping("/plans/{planId}/tasks")
    List<PlanDtos.TaskResponse> tasks(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) { return service.listTasks(jwt.getSubject(), planId); }

    @GetMapping("/plans/{planId}/tasks/page")
    PlanDtos.PageResponse<PlanDtos.TaskResponse> taskPage(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return service.taskPage(jwt.getSubject(), planId, from, to, page, size);
    }

    @PostMapping("/plans/{planId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.TaskResponse createTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @Valid @RequestBody PlanDtos.TaskRequest request) {
        return service.createTask(jwt.getSubject(), planId, request);
    }

    @PostMapping("/plans/{planId}/tasks/recurring")
    @ResponseStatus(HttpStatus.CREATED)
    List<PlanDtos.TaskResponse> createRecurringTasks(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
                                                       @Valid @RequestBody PlanDtos.RecurringTaskRequest request) {
        return service.createRecurringTasks(jwt.getSubject(), planId, request);
    }

    @PutMapping("/tasks/{taskId}")
    PlanDtos.TaskResponse updateTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.TaskRequest request) {
        return service.updateTask(jwt.getSubject(), taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) { service.deleteTask(jwt.getSubject(), taskId); }

    @GetMapping("/plans/{planId}/milestones")
    List<PlanDtos.MilestoneResponse> milestones(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId) { return service.listMilestones(jwt.getSubject(), planId); }

    @PostMapping("/plans/{planId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.MilestoneResponse createMilestone(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @Valid @RequestBody PlanDtos.MilestoneRequest request) {
        return service.createMilestone(jwt.getSubject(), planId, request);
    }

    @PutMapping("/milestones/{milestoneId}")
    PlanDtos.MilestoneResponse updateMilestone(@AuthenticationPrincipal Jwt jwt, @PathVariable String milestoneId, @Valid @RequestBody PlanDtos.MilestoneRequest request) {
        return service.updateMilestone(jwt.getSubject(), milestoneId, request);
    }

    @PutMapping("/milestones/{milestoneId}/progress")
    PlanDtos.MilestoneResponse updateMilestoneProgress(@AuthenticationPrincipal Jwt jwt, @PathVariable String milestoneId, @Valid @RequestBody PlanDtos.MilestoneProgressRequest request) {
        return service.updateMilestoneProgress(jwt.getSubject(), milestoneId, request);
    }

    @DeleteMapping("/milestones/{milestoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMilestone(@AuthenticationPrincipal Jwt jwt, @PathVariable String milestoneId) { service.deleteMilestone(jwt.getSubject(), milestoneId); }

    @GetMapping("/templates")
    List<PlanDtos.TemplateResponse> templates(@AuthenticationPrincipal Jwt jwt) { return service.listTemplates(jwt.getSubject()); }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.TemplateResponse createTemplate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanDtos.TemplateRequest request) { return service.createTemplate(jwt.getSubject(), request); }

    @PutMapping("/templates/{templateId}")
    PlanDtos.TemplateResponse updateTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable String templateId, @Valid @RequestBody PlanDtos.TemplateRequest request) {
        return service.updateTemplate(jwt.getSubject(), templateId, request);
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable String templateId) { service.deleteTemplate(jwt.getSubject(), templateId); }

    @PostMapping("/templates/{templateId}/instantiate")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.TaskResponse instantiateTemplate(@AuthenticationPrincipal Jwt jwt, @PathVariable String templateId, @Valid @RequestBody PlanDtos.InstantiateTemplateRequest request) {
        return service.instantiateTemplate(jwt.getSubject(), templateId, request);
    }

    @PutMapping("/tasks/{taskId}/progress")
    PlanDtos.ProgressResponse updateProgress(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.ProgressRequest request) {
        return service.updateProgress(jwt.getSubject(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/timer/start")
    PlanDtos.ProgressResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @RequestParam(required = false) Long version) {
        return service.start(jwt.getSubject(), taskId, version);
    }

    @PostMapping("/tasks/{taskId}/timer/pause")
    PlanDtos.ProgressResponse pause(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @RequestParam(required = false) Long version) {
        return service.pause(jwt.getSubject(), taskId, version);
    }

    @PostMapping("/tasks/{taskId}/complete")
    PlanDtos.ProgressResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.CompleteRequest request) {
        return service.complete(jwt.getSubject(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/manual-time")
    PlanDtos.ProgressResponse manualTime(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.ManualTimeRequest request) {
        return service.addManualTime(jwt.getSubject(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/defer")
    PlanDtos.ProgressResponse defer(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.DeferRequest request) {
        return service.defer(jwt.getSubject(), taskId, request);
    }

    @GetMapping("/tasks/carryover")
    List<PlanDtos.TaskResponse> carryover(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.carryover(jwt.getSubject(), from, to);
    }

    @GetMapping("/dashboard/today")
    PlanDtos.TodayResponse today(@AuthenticationPrincipal Jwt jwt,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.today(jwt.getSubject(), date == null ? LocalDate.now() : date);
    }

    @PutMapping("/plans/{planId}/reflections/{date}")
    PlanDtos.ReflectionResponse reflection(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
                                           @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @Valid @RequestBody PlanDtos.ReflectionRequest request) {
        return service.saveReflection(jwt.getSubject(), planId, date, request);
    }

    @GetMapping("/plans/{planId}/reflections/page")
    PlanDtos.PageResponse<PlanDtos.ReflectionResponse> reflectionPage(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.reflectionPage(jwt.getSubject(), planId, from, to, page, size);
    }

    @GetMapping("/time-entries")
    PlanDtos.PageResponse<PlanDtos.TimeEntryResponse> timeLedger(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.timeLedger(jwt.getSubject(), from, to, page, size);
    }

    @GetMapping(value = "/time-entries/export", produces = "text/csv;charset=UTF-8")
    ResponseEntity<String> exportTimeLedger(@AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=plan-time-ledger.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body("\uFEFF" + service.timeLedgerCsv(jwt.getSubject(), from, to));
    }

    @GetMapping("/trash")
    List<PlanDtos.TrashResponse> trash(@AuthenticationPrincipal Jwt jwt) { return service.trash(jwt.getSubject()); }

    @PostMapping("/trash/{type}/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void restoreTrash(@AuthenticationPrincipal Jwt jwt, @PathVariable String type, @PathVariable String id) { service.restoreTrash(jwt.getSubject(), type, id); }

    @DeleteMapping("/trash/{type}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void purgeTrash(@AuthenticationPrincipal Jwt jwt, @PathVariable String type, @PathVariable String id) { service.purgeTrash(jwt.getSubject(), type, id); }

    @GetMapping("/plans/{planId}/stats")
    PlanDtos.StatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.stats(jwt.getSubject(), planId, from, to);
    }

    @GetMapping("/plans/{planId}/stats/daily")
    PlanDtos.PageResponse<PlanDtos.DailyStat> dailyStats(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.dailyStats(jwt.getSubject(), planId, from, to, page, size);
    }
}
