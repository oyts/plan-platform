package com.oyts.planplatform.reminders;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.oyts.planplatform.reminders.ReminderDtos.*;

@RestController @RequestMapping("/api/reminders")
public class ReminderController {
    private final ReminderService service;
    private final ReminderPollLimiter limiter;
    public ReminderController(ReminderService service,ReminderPollLimiter limiter){this.service=service;this.limiter=limiter;}
    private <T> ResponseEntity<T> reply(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/preferences") ResponseEntity<Preferences> preferences(@AuthenticationPrincipal Jwt jwt){return reply(service.preferences(jwt.getSubject()));}
    @PutMapping("/preferences") ResponseEntity<Preferences> save(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Preferences value){return reply(service.save(jwt.getSubject(),value));}
    @GetMapping("/devices") ResponseEntity<List<Device>> devices(@AuthenticationPrincipal Jwt jwt){return reply(service.devices(jwt.getSubject()));}
    @PostMapping("/devices") ResponseEntity<Registration> register(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Register value){return reply(service.register(jwt.getSubject(),value));}
    @DeleteMapping("/devices/{id}") ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){service.revoke(jwt.getSubject(),id);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    @PostMapping("/poll") ResponseEntity<Poll> poll(@RequestHeader(value="X-Reminder-Key",required=false) String key,jakarta.servlet.http.HttpServletRequest request){limiter.acquire(request.getRemoteAddr(),key);return reply(service.poll(key));}
}
