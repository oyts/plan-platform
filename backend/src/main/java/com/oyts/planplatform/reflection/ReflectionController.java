package com.oyts.planplatform.reflection;
import com.oyts.planplatform.ai.AiConversationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.oyts.planplatform.reflection.ReflectionDtos.*;

@RestController @RequestMapping("/api/reflection")
public class ReflectionController {
 private final ReflectionService service;private final AiConversationService ai;
 public ReflectionController(ReflectionService service,AiConversationService ai){this.service=service;this.ai=ai;}
 private <T> ResponseEntity<T> reply(T v){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(v);}
 @GetMapping("/facts") ResponseEntity<?> facts(@AuthenticationPrincipal Jwt u,@RequestParam Kind kind,@RequestParam LocalDate start){return reply(service.facts(u.getSubject(),kind,start));}
 @GetMapping("/items") ResponseEntity<?> items(@AuthenticationPrincipal Jwt u,@RequestParam Kind kind,@RequestParam LocalDate start,@RequestParam String mode,@RequestParam(defaultValue="ARRANGED") String bucket,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return reply(service.items(u.getSubject(),kind,start,mode,bucket,page,size));}
 @GetMapping("/days/{date}") ResponseEntity<?> notes(@AuthenticationPrincipal Jwt u,@PathVariable LocalDate date){return reply(service.notes(u.getSubject(),date));}
 @PutMapping("/days/{date}") ResponseEntity<?> save(@AuthenticationPrincipal Jwt u,@PathVariable LocalDate date,@Valid @RequestBody Save r){return reply(service.save(u.getSubject(),date,r));}
 @GetMapping("/preferences") ResponseEntity<?> preferences(@AuthenticationPrincipal Jwt u){return reply(service.preferences(u.getSubject()));}
 @PutMapping("/preferences") ResponseEntity<?> preferences(@AuthenticationPrincipal Jwt u,@Valid @RequestBody Preferences r){return reply(service.savePreferences(u.getSubject(),r));}
 @PostMapping("/guides/claim") ResponseEntity<?> guide(@AuthenticationPrincipal Jwt u){return reply(service.claimGuide(u.getSubject()));}
 @GetMapping("/trend") ResponseEntity<?> trend(@AuthenticationPrincipal Jwt u,@RequestParam LocalDate start){return reply(service.trend(u.getSubject(),start));}
 @PostMapping("/trend/{start}/decision") ResponseEntity<?> decide(@AuthenticationPrincipal Jwt u,@PathVariable LocalDate start,@Valid @RequestBody TrendDecision r){return reply(service.decide(u.getSubject(),start,r.decision()));}
 @PostMapping("/analysis-preview") ResponseEntity<?> preview(@AuthenticationPrincipal Jwt u,@Valid @RequestBody PreviewRequest r){return reply(service.preview(u.getSubject(),r.kind(),r.start(),r.includeNotes(),r.relatedMode(),r.relatedId()));}
 @GetMapping("/targets") ResponseEntity<?> targets(@AuthenticationPrincipal Jwt u,@RequestParam String mode,@RequestParam(defaultValue="0") int page){return reply(service.targets(u.getSubject(),mode,page));}
 @PostMapping("/analyses") ResponseEntity<?> analyse(@AuthenticationPrincipal Jwt u,@Valid @RequestBody Analyse r){return reply(ai.createReview(u.getSubject(),r));}
 @GetMapping("/analyses") ResponseEntity<?> analyses(@AuthenticationPrincipal Jwt u){return reply(ai.reviewList(u.getSubject()));}
}
