package com.oyts.planplatform.feedback;

import com.oyts.planplatform.plan.PlanDtos.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/feedback")
class FeedbackController {
    private final FeedbackService service;FeedbackController(FeedbackService service){this.service=service;}
    private <T> ResponseEntity<T> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping ResponseEntity<PageResponse<FeedbackDtos.Summary>> list(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reply(service.list(jwt.getSubject(),status,page,size));}
    @PostMapping ResponseEntity<FeedbackDtos.Detail> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody FeedbackDtos.Create r){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.create(jwt.getSubject(),r));}
    @GetMapping("/{id}") ResponseEntity<FeedbackDtos.Detail> read(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){return reply(service.read(jwt.getSubject(),id));}
    @PostMapping("/{id}/replies") ResponseEntity<FeedbackDtos.Detail> add(@AuthenticationPrincipal Jwt jwt,@PathVariable String id,@Valid @RequestBody FeedbackDtos.Reply r){return reply(service.reply(jwt.getSubject(),id,r));}
}

@RestController @RequestMapping("/api/admin/feedback") @PreAuthorize("hasRole('ADMIN')")
class AdminFeedbackController {
    private final FeedbackService service;AdminFeedbackController(FeedbackService service){this.service=service;}
    private <T> ResponseEntity<T> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping ResponseEntity<PageResponse<FeedbackDtos.Summary>> list(@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reply(service.list(null,status,page,size));}
    @GetMapping("/{id}") ResponseEntity<FeedbackDtos.Detail> read(@PathVariable String id){return reply(service.read(null,id));}
    @PutMapping("/{id}") ResponseEntity<FeedbackDtos.Detail> handle(@PathVariable String id,@Valid @RequestBody FeedbackDtos.Handle r){return reply(service.handle(id,r));}
}
