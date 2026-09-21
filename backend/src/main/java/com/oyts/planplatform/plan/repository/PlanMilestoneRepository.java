package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanMilestone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanMilestoneRepository extends JpaRepository<PlanMilestone, String> {
    List<PlanMilestone> findByPlanIdAndDeletedAtIsNullOrderByDateAscCreatedAtAsc(String planId);
    Optional<PlanMilestone> findByIdAndPlanOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    Optional<PlanMilestone> findByIdAndPlanOwnerId(String id, String ownerId);
    List<PlanMilestone> findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
}
