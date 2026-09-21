package com.oyts.planplatform.sync;

import com.oyts.planplatform.common.ConflictException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

public class IdempotencyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Idempotency-Key";
    private final IdempotencyService service;
    public IdempotencyFilter(IdempotencyService service) { this.service = service; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER); Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (key == null || key.isBlank() || authentication == null || !authentication.isAuthenticated() || !isMutation(request.getMethod())) { chain.doFilter(request, response); return; }
        if (!key.matches("[A-Za-z0-9._:-]{8,100}")) { response.sendError(400, "X-Idempotency-Key 格式无效。" ); return; }
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        String userId = authentication.getName(); String method = request.getMethod(); String path = request.getRequestURI();
        String fingerprint = fingerprint(cachedRequest);
        IdempotencyService.IdempotencyDecision decision;
        try {
            try { decision = service.reserve(userId, key, method, path, fingerprint); }
            catch (DataIntegrityViolationException duplicate) { decision = service.lookup(userId, key, method, path, fingerprint); }
        }
        catch (ConflictException conflict) { response.setStatus(409); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"status\":409,\"message\":\"" + json(conflict.getMessage()) + "\"}"); return; }
        if (decision.replay()) { response.setStatus(decision.status()); if (decision.contentType() != null) response.setContentType(decision.contentType()); response.setHeader("X-Idempotent-Replay", "true"); if (decision.body() != null) response.getOutputStream().write(decision.body().getBytes(StandardCharsets.UTF_8)); return; }
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            try { chain.doFilter(cachedRequest, wrapped); }
            catch (IOException | ServletException | RuntimeException failure) {
                service.release(decision.recordId());
                throw failure;
            }
            int status = wrapped.getStatus();
            if (status >= 200 && status < 300) service.complete(decision.recordId(), status, wrapped.getContentType(), new String(wrapped.getContentAsByteArray(), StandardCharsets.UTF_8));
            else service.release(decision.recordId());
        } finally { wrapped.copyBodyToResponse(); }
    }
    private boolean isMutation(String method) { return method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE"); }
    private String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    private String fingerprint(CachedBodyRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0); digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0); digest.update(String.valueOf(request.getQueryString()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0); digest.update(request.body());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        CachedBodyRequest(HttpServletRequest request) throws IOException { super(request); body = request.getInputStream().readAllBytes(); }
        byte[] body() { return body; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
                @Override public int read() { return input.read(); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
