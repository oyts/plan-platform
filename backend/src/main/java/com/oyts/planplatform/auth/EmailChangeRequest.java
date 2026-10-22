package com.oyts.planplatform.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity @Table(name="email_change_requests")
class EmailChangeRequest {
    @Id @Column(length=36) private String id;
    @Column(name="owner_id",nullable=false,length=36) private String ownerId;
    @Column(name="old_email",nullable=false,length=190) private String oldEmail;
    @Column(name="new_email",nullable=false,length=190) private String newEmail;
    @Column(name="password_fingerprint",nullable=false,length=64) private String passwordFingerprint;
    @Column(name="auth_generation",nullable=false) private long authGeneration;
    @Column(name="old_token_hash",nullable=false,length=64) private String oldTokenHash;
    @Column(name="new_token_hash",nullable=false,length=64) private String newTokenHash;
    @Column(name="old_verified_at") private Instant oldVerifiedAt;
    @Column(name="new_verified_at") private Instant newVerifiedAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="consumed_at") private Instant consumedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
    protected EmailChangeRequest() {}
    EmailChangeRequest(UserAccount user,String email,String passwordFingerprint,String oldHash,String newHash,Instant expires) {
        id=UUID.randomUUID().toString();ownerId=user.getId();oldEmail=user.getEmail();newEmail=email;
        this.passwordFingerprint=passwordFingerprint;authGeneration=user.getAuthGeneration();oldTokenHash=oldHash;newTokenHash=newHash;
        expiresAt=expires;createdAt=Instant.now();
    }
    boolean active(){return consumedAt==null&&cancelledAt==null&&expiresAt.isAfter(Instant.now());}
    boolean matches(UserAccount user,String fingerprint){return user.isEnabled()&&oldEmail.equals(user.getEmail())&&authGeneration==user.getAuthGeneration()&&passwordFingerprint.equals(fingerprint);}
    void verify(boolean old){if(old)oldVerifiedAt=Instant.now();else newVerifiedAt=Instant.now();}
    void consume(){consumedAt=Instant.now();}
    void cancel(){if(consumedAt==null&&cancelledAt==null)cancelledAt=Instant.now();}
    String getId(){return id;} String getOwnerId(){return ownerId;} String getNewEmail(){return newEmail;}
    String getOldTokenHash(){return oldTokenHash;} String getNewTokenHash(){return newTokenHash;}
    boolean verified(boolean old){return old?oldVerifiedAt!=null:newVerifiedAt!=null;}
    Instant getExpiresAt(){return expiresAt;} Instant getCreatedAt(){return createdAt;}
    String state(){return consumedAt!=null?"CHANGED":cancelledAt!=null?"CANCELLED":!expiresAt.isAfter(Instant.now())?"EXPIRED":"PENDING";}
}
interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest,String> {
    Optional<EmailChangeRequest> findByIdAndOwnerId(String id,String owner);
    Optional<EmailChangeRequest> findFirstByOwnerIdOrderByCreatedAtDesc(String owner);
    List<EmailChangeRequest> findByOwnerIdAndConsumedAtIsNullAndCancelledAtIsNull(String owner);
    long countByOwnerIdAndCreatedAtAfter(String owner,Instant since);
}
