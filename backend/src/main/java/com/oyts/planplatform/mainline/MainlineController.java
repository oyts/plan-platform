package com.oyts.planplatform.mainline;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/mainlines")
class MainlineController {
    private final MainlineService service; MainlineController(MainlineService service) { this.service = service; }
    @GetMapping List<MainlineDtos.MainlineResponse> list(@AuthenticationPrincipal Jwt jwt) { return service.list(jwt.getSubject()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) MainlineDtos.MainlineResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MainlineDtos.MainlineRequest r) { return service.create(jwt.getSubject(), r); }
    @PutMapping("/{id}") MainlineDtos.MainlineResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody MainlineDtos.MainlineRequest r) { return service.update(jwt.getSubject(), id, r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void archive(@AuthenticationPrincipal Jwt jwt, @PathVariable String id) { service.archive(jwt.getSubject(), id); }
    @PostMapping("/{id}/items") @ResponseStatus(HttpStatus.CREATED) MainlineDtos.ItemResponse add(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody MainlineDtos.ItemRequest r) { return service.addItem(jwt.getSubject(), id, r); }
    @PutMapping("/items/{itemId}") MainlineDtos.ItemResponse updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable String itemId, @Valid @RequestBody MainlineDtos.ItemRequest r) { return service.updateItem(jwt.getSubject(), itemId, r); }
    @DeleteMapping("/items/{itemId}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable String itemId) { service.deleteItem(jwt.getSubject(), itemId); }
    @PostMapping("/items/{itemId}/start") MainlineDtos.ItemResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable String itemId, @RequestParam(required = false) Long version) { return service.start(jwt.getSubject(), itemId, version); }
    @PostMapping("/items/{itemId}/pause") MainlineDtos.ItemResponse pause(@AuthenticationPrincipal Jwt jwt, @PathVariable String itemId, @RequestParam(required = false) Long version) { return service.pause(jwt.getSubject(), itemId, version); }
    @PostMapping("/items/{itemId}/complete") MainlineDtos.ItemResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable String itemId, @Valid @RequestBody MainlineDtos.CompleteRequest r) { return service.complete(jwt.getSubject(), itemId, r); }
    @PostMapping("/{id}/stages") @ResponseStatus(HttpStatus.CREATED) MainlineDtos.StageResponse addStage(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody MainlineDtos.StageRequest r) { return service.addStage(jwt.getSubject(), id, r); }
    @PutMapping("/stages/{id}") MainlineDtos.StageResponse updateStage(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody MainlineDtos.StageRequest r) { return service.updateStage(jwt.getSubject(), id, r); }
    @DeleteMapping("/stages/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteStage(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @RequestParam(required = false) Long version) { service.deleteStage(jwt.getSubject(), id, version); }
}
