package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.Plan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {
    List<Plan> findByOwnerIdAndArchivedFalseOrderByStartDateDesc(String ownerId);
    Optional<Plan> findByIdAndOwnerId(String id, String ownerId);
}
