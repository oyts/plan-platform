package com.oyts.planplatform.daily;

import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api")
public class DailyController {
    private final DailyService service;
    DailyController(DailyService service) { this.service = service; }
    private <T> ResponseEntity<T> privateReply(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
    @GetMapping("/daily/summary")
    ResponseEntity<DailyDtos.Summary> summary(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, @RequestParam(defaultValue = "3") int days) {
        return privateReply(service.summary(jwt.getSubject(), date, days));
    }
    @GetMapping("/daily/items")
    ResponseEntity<PageResponse<DailyDtos.Item>> items(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(defaultValue = "3") int days, @RequestParam(defaultValue = "TODAY") String bucket,
        @RequestParam(defaultValue = "ALL") String mode, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return privateReply(service.items(jwt.getSubject(), date, days, bucket, mode, page, size));
    }
    @GetMapping("/daily/routes")
    ResponseEntity<PageResponse<DailyDtos.Route>> routes(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return privateReply(service.routes(jwt.getSubject(), page, size));
    }
    @GetMapping("/daily/reminders")
    ResponseEntity<DailyDtos.Reminders> reminders(@AuthenticationPrincipal Jwt jwt) { return privateReply(service.reminders(jwt.getSubject())); }
    @PutMapping("/daily/reminders")
    ResponseEntity<DailyDtos.Reminders> saveReminders(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DailyDtos.ReminderRequest r) { return privateReply(service.saveReminders(jwt.getSubject(), r)); }
    @GetMapping("/weekly/summary")
    ResponseEntity<DailyDtos.Week> week(@AuthenticationPrincipal Jwt jwt, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) { return privateReply(service.week(jwt.getSubject(), start)); }
    @GetMapping("/weekly/reviews/{start}")
    ResponseEntity<DailyDtos.Review> review(@AuthenticationPrincipal Jwt jwt, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) { return privateReply(service.review(jwt.getSubject(), start)); }
    @PutMapping("/weekly/reviews/{start}")
    ResponseEntity<DailyDtos.Review> saveReview(@AuthenticationPrincipal Jwt jwt, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @Valid @RequestBody DailyDtos.ReviewRequest r) { return privateReply(service.saveReview(jwt.getSubject(), start, r)); }
    @GetMapping("/weekly/reviews")
    ResponseEntity<PageResponse<DailyDtos.Review>> history(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) { return privateReply(service.history(jwt.getSubject(), page, size)); }
}
