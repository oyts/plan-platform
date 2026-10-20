package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.PlanTask;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlanTaskRepository extends JpaRepository<PlanTask, String> {
    interface ReminderCandidate {
        String getId();
        String getTitle();
        String getPlanId();
        LocalDate getEffectiveDate();
        java.time.LocalTime getStartTime();
    }
    // Keep LocalTime extraction on the same Hibernate mapping as task writes.
    // Raw JDBC TIME.toString() can shift it when JDBC and JVM time zones differ.
    @Query("""
        select t.id as id,t.title as title,p.id as planId,
               coalesce(g.deferTo,t.date) as effectiveDate,t.startTime as startTime
        from PlanTask t join t.plan p left join t.progress g
        where p.owner.id=:owner and p.deletedAt is null and p.archived=false
          and t.deletedAt is null and t.startTime is not null
          and coalesce(g.status,'PENDING') in ('PENDING','DEFERRED')
          and coalesce(g.everStarted,false)=false
          and not exists(select 1 from TimeEntry e where e.progress=g)
          and coalesce(g.deferTo,t.date) between :from and :to
        order by coalesce(g.deferTo,t.date),t.startTime,t.id
        """)
    List<ReminderCandidate> findReminderCandidates(@Param("owner") String owner,
        @Param("from") LocalDate from,@Param("to") LocalDate to,Pageable pageable);
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
