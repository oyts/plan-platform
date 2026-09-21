package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.TaskProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskProgressRepository extends JpaRepository<TaskProgress, String> {
    Optional<TaskProgress> findByTaskId(String taskId);
    @EntityGraph(attributePaths = {"task", "task.plan", "task.tag"})
    List<TaskProgress> findByTaskPlanId(String planId);
    @EntityGraph(attributePaths = {"task", "task.plan", "task.tag"})
    List<TaskProgress> findByTaskPlanOwnerId(String ownerId);
}
