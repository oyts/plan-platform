package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTagRepository extends JpaRepository<PlanTag, String> {
    List<PlanTag> findByPlanIdOrderByOverallDescCreatedAtAsc(String planId);
    Optional<PlanTag> findByIdAndPlanOwnerId(String id, String ownerId);
    long countByPlanIdAndOverallTrue(String planId);
}
