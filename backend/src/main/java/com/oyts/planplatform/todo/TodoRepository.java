package com.oyts.planplatform.todo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TodoRepository extends JpaRepository<Todo, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Todo> {
    Optional<Todo> findByIdAndOwnerId(String id, String ownerId);
    List<Todo> findTop2001ByOwnerIdAndDeletedAndDueDateBetweenOrderByDueDateAscCreatedAtAscIdAsc(
        String ownerId, boolean deleted, LocalDate from, LocalDate to);
}
