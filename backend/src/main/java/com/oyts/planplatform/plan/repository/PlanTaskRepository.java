package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanTaskRepository extends JpaRepository<PlanTask, String> {
    @EntityGraph(attributePaths = {"tag"})
    List<PlanTask> findByPlanIdOrderByDateAscSortOrderAsc(String planId);
    @EntityGraph(attributePaths = {"tag"})
    List<PlanTask> findByPlanOwnerIdAndDateOrderByStartTimeAsc(String ownerId, LocalDate date);
    @EntityGraph(attributePaths = {"plan", "tag"})
    Optional<PlanTask> findByIdAndPlanOwnerId(String id, String ownerId);
    long countByTagId(String tagId);
}
