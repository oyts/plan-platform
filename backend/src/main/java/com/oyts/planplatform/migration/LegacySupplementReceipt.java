package com.oyts.planplatform.migration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity @Table(name="legacy_recycling_supplements")
class LegacySupplementReceipt {
    @Id @Column(length=36) private String id;
    @Column(name="owner_id",nullable=false,length=36) private String ownerId;
    @Column(name="import_id",nullable=false,length=36) private String importId;
    @Column(name="operation_key",nullable=false,length=36) private String operationKey;
    @Column(nullable=false,length=64) private String fingerprint;
    @Column(name="result_json",nullable=false,columnDefinition="text") private String resultJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected LegacySupplementReceipt() {}
    LegacySupplementReceipt(String owner,String batch,String key,String fingerprint,String result) {
        id=UUID.randomUUID().toString();ownerId=owner;importId=batch;operationKey=key;
        this.fingerprint=fingerprint;resultJson=result;createdAt=Instant.now();
    }
    String getImportId(){return importId;}
    String getOperationKey(){return operationKey;}
    String getFingerprint(){return fingerprint;}
    String getResultJson(){return resultJson;}
}
interface LegacySupplementReceiptRepository extends JpaRepository<LegacySupplementReceipt,String> {
    Optional<LegacySupplementReceipt> findByOwnerIdAndOperationKey(String owner,String key);
    Optional<LegacySupplementReceipt> findByImportId(String importId);
}
