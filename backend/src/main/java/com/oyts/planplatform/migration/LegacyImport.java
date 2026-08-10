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
    @Column(nullable = false, columnDefinition = "text") private String payload;
    @Column(name = "imported_at", nullable = false) private Instant importedAt;
    protected LegacyImport() {}
    LegacyImport(UserAccount owner, String sourceVersion, String payload) { this.owner = owner; this.sourceVersion = sourceVersion; this.payload = payload; }
    @PrePersist void prePersist() { id = UUID.randomUUID().toString(); importedAt = Instant.now(); }
    String getId() { return id; }
}
