package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlanTaskRepository extends JpaRepository<PlanTask, String> {
    @EntityGraph(attributePaths = {"tag"})
    List<PlanTask> findByPlanIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(String planId);
    @EntityGraph(attributePaths = {"tag", "progress"})
    List<PlanTask> findByPlanIdAndRecurrenceIdAndDeletedAtIsNullOrderByDateAscSortOrderAsc(String planId, String recurrenceId);
    @EntityGraph(attributePaths = {"tag"})
    Page<PlanTask> findByPlanIdAndDeletedAtIsNullAndDateBetweenOrderByDateAscSortOrderAsc(String planId, LocalDate from, LocalDate to, Pageable pageable);
    @EntityGraph(attributePaths = {"tag"})
    List<PlanTask> findByPlanOwnerIdAndDeletedAtIsNullAndDateOrderByStartTimeAsc(String ownerId, LocalDate date);
    @EntityGraph(attributePaths = {"plan", "tag"})
    Optional<PlanTask> findByIdAndPlanOwnerIdAndDeletedAtIsNull(String id, String ownerId);
    @EntityGraph(attributePaths = {"plan", "tag"})
    List<PlanTask> findByPlanOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String ownerId);
    Optional<PlanTask> findByIdAndPlanOwnerId(String id, String ownerId);
    long countByTagIdAndDeletedAtIsNull(String tagId);
}
