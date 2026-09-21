package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTagRepository extends JpaRepository<PlanTag, String> {
    List<PlanTag> findByPlanIdAndDeletedAtIsNullOrderByOverallDescCreatedAtAsc(String planId);
    Optional<PlanTag> findByIdAndPlanOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<PlanTag> findByIdAndPlanOwnerId(String id, String ownerId);
    List<PlanTag> findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    long countByPlanIdAndDeletedAtIsNullAndOverallTrue(String planId);
}
