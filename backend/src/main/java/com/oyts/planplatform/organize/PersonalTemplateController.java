package com.oyts.planplatform.organize;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/structure-templates")
class PersonalTemplateController {
    private final PersonalTemplateService service;
    PersonalTemplateController(PersonalTemplateService service){this.service=service;}
    private ResponseEntity<?> reply(Object value){return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(value);}
    @GetMapping Object list(@AuthenticationPrincipal Jwt u,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="")String search,@RequestParam(defaultValue="ALL")String kind,@RequestParam(defaultValue="")String category,@RequestParam(defaultValue="false")boolean favorite){return reply(service.list(u.getSubject(),page,search,kind,category,favorite));}
    @GetMapping("/{id}") Object read(@AuthenticationPrincipal Jwt u,@PathVariable UUID id){return reply(service.read(u.getSubject(),id.toString()));}
    @PostMapping("/capture/preview") Object preview(@AuthenticationPrincipal Jwt u,@Valid @RequestBody PersonalTemplateService.Capture r){return reply(service.capturePreview(u.getSubject(),r));}
    @PostMapping("/capture") Object save(@AuthenticationPrincipal Jwt u,@Valid @RequestBody PersonalTemplateService.Save r){return reply(service.save(u.getSubject(),r));}
    @PutMapping("/{id}") Object edit(@AuthenticationPrincipal Jwt u,@PathVariable UUID id,@Valid @RequestBody PersonalTemplateService.Edit r){return reply(service.edit(u.getSubject(),id.toString(),r));}
    @DeleteMapping("/{id}") Object delete(@AuthenticationPrincipal Jwt u,@PathVariable UUID id,@RequestParam long version){service.delete(u.getSubject(),id.toString(),version);return ResponseEntity.noContent().build();}
    @PostMapping("/{id}/preview") Object use(@AuthenticationPrincipal Jwt u,@PathVariable UUID id,@Valid @RequestBody PersonalTemplateService.Use r){return reply(service.usePreview(u.getSubject(),id.toString(),r));}
    @PostMapping("/{id}/apply") Object apply(@AuthenticationPrincipal Jwt u,@PathVariable UUID id,@Valid @RequestBody PersonalTemplateService.Apply r){return reply(service.apply(u.getSubject(),id.toString(),r));}
}
