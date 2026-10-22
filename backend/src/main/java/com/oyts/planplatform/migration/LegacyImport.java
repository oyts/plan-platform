package com.oyts.planplatform.migration;
import com.oyts.planplatform.auth.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name = "legacy_imports")
class LegacyImport {
    @Id @Column(length = 36) private String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id") private UserAccount owner;
    @Column(name = "source_version", nullable = false, length = 30) private String sourceVersion;
    @Column(nullable = false, length = 64) private String checksum;
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(nullable = false, length = 20) private String status = "SNAPSHOT";
    @Column(name = "report_json", columnDefinition = "text") private String reportJson;
    @Column(name = "imported_at", nullable = false) private Instant importedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "rolled_back_at") private Instant rolledBackAt;
    protected LegacyImport() {}
    LegacyImport(UserAccount owner, String sourceVersion, String checksum, String payload) { this.owner = owner; this.sourceVersion = sourceVersion; this.checksum = checksum; this.payload = payload; }
    @PrePersist void prePersist() { id = UUID.randomUUID().toString(); importedAt = Instant.now(); }
    void complete(String reportJson) { this.status = "COMPLETED"; this.reportJson = reportJson; this.completedAt = Instant.now(); }
    void rollback(String reportJson) { this.status = "ROLLED_BACK"; this.reportJson = reportJson; this.rolledBackAt = Instant.now(); }
    void restart() { this.status = "SNAPSHOT"; this.reportJson = null; this.completedAt = null; this.rolledBackAt = null; }
    String getId() { return id; }
    String getSourceVersion() { return sourceVersion; }
    String getChecksum() { return checksum; }
    String getStatus() { return status; }
    String getReportJson() { return reportJson; }
    String getPayload() { return payload; }
    Instant getImportedAt() { return importedAt; }
    Instant getCompletedAt() { return completedAt; }
    Instant getRolledBackAt() { return rolledBackAt; }
}
