package com.oyts.planplatform.sync;

import com.oyts.planplatform.auth.UserAccount;
import com.oyts.planplatform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id", nullable = false) private UserAccount owner;
    @Column(name = "operation_key", nullable = false, length = 100) private String operationKey;
    @Column(name = "request_method", nullable = false, length = 10) private String requestMethod;
    @Column(name = "request_path", nullable = false, length = 500) private String requestPath;
    @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;
    @Column(name = "response_status") private Integer responseStatus;
    @Column(name = "response_content_type", length = 200) private String responseContentType;
    @Column(name = "response_body", columnDefinition = "text") private String responseBody;
    @Column(nullable = false) private boolean completed;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    protected IdempotencyRecord() {}
    public IdempotencyRecord(UserAccount owner, String operationKey, String requestMethod, String requestPath, String requestFingerprint, Instant expiresAt) { this.owner = owner; this.operationKey = operationKey; this.requestMethod = requestMethod; this.requestPath = requestPath; this.requestFingerprint = requestFingerprint; this.expiresAt = expiresAt; }
    public void complete(int status, String contentType, String body) { responseStatus = status; responseContentType = contentType; responseBody = body; completed = true; }
    public String getOperationKey() { return operationKey; } public String getRequestMethod() { return requestMethod; } public String getRequestPath() { return requestPath; } public String getRequestFingerprint() { return requestFingerprint; }
    public Integer getResponseStatus() { return responseStatus; } public String getResponseContentType() { return responseContentType; } public String getResponseBody() { return responseBody; }
    public boolean isCompleted() { return completed; } public Instant getExpiresAt() { return expiresAt; }
}
