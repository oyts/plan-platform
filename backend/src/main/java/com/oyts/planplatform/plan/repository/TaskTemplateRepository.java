package com.oyts.planplatform.plan.repository;

import com.oyts.planplatform.plan.domain.TaskTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskTemplateRepository extends JpaRepository<TaskTemplate, String> {
    List<TaskTemplate> findByOwnerIdOrderByCreatedAtDesc(String ownerId);
    Optional<TaskTemplate> findByIdAndOwnerId(String id, String ownerId);
    boolean existsByOwnerIdAndNameIgnoreCase(String ownerId, String name);
}
