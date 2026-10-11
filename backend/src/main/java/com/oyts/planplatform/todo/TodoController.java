package com.oyts.planplatform.todo;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
class TodoController {
    private final TodoService service;
    TodoController(TodoService service) { this.service = service; }
    @GetMapping
    List<TodoDtos.Response> list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean deleted) {
        return service.list(jwt.getSubject(), from, to, deleted);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    TodoDtos.Response create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TodoDtos.Create request) {
        return service.create(jwt.getSubject(), request);
    }
    // Deletion is a versioned tombstone, so offline retries never silently destroy newer changes.
    @PutMapping("/{id}")
    TodoDtos.Response update(@AuthenticationPrincipal Jwt jwt, @PathVariable String id, @Valid @RequestBody TodoDtos.Update request) {
        return service.update(jwt.getSubject(), id, request);
    }
}
