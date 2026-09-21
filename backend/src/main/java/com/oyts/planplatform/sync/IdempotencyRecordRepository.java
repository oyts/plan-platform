package com.oyts.planplatform.sync;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
    Optional<IdempotencyRecord> findByOwnerIdAndOperationKey(String ownerId, String operationKey);
    long deleteByExpiresAtBefore(java.time.Instant cutoff);
}
