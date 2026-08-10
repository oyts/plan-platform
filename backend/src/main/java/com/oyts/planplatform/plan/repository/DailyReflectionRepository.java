package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.DailyReflection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyReflectionRepository extends JpaRepository<DailyReflection, String> {
    Optional<DailyReflection> findByPlanIdAndDate(String planId, LocalDate date);
    List<DailyReflection> findByPlanIdAndDateBetweenOrderByDateDesc(String planId, LocalDate from, LocalDate to);
}
