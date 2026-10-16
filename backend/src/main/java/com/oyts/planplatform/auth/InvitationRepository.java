package com.oyts.planplatform.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findByTokenHashAndEmailIgnoreCase(String tokenHash, String email);
    Optional<Invitation> findById(String id);
    List<Invitation> findByOrderByCreatedAtDesc();
    List<Invitation> findByEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter(String email, Instant now);
}
