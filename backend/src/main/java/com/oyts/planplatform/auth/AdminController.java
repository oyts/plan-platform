package com.oyts.planplatform.auth;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/invitations")
@PreAuthorize("hasRole('ADMIN')")
class AdminController {
    private final AdminService service; AdminController(AdminService service) { this.service = service; }
    @GetMapping org.springframework.http.ResponseEntity<List<AuthDtos.InvitationResponse>> list() { return org.springframework.http.ResponseEntity.ok().header("Cache-Control","private, no-store").body(service.invitations()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) org.springframework.http.ResponseEntity<AuthDtos.InvitationResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthDtos.InvitationRequest request) { return org.springframework.http.ResponseEntity.status(201).header("Cache-Control","private, no-store").body(service.create(jwt.getSubject(), request)); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void revoke(@PathVariable String id) { service.revoke(id); }
}
