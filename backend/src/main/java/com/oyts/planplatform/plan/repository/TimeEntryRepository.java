package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.TimeEntry;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, String> {
    List<TimeEntry> findByProgressIdOrderByStartedAtAsc(String progressId);
    @EntityGraph(attributePaths = {"progress", "progress.task", "progress.task.tag"})
    Page<TimeEntry> findByProgressTaskPlanOwnerIdAndStartedAtBetweenOrderByStartedAtDesc(String ownerId, Instant from, Instant to, Pageable pageable);
    @EntityGraph(attributePaths = {"progress", "progress.task", "progress.task.tag"})
    List<TimeEntry> findByProgressTaskPlanOwnerIdAndStartedAtBetweenOrderByStartedAtDesc(String ownerId, Instant from, Instant to);
}
