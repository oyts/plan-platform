package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanMilestone;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanMilestoneRepository extends JpaRepository<PlanMilestone, String> {
    List<PlanMilestone> findByPlanIdOrderByDateAscCreatedAtAsc(String planId);
    Optional<PlanMilestone> findByIdAndPlanOwnerId(String id, String ownerId);
}
