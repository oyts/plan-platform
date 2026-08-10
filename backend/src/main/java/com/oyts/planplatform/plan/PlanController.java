package com.oyts.planplatform.plan;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/plans/{planId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    PlanDtos.TaskResponse createTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId, @Valid @RequestBody PlanDtos.TaskRequest request) {
        return service.createTask(jwt.getSubject(), planId, request);
    }

    @PutMapping("/tasks/{taskId}")
    PlanDtos.TaskResponse updateTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId, @Valid @RequestBody PlanDtos.TaskRequest request) {
        return service.updateTask(jwt.getSubject(), taskId, request);
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTask(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) { service.deleteTask(jwt.getSubject(), taskId); }

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

    @GetMapping("/plans/{planId}/stats")
    PlanDtos.StatsResponse stats(@AuthenticationPrincipal Jwt jwt, @PathVariable String planId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.stats(jwt.getSubject(), planId, from, to);
    }
}
