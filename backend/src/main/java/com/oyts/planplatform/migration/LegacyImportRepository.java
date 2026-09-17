package com.oyts.planplatform.migration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface LegacyImportRepository extends JpaRepository<LegacyImport, String> {
    Optional<LegacyImport> findByOwnerIdAndChecksum(String ownerId, String checksum);
    Optional<LegacyImport> findByIdAndOwnerId(String id, String ownerId);
    List<LegacyImport> findByOwnerIdOrderByImportedAtDesc(String ownerId);
    boolean existsByOwnerIdAndStatus(String ownerId, String status);
}
