package com.oyts.planplatform.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    Optional<PasswordResetToken> findByTokenHashAndUserEmailIgnoreCase(String tokenHash, String email);
    List<PasswordResetToken> findByUserIdAndUsedAtIsNullAndExpiresAtAfter(String userId, Instant now);
}
