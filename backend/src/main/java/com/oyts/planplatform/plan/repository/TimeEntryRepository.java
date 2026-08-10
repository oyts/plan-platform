package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.TimeEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, String> {
    List<TimeEntry> findByProgressIdOrderByStartedAtAsc(String progressId);
}
