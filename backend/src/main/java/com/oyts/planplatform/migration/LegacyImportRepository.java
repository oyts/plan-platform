package com.oyts.planplatform.migration;
import org.springframework.data.jpa.repository.JpaRepository;
interface LegacyImportRepository extends JpaRepository<LegacyImport, String> {}
