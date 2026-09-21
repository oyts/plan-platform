package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.Plan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {
    List<Plan> findByOwnerIdAndDeletedAtIsNullAndArchivedFalseOrderByStartDateDesc(String ownerId);
    List<Plan> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    Optional<Plan> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<Plan> findByIdAndOwnerId(String id, String ownerId);
}
