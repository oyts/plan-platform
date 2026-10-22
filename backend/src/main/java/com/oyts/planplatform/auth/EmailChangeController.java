package com.oyts.planplatform.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth/email-change")
class EmailChangeController {
    private final EmailChangeService service;private final RefreshCookieService cookies;
    EmailChangeController(EmailChangeService service,RefreshCookieService cookies){this.service=service;this.cookies=cookies;}
    @GetMapping("/status") EmailChangeService.Status status(@AuthenticationPrincipal Jwt jwt,HttpServletRequest request){return service.status(jwt.getSubject(),request.getRemoteAddr());}
    @PostMapping("/request") EmailChangeService.Status request(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody EmailChangeService.Start input,HttpServletRequest request){return service.start(jwt.getSubject(),generation(jwt),input,request.getRemoteAddr());}
    @PostMapping("/verify") @ResponseStatus(HttpStatus.NO_CONTENT) void verify(@Valid @RequestBody EmailChangeService.Verify input,HttpServletRequest request){service.verify(input,request.getRemoteAddr());}
    @PostMapping("/{id}/cancel") @ResponseStatus(HttpStatus.NO_CONTENT) void cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){service.cancel(jwt.getSubject(),generation(jwt),id.toString());}
    @PostMapping("/complete") @ResponseStatus(HttpStatus.NO_CONTENT) void finish(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody EmailChangeService.Finish input,HttpServletRequest request,HttpServletResponse response){service.finish(jwt.getSubject(),generation(jwt),input,request.getRemoteAddr());cookies.clear(response);}
    private long generation(Jwt jwt){Object value=jwt.getClaim("accountGeneration");return value instanceof Number number?number.longValue():0;}
}
